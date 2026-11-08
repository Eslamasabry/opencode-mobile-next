//! OC2 stable driver for the proven version range. Mutations are never
//! retried, even after uncertainty.
use crate::opencode::{
    known_directories, model_selection, validate_directory, validate_prompt, validate_session_id,
    validate_team_ids, GlobalStatusObservation, ProtocolError,
};
use reqwest::{Client, Method, StatusCode, Url};
use serde_json::{json, Value};
use std::{
    collections::{BTreeMap, BTreeSet},
    net::IpAddr,
    sync::Mutex,
    time::{Duration, Instant},
};
/// Stable OC2 lines whose wire contract this driver was run against end to
/// end (`tests/oc2_live.rs`): 2.0.29, 2.1.1 and 2.2.5. A minor line is only
/// added after that live run; the OpenAPI check in [`validate_openapi`] still
/// gates every server inside the range. 2.0 releases before 2.0.29 are out:
/// their `/api/info` and permission contract were never verified.
const SUPPORTED: &[(u64, u64)] = &[(0, 29), (1, 0), (2, 0)];

/// `2.<minor>.<patch>` with plain decimal parts, inside [`SUPPORTED`].
/// Pre-release and build suffixes (betas, local builds) are refused.
pub(crate) fn supported_version(version: &str) -> bool {
    let mut parts = version.split('.');
    let mut number = || {
        parts
            .next()
            .filter(|p| !p.is_empty() && p.len() <= 6 && p.bytes().all(|b| b.is_ascii_digit()))
            .and_then(|p| p.parse::<u64>().ok())
    };
    let (Some(2), Some(minor), Some(patch)) = (number(), number(), number()) else {
        return false;
    };
    parts.next().is_none()
        && SUPPORTED
            .iter()
            .any(|&(line, first)| line == minor && patch >= first)
}
const MAX_RESPONSE: usize = 8 * 1024 * 1024;
const FRESHNESS: Duration = Duration::from_secs(30);

pub struct OpenCode2Client {
    http: Client,
    stream: Client,
    base: Url,
    username: String,
    password: String,
    identity: tokio::sync::Mutex<Option<ServerIdentity>>,
    accepted: Mutex<BTreeMap<String, String>>,
    /// Sessions this client made on the server, with their directory: the
    /// proof that a restarted server is still the same session store.
    sessions: Mutex<BTreeMap<String, String>>,
}

/// The server this client proved once: its process and where it keeps its
/// files. A new process is the same server only when [OpenCode2Client::ready]
/// proves it again (see there).
struct ServerIdentity {
    pid: u64,
    paths: Value,
}

/// How many of this client's own sessions a restarted server is asked for.
const RESTART_SESSION_PROBES: usize = 3;
pub(crate) fn validate_info(info: &Value) -> Result<(), ProtocolError> {
    if !info["version"].as_str().is_some_and(supported_version)
        || !info["pid"].is_u64()
        || !info["urls"].is_array()
        || !info.pointer("/paths/tmp").is_some_and(Value::is_string)
    {
        return Err(ProtocolError("unsupported_server_version"));
    }
    Ok(())
}
impl OpenCode2Client {
    pub fn new(url: &str, username: &str, password: &str) -> Result<Self, ProtocolError> {
        let base = Url::parse(url).map_err(|_| ProtocolError("invalid_server"))?;
        let loopback = base
            .host_str()
            .and_then(|s| s.trim_matches(['[', ']']).parse::<IpAddr>().ok())
            .is_some_and(|ip| ip.is_loopback());
        if base.scheme() != "http"
            || !loopback
            || !base.username().is_empty()
            || base.password().is_some()
            || base.query().is_some()
            || base.fragment().is_some()
            || base.path() != "/"
            || username != "opencode"
            || password.is_empty()
        {
            return Err(ProtocolError("invalid_server"));
        }
        let client = |timeout| {
            let mut b = Client::builder()
                .no_proxy()
                .redirect(reqwest::redirect::Policy::none())
                .connect_timeout(Duration::from_secs(10));
            if timeout {
                b = b.timeout(Duration::from_secs(10));
            }
            b.build()
                .map_err(|_| ProtocolError("transport_unavailable"))
        };
        Ok(Self {
            http: client(true)?,
            stream: client(false)?,
            base,
            username: username.into(),
            password: password.into(),
            identity: tokio::sync::Mutex::new(None),
            accepted: Mutex::new(BTreeMap::new()),
            sessions: Mutex::new(BTreeMap::new()),
        })
    }
    async fn request(
        &self,
        method: Method,
        path: &str,
        directory: Option<&str>,
        query: &[(&str, &str)],
        body: Option<&Value>,
    ) -> Result<Value, ProtocolError> {
        let mut url = self.base.clone();
        url.set_path(path);
        if let Some(dir) = directory {
            validate_directory(dir)?;
            url.query_pairs_mut()
                .append_pair("location[directory]", dir);
        }
        for (key, value) in query {
            url.query_pairs_mut().append_pair(key, value);
        }
        let mutation = method != Method::GET;
        let uncertain = if mutation {
            "transport_uncertain"
        } else {
            "transport_unavailable"
        };
        let mut request = self
            .http
            .request(method, url)
            .basic_auth(&self.username, Some(&self.password));
        if let Some(body) = body {
            request = request.json(body);
        }
        let mut response = request.send().await.map_err(|_| ProtocolError(uncertain))?;
        if !response.status().is_success() {
            return Err(ProtocolError(match response.status() {
                StatusCode::UNAUTHORIZED | StatusCode::FORBIDDEN => "authentication_failed",
                StatusCode::NOT_FOUND => "protocol_unavailable",
                status if mutation && status.is_server_error() => "transport_uncertain",
                _ => "server_rejected",
            }));
        }
        if response.status() == StatusCode::NO_CONTENT {
            return Ok(Value::Null);
        }
        let mut bytes = Vec::new();
        while let Some(chunk) = response
            .chunk()
            .await
            .map_err(|_| ProtocolError(uncertain))?
        {
            if bytes.len().saturating_add(chunk.len()) > MAX_RESPONSE {
                return Err(ProtocolError(if mutation {
                    "transport_uncertain"
                } else {
                    "response_too_large"
                }));
            }
            bytes.extend_from_slice(&chunk);
        }
        serde_json::from_slice(&bytes).map_err(|_| {
            ProtocolError(if mutation {
                "transport_uncertain"
            } else {
                "invalid_response"
            })
        })
    }
    /// Returns the server's version once its identity and schema are proven.
    ///
    /// The first answer pins the server: its pid and its `paths`, after the
    /// OpenAPI check. A later answer from another pid is a restart (the app
    /// restarting OpenCode from Settings, or relaunching it after a crash)
    /// only if it proves to be the same server again: it took this client's
    /// password (OpenCode 2 always checks one, and only the app has it), it
    /// passes the version and OpenAPI checks again, it keeps its files in
    /// the same `paths`, and, once this client made sessions, it still holds
    /// at least one of them in their own folder. Then the new pid is pinned.
    /// Anything else is another server: `server_identity_changed`, and the
    /// old pin stays, so the real server coming back is accepted again.
    async fn ready(&self) -> Result<String, ProtocolError> {
        let info = self
            .request(Method::GET, "/api/info", None, &[], None)
            .await?;
        validate_info(&info)?;
        let pid = info["pid"]
            .as_u64()
            .ok_or(ProtocolError("invalid_response"))?;
        let version = info["version"].as_str().unwrap_or_default().to_owned();
        let mut identity = self.identity.lock().await;
        match identity.as_ref() {
            Some(known) if known.pid == pid => return Ok(version),
            Some(known) => {
                if known.paths != info["paths"] {
                    return Err(ProtocolError("server_identity_changed"));
                }
                self.validate_schema().await?;
                if !self.holds_own_sessions().await? {
                    return Err(ProtocolError("server_identity_changed"));
                }
            }
            None => self.validate_schema().await?,
        }
        *identity = Some(ServerIdentity {
            pid,
            paths: info["paths"].clone(),
        });
        Ok(version)
    }
    /// The pid of the server this client last proved, if any. A turn sent to
    /// one pid and found dead under another was cut off by a restart.
    pub async fn server_pid(&self) -> Option<u64> {
        self.identity.lock().await.as_ref().map(|known| known.pid)
    }
    async fn validate_schema(&self) -> Result<(), ProtocolError> {
        validate_openapi(
            &self
                .request(Method::GET, "/openapi.json", None, &[], None)
                .await?,
        )
    }
    /// Whether the server still has one of this client's newest sessions
    /// (true when it made none). A missing session is a different store; a
    /// failed read is not a proof either way and fails the check.
    async fn holds_own_sessions(&self) -> Result<bool, ProtocolError> {
        let probes = self
            .sessions
            .lock()
            .map_err(|_| ProtocolError("server_identity_changed"))?
            .iter()
            // Session IDs sort newest first.
            .take(RESTART_SESSION_PROBES)
            .map(|(id, dir)| (id.clone(), dir.clone()))
            .collect::<Vec<_>>();
        if probes.is_empty() {
            return Ok(true);
        }
        for (id, directory) in probes {
            match self.session(&directory, &id).await {
                Ok(_) => return Ok(true),
                Err(ProtocolError("protocol_unavailable" | "session_scope_mismatch")) => {}
                Err(error) => return Err(error),
            }
        }
        Ok(false)
    }
    pub async fn verify(&self) -> Result<Value, ProtocolError> {
        let version = self.ready().await?;
        Ok(
            json!({"healthy":true,"version":version,"pinnedVersion":true,"openapiVerified":true,
            "capabilities":{"sessionDriver":true,"readOnlyPermissions":true,"executionDriver":true,
            "knownPersonDirectoryStatus":true,"globalStatusObserver":true,"globalAdmissionAuthority":false,
            "chatAdmission":false,"execution":false,"oc2":true},"blockers":["global_status_unavailable"]}),
        )
    }
    pub async fn resolve_model(
        &self,
        directory: &str,
        requested: &str,
    ) -> Result<String, ProtocolError> {
        if !requested.is_empty() {
            model_selection(requested)?;
            return Ok(requested.into());
        }
        self.ready().await?;
        let response = self
            .request(
                Method::GET,
                "/api/model/default",
                Some(directory),
                &[],
                None,
            )
            .await?;
        default_model(&response, directory)
    }

    async fn session(&self, directory: &str, id: &str) -> Result<Value, ProtocolError> {
        validate_session_id(id)?;
        let result = self
            .request(
                Method::GET,
                &format!("/api/session/{id}"),
                Some(directory),
                &[],
                None,
            )
            .await?;
        let session = result
            .get("data")
            .ok_or(ProtocolError("invalid_response"))?;
        if session["id"] != id
            || session
                .pointer("/location/directory")
                .and_then(Value::as_str)
                != Some(directory)
        {
            return Err(ProtocolError("session_scope_mismatch"));
        }
        Ok(session.clone())
    }
    pub async fn create_session(
        &self,
        directory: &str,
        title: &str,
    ) -> Result<String, ProtocolError> {
        self.ready().await?;
        let rules = deny_all();
        let result = self.request(Method::POST,"/api/session",Some(directory),&[],Some(&json!({
            "title":title,"agent":"build","location":{"directory":directory},"permissions":rules
        }))).await?;
        let id = result
            .pointer("/data/id")
            .and_then(Value::as_str)
            .ok_or(ProtocolError("transport_uncertain"))?;
        validate_session_id(id).map_err(|_| ProtocolError("transport_uncertain"))?;
        let session = self.session(directory, id).await?;
        confirm_policy(&session, &rules)?;
        self.sessions
            .lock()
            .map_err(|_| ProtocolError("transport_uncertain"))?
            .insert(id.into(), directory.into());
        Ok(id.into())
    }
    pub async fn prompt(
        &self,
        directory: &str,
        id: &str,
        role: &str,
        model: &str,
        instructions: &str,
        text: &str,
        readonly: bool,
    ) -> Result<Value, ProtocolError> {
        validate_session_id(id)?;
        validate_prompt(role, model, instructions, text, readonly)?;
        let instruction = json!(format!("AI Team role: {role}.\n{instructions}"));
        if serde_json::to_vec(&instruction)
            .map_err(|_| ProtocolError("invalid_role"))?
            .len()
            > 8192
        {
            return Err(ProtocolError("instructions_too_large"));
        }
        let (provider, model_id) =
            model_selection(model)?.ok_or(ProtocolError("modelNotConfigured"))?;
        self.ready().await?;
        self.session(directory, id).await?;
        if active(&self.active().await?)?.contains(id) {
            return Err(ProtocolError("session_busy"));
        }
        let path = format!("/api/session/{id}");
        let rules = role_permissions(readonly);
        self.request(
            Method::PATCH,
            &path,
            Some(directory),
            &[],
            Some(&json!({"permissions":rules})),
        )
        .await?;
        confirm_policy(&self.session(directory, id).await?, &rules)?;
        self.request(
            Method::POST,
            &format!("{path}/agent"),
            Some(directory),
            &[],
            Some(&json!({"agent":"build"})),
        )
        .await?;
        let selection = json!({"providerID":provider,"id":model_id});
        self.request(
            Method::POST,
            &format!("{path}/model"),
            Some(directory),
            &[],
            Some(&json!({"model":selection})),
        )
        .await?;
        self.request(
            Method::PUT,
            &format!("/api/experimental/session/{id}/instructions/entries/aiteam-role"),
            Some(directory),
            &[],
            Some(&json!({"value":instruction})),
        )
        .await?;
        let entries = self
            .request(
                Method::GET,
                &format!("/api/experimental/session/{id}/instructions/entries"),
                Some(directory),
                &[],
                None,
            )
            .await?;
        let matches = entries["data"]
            .as_array()
            .ok_or(ProtocolError("instructions_unconfirmed"))?
            .iter()
            .filter(|e| e["key"] == "aiteam-role")
            .collect::<Vec<_>>();
        if matches.len() != 1 || matches[0]["value"] != instruction {
            return Err(ProtocolError("instructions_unconfirmed"));
        }
        let session = self.session(directory, id).await?;
        confirm_policy(&session, &rules)?;
        if session["agent"] != "build"
            || session.pointer("/model/providerID").and_then(Value::as_str) != Some(provider)
            || session.pointer("/model/id").and_then(Value::as_str) != Some(model_id)
        {
            return Err(ProtocolError("model_unconfirmed"));
        }
        let accepted = self
            .request(
                Method::POST,
                &format!("{path}/prompt"),
                Some(directory),
                &[],
                Some(&json!({"text":text})),
            )
            .await?;
        if accepted.pointer("/data/sessionID").and_then(Value::as_str) != Some(id)
            || accepted.pointer("/data/type").and_then(Value::as_str) != Some("user")
            || !accepted
                .pointer("/data/id")
                .and_then(Value::as_str)
                .is_some_and(|s| s.starts_with("msg_"))
        {
            return Err(ProtocolError("transport_uncertain"));
        }
        self.accepted
            .lock()
            .map_err(|_| ProtocolError("transport_uncertain"))?
            .insert(id.into(), accepted["data"]["id"].as_str().unwrap().into());
        Ok(json!({"state":"running","sessionId":id,"accepted":true}))
    }
    async fn active(&self) -> Result<Value, ProtocolError> {
        self.request(Method::GET, "/api/session/active", None, &[], None)
            .await
    }
    pub async fn person_directory_status(
        &self,
        dirs: &[String],
        ids: &[String],
    ) -> Result<bool, ProtocolError> {
        known_directories(dirs)?;
        self.server_idle_except(ids).await
    }
    /// Whether nothing but the team's sessions [ids] runs anywhere on the
    /// server. OC2's active list is process-wide, so this needs no folder:
    /// it reconciles busy evidence before the person opened any chat
    /// project. It is evidence only, never admission authority by itself.
    pub async fn server_idle_except(&self, ids: &[String]) -> Result<bool, ProtocolError> {
        validate_team_ids(ids)?;
        self.ready().await?;
        // Conservatively block on ANY non-team work.
        Ok(active(&self.active().await?)?
            .iter()
            .all(|id| ids.contains(id)))
    }
    async fn messages(&self, directory: &str, id: &str) -> Result<Vec<Value>, ProtocolError> {
        let mut messages = Vec::new();
        let mut cursor: Option<String> = None;
        let mut cursors = BTreeSet::new();
        let mut bytes = 0usize;
        for _ in 0..128 {
            let query = match cursor.as_deref() {
                Some(c) => vec![("cursor", c)],
                None => vec![("order", "asc"), ("limit", "100")],
            };
            let page = self
                .request(
                    Method::GET,
                    &format!("/api/session/{id}/message"),
                    Some(directory),
                    &query,
                    None,
                )
                .await?;
            let items = page["data"]
                .as_array()
                .ok_or(ProtocolError("invalid_messages"))?;
            bytes = bytes.saturating_add(
                serde_json::to_vec(items)
                    .map_err(|_| ProtocolError("invalid_messages"))?
                    .len(),
            );
            if bytes > MAX_RESPONSE {
                return Err(ProtocolError("response_too_large"));
            }
            let page_cursor = page["cursor"]
                .as_object()
                .ok_or(ProtocolError("invalid_messages"))?;
            for item in items {
                let mid = item["id"]
                    .as_str()
                    .ok_or(ProtocolError("invalid_messages"))?;
                if !mid.starts_with("msg_")
                    || messages.last().is_some_and(|old: &Value| {
                        old["id"].as_str().is_some_and(|prev| prev >= mid)
                    })
                {
                    return Err(ProtocolError("invalid_messages"));
                }
                messages.push(item.clone());
            }
            cursor = match page_cursor.get("next") {
                None | Some(Value::Null) => None,
                Some(Value::String(s)) if !s.is_empty() && s.len() <= 8192 => Some(s.clone()),
                _ => return Err(ProtocolError("invalid_messages")),
            };
            // OC2 emits edge cursors even on the last nonempty page. Follow to
            // an empty page instead of treating a full/short page as complete.
            if items.is_empty() || cursor.is_none() {
                return Ok(messages);
            }
            if !cursors.insert(cursor.clone().unwrap()) {
                return Err(ProtocolError("invalid_messages"));
            }
        }
        Err(ProtocolError("response_too_large"))
    }
    pub async fn observe(&self, directory: &str, id: &str) -> Result<Value, ProtocolError> {
        tokio::time::timeout(Duration::from_secs(10), self.observe_fresh(directory, id))
            .await
            .map_err(|_| ProtocolError("observation_stale"))?
    }
    async fn observe_fresh(&self, directory: &str, id: &str) -> Result<Value, ProtocolError> {
        validate_session_id(id)?;
        self.ready().await?;
        self.session(directory, id).await?;
        let before = self.active().await?;
        let messages = self.messages(directory, id).await?;
        let permissions = self
            .request(
                Method::GET,
                &format!("/api/session/{id}/permission"),
                Some(directory),
                &[],
                None,
            )
            .await?;
        let forms = self
            .request(Method::GET, "/api/form", Some(directory), &[], None)
            .await?;
        if forms.pointer("/location/directory").and_then(Value::as_str) != Some(directory) {
            return Err(ProtocolError("session_scope_mismatch"));
        }
        let session = self.session(directory, id).await?;
        let inbox = self
            .request(
                Method::GET,
                &format!("/api/session/{id}/inbox"),
                Some(directory),
                &[],
                None,
            )
            .await?;
        let queued = scoped_pending(&inbox, id)?;
        let after = self.active().await?;
        let mut result = observation(
            id,
            &session,
            &messages,
            &before,
            &after,
            &permissions,
            &forms,
        )?;
        let expected = self
            .accepted
            .lock()
            .map_err(|_| ProtocolError("invalid_messages"))?
            .get(id)
            .cloned();
        let latest_user = messages
            .iter()
            .rev()
            .find(|m| m["type"] == "user")
            .and_then(|m| m["id"].as_str());
        if (result["state"] == "completed" || result["reason"] == "modelNotConfigured")
            && (queued || expected.as_deref().is_some_and(|e| Some(e) != latest_user))
        {
            result["state"] = json!("unknown");
            result.as_object_mut().unwrap().remove("reason");
        }
        Ok(result)
    }
    pub async fn abort(&self, directory: &str, id: &str) -> Result<(), ProtocolError> {
        validate_session_id(id)?;
        self.ready().await?;
        self.session(directory, id).await?;
        let path = format!("/api/session/{id}");
        // Revoke tools first. Each mutation below is attempted exactly once.
        let rules = deny_all();
        self.request(
            Method::PATCH,
            &path,
            Some(directory),
            &[],
            Some(&json!({"permissions":rules})),
        )
        .await?;
        confirm_policy(&self.session(directory, id).await?, &rules)?;
        let inbox = self
            .request(
                Method::GET,
                &format!("{path}/inbox"),
                Some(directory),
                &[],
                None,
            )
            .await?;
        scoped_pending(&inbox, id)?;
        for item in inbox["data"]
            .as_array()
            .ok_or(ProtocolError("invalid_pending_requests"))?
        {
            let item_id = item["id"]
                .as_str()
                .ok_or(ProtocolError("invalid_pending_requests"))?;
            if !item_id.starts_with("msg_")
                || item_id.len() > 128
                || !item_id
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || b == b'_')
            {
                return Err(ProtocolError("invalid_pending_requests"));
            }
            self.request(
                Method::DELETE,
                &format!("{path}/inbox/{item_id}"),
                Some(directory),
                &[],
                None,
            )
            .await?;
        }
        let result = self
            .request(
                Method::POST,
                &format!("{path}/interrupt"),
                Some(directory),
                &[("resume", "false")],
                None,
            )
            .await?;
        if !result["interrupted"].is_boolean() {
            return Err(ProtocolError("transport_uncertain"));
        }
        // Only reads repeat; queued work or active execution never means stopped.
        tokio::time::timeout(Duration::from_secs(10), async {
            for _ in 0..20 {
                let inbox = self
                    .request(
                        Method::GET,
                        &format!("{path}/inbox"),
                        Some(directory),
                        &[],
                        None,
                    )
                    .await?;
                let queued = scoped_pending(&inbox, id)?;
                let running = active(&self.active().await?)?.contains(id);
                if !queued && !running {
                    return Ok(());
                }
                tokio::time::sleep(Duration::from_millis(100)).await;
            }
            Err(ProtocolError("stop_unconfirmed"))
        })
        .await
        .map_err(|_| ProtocolError("stop_unconfirmed"))?
    }
    pub async fn global_status_observer(&self) -> Result<GlobalStatusObserver, ProtocolError> {
        self.ready().await?;
        let mut url = self.base.clone();
        url.set_path("/api/event");
        let response = tokio::time::timeout(
            Duration::from_secs(10),
            self.stream
                .get(url)
                .header(reqwest::header::ACCEPT, "text/event-stream")
                .basic_auth(&self.username, Some(&self.password))
                .send(),
        )
        .await
        .map_err(|_| ProtocolError("global_status_disconnected"))?
        .map_err(|_| ProtocolError("global_status_disconnected"))?;
        if !response.status().is_success() {
            return Err(ProtocolError("global_status_disconnected"));
        }
        if response
            .headers()
            .get(reqwest::header::CONTENT_TYPE)
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.split(';').next())
            != Some("text/event-stream")
        {
            return Err(ProtocolError("invalid_global_status_stream"));
        }
        Ok(GlobalStatusObserver {
            response: Some(response),
            buffer: Vec::new(),
            data: String::new(),
            frame_bytes: 0,
            busy: BTreeSet::new(),
            last_event: Instant::now(),
        })
    }
}
fn default_model(response: &Value, directory: &str) -> Result<String, ProtocolError> {
    if response
        .pointer("/location/directory")
        .and_then(Value::as_str)
        != Some(directory)
    {
        return Err(ProtocolError("session_scope_mismatch"));
    }
    let model = &response["data"];
    if model["enabled"] != true {
        return Err(ProtocolError("modelNotConfigured"));
    }
    let provider = model["providerID"]
        .as_str()
        .ok_or(ProtocolError("modelNotConfigured"))?;
    let id = model["id"]
        .as_str()
        .ok_or(ProtocolError("modelNotConfigured"))?;
    let name = format!("{provider}/{id}");
    model_selection(&name).map_err(|_| ProtocolError("modelNotConfigured"))?;
    Ok(name)
}
fn deny_all() -> Value {
    json!([{"action":"*","resource":"*","effect":"deny"}])
}
fn role_permissions(readonly: bool) -> Value {
    let mut rules = deny_all().as_array().unwrap().clone();
    for action in if readonly {
        &["read", "glob", "grep"][..]
    } else {
        &["read", "glob", "grep", "edit", "shell"][..]
    } {
        rules.push(json!({"action":action,"resource":"*","effect":"allow"}));
    }
    Value::Array(rules)
}
fn confirm_policy(session: &Value, expected: &Value) -> Result<(), ProtocolError> {
    if session.get("permissions") != Some(expected) {
        return Err(ProtocolError("permission_policy_unconfirmed"));
    }
    Ok(())
}
fn active(response: &Value) -> Result<BTreeSet<String>, ProtocolError> {
    let map = response["data"]
        .as_object()
        .ok_or(ProtocolError("invalid_status"))?;
    let mut ids = BTreeSet::new();
    for (id, state) in map {
        validate_session_id(id).map_err(|_| ProtocolError("invalid_status"))?;
        if state != &json!({"type":"running"}) {
            return Err(ProtocolError("invalid_status"));
        }
        ids.insert(id.clone());
    }
    Ok(ids)
}
fn pending(response: &Value, id: &str) -> Result<bool, ProtocolError> {
    let items = response["data"]
        .as_array()
        .ok_or(ProtocolError("invalid_pending_requests"))?;
    let mut found = false;
    for item in items {
        let session = item["sessionID"]
            .as_str()
            .ok_or(ProtocolError("invalid_pending_requests"))?;
        validate_session_id(session).map_err(|_| ProtocolError("invalid_pending_requests"))?;
        found |= session == id;
    }
    Ok(found)
}
fn scoped_pending(response: &Value, id: &str) -> Result<bool, ProtocolError> {
    let items = response["data"]
        .as_array()
        .ok_or(ProtocolError("invalid_pending_requests"))?;
    for item in items {
        if item["sessionID"].as_str() != Some(id) {
            return Err(ProtocolError("session_scope_mismatch"));
        }
    }
    pending(response, id)
}
fn observation(
    id: &str,
    session: &Value,
    messages: &[Value],
    before: &Value,
    after: &Value,
    permissions: &Value,
    forms: &Value,
) -> Result<Value, ProtocolError> {
    let running = active(before)?.contains(id) | active(after)?.contains(id);
    let blocked = scoped_pending(permissions, id)? | pending(forms, id)?;
    let mut state = if running { "running" } else { "unknown" };
    let user = messages.iter().rposition(|m| m["type"] == "user");
    let assistant = messages.iter().rposition(|m| m["type"] == "assistant");
    let idle = messages.iter().rposition(|m| m["type"] == "idle");
    let mut text = String::new();
    let mut failure_reason = None;
    if let Some(index) = assistant {
        let message = &messages[index];
        let content = message["content"]
            .as_array()
            .ok_or(ProtocolError("invalid_messages"))?;
        for part in content {
            if part["type"] == "text" {
                let value = part["text"]
                    .as_str()
                    .ok_or(ProtocolError("invalid_messages"))?;
                if !text.is_empty() {
                    text.push('\n');
                }
                text.push_str(value);
            }
        }
        if user.is_some_and(|u| u < index) {
            if message.get("error").is_some_and(|e| !e.is_null())
                || matches!(
                    message["finish"].as_str(),
                    Some("error" | "length" | "content-filter")
                )
            {
                // OC2's structured provider authentication failure includes
                // unavailable free-tier access as well as invalid credentials.
                // Export only this fixed recovery code, never provider text,
                // response bodies, headers, or arbitrary error type strings.
                if message.pointer("/error/type").and_then(Value::as_str) == Some("provider.auth") {
                    // An assistant error can precede a provider retry. Only
                    // settled failure authorizes choosing a model and asking
                    // again; active/uncertain turns must not be duplicated.
                    if !running
                        && session["outcome"] == "failed"
                        && idle.is_some_and(|i| {
                            i > index
                                && messages[i]["outcome"] == "failed"
                                && messages[i]
                                    .pointer("/time/created")
                                    .and_then(Value::as_f64)
                                    .is_some_and(|time| time > 0.0)
                                && messages[i].pointer("/time/created")
                                    == session.pointer("/time/idle")
                        })
                    {
                        state = "failed";
                        failure_reason = Some("modelNotConfigured");
                    }
                } else {
                    state = "failed";
                }
            } else if !running
                && message["finish"] == "stop"
                && !text.is_empty()
                && !content.iter().any(|p| p["type"] == "tool")
                && message
                    .pointer("/time/completed")
                    .and_then(Value::as_f64)
                    .is_some_and(|t| t > 0.0)
                && idle.is_some_and(|i| {
                    i > index
                        && messages[i]["outcome"] == "succeeded"
                        && messages[i]
                            .pointer("/time/created")
                            .and_then(Value::as_f64)
                            .is_some_and(|time| time > 0.0)
                        && messages[i].pointer("/time/created") == session.pointer("/time/idle")
                })
                && session["outcome"] == "succeeded"
            {
                state = "completed";
            }
        }
    }
    if blocked {
        state = "blocked";
    }
    // Usage fields are optional; never synthesize zero for missing accounting.
    let mut result = json!({"state":state,"text":text,"usage":usage(messages)});
    if state == "failed" {
        if let Some(reason) = failure_reason {
            result["reason"] = json!(reason);
        }
    }
    Ok(result)
}
fn usage(messages: &[Value]) -> Value {
    let assistants: Vec<&Value> = messages
        .iter()
        .filter(|m| m["type"] == "assistant")
        .collect();
    let mut usage = serde_json::Map::new();
    if assistants.is_empty() {
        return Value::Object(usage);
    }
    let sum = |path: &str| -> Option<Value> {
        let mut total = 0.0;
        for info in &assistants {
            let n = info.pointer(path)?.as_f64()?;
            if !n.is_finite() || n < 0.0 {
                return None;
            }
            total += n;
        }
        serde_json::Number::from_f64(total).map(Value::Number)
    };
    if let Some(cost) = sum("/cost") {
        usage.insert("cost".into(), cost);
    }
    let mut tokens = serde_json::Map::new();
    for field in ["total", "input", "output", "reasoning"] {
        if let Some(value) = sum(&format!("/tokens/{field}")) {
            tokens.insert(field.into(), value);
        }
    }
    let mut cache = serde_json::Map::new();
    for field in ["read", "write"] {
        if let Some(value) = sum(&format!("/tokens/cache/{field}")) {
            cache.insert(field.into(), value);
        }
    }
    if !cache.is_empty() {
        tokens.insert("cache".into(), Value::Object(cache));
    }
    if !tokens.is_empty() {
        usage.insert("tokens".into(), Value::Object(tokens));
    }
    Value::Object(usage)
}

const OPERATIONS: &[(&str, &str, &str)] = &[
    ("/api/info", "get", "server.info"),
    ("/api/event", "get", "event.subscribe"),
    ("/api/session", "post", "session.create"),
    ("/api/session/active", "get", "session.active"),
    ("/api/session/{sessionID}", "get", "session.get"),
    ("/api/session/{sessionID}", "patch", "session.update"),
    (
        "/api/session/{sessionID}/agent",
        "post",
        "session.switchAgent",
    ),
    (
        "/api/session/{sessionID}/model",
        "post",
        "session.switchModel",
    ),
    ("/api/session/{sessionID}/prompt", "post", "session.prompt"),
    (
        "/api/session/{sessionID}/interrupt",
        "post",
        "session.interrupt",
    ),
    (
        "/api/session/{sessionID}/message",
        "get",
        "session.message.list",
    ),
    (
        "/api/session/{sessionID}/inbox",
        "get",
        "session.inbox.list",
    ),
    (
        "/api/session/{sessionID}/permission",
        "get",
        "session.permission.list",
    ),
    (
        "/api/session/{sessionID}/inbox/{inboxID}",
        "delete",
        "session.inbox.cancel",
    ),
    ("/api/form", "get", "form.list"),
    ("/api/model/default", "get", "model.default"),
    (
        "/api/experimental/session/{sessionID}/instructions/entries/{key}",
        "put",
        "experimental.session.instructions.entry.put",
    ),
    (
        "/api/experimental/session/{sessionID}/instructions/entries",
        "get",
        "experimental.session.instructions.entry.list",
    ),
];
fn validate_openapi(doc: &Value) -> Result<(), ProtocolError> {
    let bad = || ProtocolError("openapi_unconfirmed");
    for (path, method, operation) in OPERATIONS {
        if doc["paths"][path][method]["operationId"] != *operation {
            return Err(bad());
        }
    }
    let body = |path: &str, method: &str| {
        doc["paths"][path][method].pointer("/requestBody/content/application~1json/schema")
    };
    for (path, method) in [
        ("/api/session", "post"),
        ("/api/session/{sessionID}", "patch"),
    ] {
        let permission = body(path, method)
            .and_then(|b| b.pointer("/properties/permissions/anyOf"))
            .and_then(Value::as_array)
            .ok_or_else(bad)?;
        if !permission
            .iter()
            .any(|p| p["$ref"] == "#/components/schemas/Permission.Ruleset")
        {
            return Err(bad());
        }
    }
    if doc
        .pointer("/components/schemas/Permission.Ruleset/items/$ref")
        .and_then(Value::as_str)
        != Some("#/components/schemas/Permission.Rule")
    {
        return Err(bad());
    }
    let rule = &doc["components"]["schemas"]["Permission.Rule"];
    for field in ["action", "resource"] {
        if rule["properties"][field]["type"] != "string" {
            return Err(bad());
        }
    }
    if rule["properties"]["effect"]["$ref"] != "#/components/schemas/Permission.Effect" {
        return Err(bad());
    }
    let effects = doc["components"]["schemas"]["Permission.Effect"]["enum"]
        .as_array()
        .ok_or_else(bad)?;
    if effects.len() != 3
        || !["deny", "allow", "ask"]
            .iter()
            .all(|s| effects.contains(&json!(s)))
    {
        return Err(bad());
    }
    let prompt = body("/api/session/{sessionID}/prompt", "post").ok_or_else(bad)?;
    if prompt["required"] != json!(["text"]) || prompt["properties"]["text"]["type"] != "string" {
        return Err(bad());
    }
    for field in ["id", "providerID"] {
        if doc["components"]["schemas"]["Model.Ref"]["properties"]
            .get(field)
            .is_none()
        {
            return Err(bad());
        }
    }
    Ok(())
}

pub struct GlobalStatusObserver {
    response: Option<reqwest::Response>,
    buffer: Vec<u8>,
    data: String,
    frame_bytes: usize,
    busy: BTreeSet<(String, String)>,
    last_event: Instant,
}
impl GlobalStatusObserver {
    pub fn connected(&self) -> bool {
        self.response.is_some() && self.last_event.elapsed() <= FRESHNESS
    }
    pub fn observed_non_team_busy(&self, ids: &[String]) -> Result<bool, ProtocolError> {
        validate_team_ids(ids)?;
        if !self.connected() {
            return Err(ProtocolError("global_status_disconnected"));
        }
        Ok(self.busy.iter().any(|(_, id)| !ids.contains(id)))
    }
    fn disconnect(&mut self, code: &'static str) -> ProtocolError {
        self.response = None;
        self.buffer.clear();
        self.data.clear();
        ProtocolError(code)
    }
    fn frame(&mut self) -> Result<Option<GlobalStatusObservation>, ProtocolError> {
        while let Some(end) = self.buffer.iter().position(|b| *b == b'\n') {
            let bytes: Vec<u8> = self.buffer.drain(..=end).collect();
            self.frame_bytes = self.frame_bytes.saturating_add(bytes.len());
            if self.frame_bytes > 256 * 1024 {
                return Err(ProtocolError("global_status_frame_too_large"));
            }
            let line = std::str::from_utf8(&bytes[..bytes.len() - 1])
                .map_err(|_| ProtocolError("invalid_global_status_stream"))?
                .trim_end_matches('\r');
            if line.starts_with(':') {
                // Heartbeats prove transport liveness only, never idle authority.
                self.last_event = Instant::now();
            } else if line.is_empty() {
                self.frame_bytes = 0;
                if !self.data.is_empty() {
                    return parse_event(&std::mem::take(&mut self.data)).map(Some);
                }
            } else if let Some(value) = line.strip_prefix("data:") {
                if !self.data.is_empty() {
                    self.data.push('\n');
                }
                self.data.push_str(value.strip_prefix(' ').unwrap_or(value));
            } else if line == "event: effect/http-api/stream/failure" {
                return Err(ProtocolError("global_status_disconnected"));
            }
        }
        Ok(None)
    }
    pub async fn next(&mut self) -> Result<GlobalStatusObservation, ProtocolError> {
        if !self.connected() {
            return Err(self.disconnect("global_status_disconnected"));
        }
        loop {
            match self.frame() {
                Ok(Some(event)) => {
                    if let GlobalStatusObservation::Status {
                        directory,
                        session_id,
                        busy,
                    } = &event
                    {
                        let key = (directory.clone(), session_id.clone());
                        if *busy {
                            if self.busy.len() >= 4096 && !self.busy.contains(&key) {
                                return Err(self.disconnect("global_status_capacity_unknown"));
                            }
                            self.busy.insert(key);
                        } else {
                            self.busy.remove(&key);
                        }
                    }
                    self.last_event = Instant::now();
                    return Ok(event);
                }
                Ok(None) => {}
                Err(e) => return Err(self.disconnect(e.code())),
            }
            let Some(response) = self.response.as_mut() else {
                return Err(self.disconnect("global_status_disconnected"));
            };
            match tokio::time::timeout(FRESHNESS, response.chunk()).await {
                Ok(Ok(Some(chunk))) => {
                    if self.buffer.len().saturating_add(chunk.len()) > 256 * 1024 {
                        return Err(self.disconnect("global_status_frame_too_large"));
                    }
                    self.buffer.extend_from_slice(&chunk);
                }
                _ => return Err(self.disconnect("global_status_disconnected")),
            }
        }
    }
}
fn parse_event(data: &str) -> Result<GlobalStatusObservation, ProtocolError> {
    let data: Value =
        serde_json::from_str(data).map_err(|_| ProtocolError("invalid_global_status_stream"))?;
    let kind = data["type"]
        .as_str()
        .ok_or(ProtocolError("invalid_global_status_stream"))?;
    if matches!(
        kind,
        "location.shutdown" | "server.disposed" | "server.shutdown" | "global.disposed"
    ) {
        return Err(ProtocolError("global_status_disconnected"));
    }
    if !matches!(kind, "session.status" | "session.idle") {
        return Ok(GlobalStatusObservation::Other);
    }
    let directory = data
        .pointer("/location/directory")
        .and_then(Value::as_str)
        .ok_or(ProtocolError("invalid_global_status_stream"))?;
    let id = data
        .pointer("/data/sessionID")
        .and_then(Value::as_str)
        .ok_or(ProtocolError("invalid_global_status_stream"))?;
    validate_directory(directory)?;
    validate_session_id(id)?;
    let busy = if kind == "session.idle" {
        false
    } else {
        match data.pointer("/data/status/type").and_then(Value::as_str) {
            Some("busy" | "retry") => true,
            Some("idle") => false,
            _ => return Err(ProtocolError("invalid_global_status_stream")),
        }
    };
    Ok(GlobalStatusObservation::Status {
        directory: directory.into(),
        session_id: id.into(),
        busy,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    fn spec() -> Value {
        serde_json::from_str(include_str!(
            "../tests/fixtures/oc2-2.0.29-driver-openapi.json"
        ))
        .unwrap()
    }
    fn info() -> Value {
        json!({"version":"2.0.29","pid":1,"urls":[],"paths":{"tmp":"/tmp"}})
    }
    #[test]
    fn usage_omits_missing_fields_and_preserves_known_zero() {
        let mut messages = vec![json!({"type":"assistant", "cost":0.2,
            "tokens":{"input":10,"output":20}})];
        assert_eq!(usage(&messages)["cost"], 0.2);
        assert!(usage(&messages)["tokens"].get("total").is_none());
        messages[0]["cost"] = json!(0);
        assert_eq!(usage(&messages)["cost"], 0.0);
        messages.push(json!({"type":"assistant"}));
        assert!(usage(&messages).get("cost").is_none());
        assert!(usage(&messages).get("tokens").is_none());
        assert_eq!(usage(&[json!({"type":"user", "cost":10})]), json!({}));
    }

    #[test]
    fn only_proven_version_and_complete_schema_are_accepted() {
        assert!(validate_info(&info()).is_ok());
        let mut unsupported = info();
        unsupported["version"] = json!("2.0.28");
        assert!(validate_info(&unsupported).is_err());
        let original = spec();
        assert!(validate_openapi(&original).is_ok());
        for (path, method, _) in OPERATIONS {
            let mut changed = original.clone();
            changed["paths"][path][method]["operationId"] = json!("changed");
            assert!(validate_openapi(&changed).is_err(), "{path}");
        }
        let mut changed = original;
        changed["components"]["schemas"]["Permission.Effect"]["enum"] = json!(["allow", "ask"]);
        assert!(validate_openapi(&changed).is_err());
    }
    #[test]
    fn installed_stable_lines_are_accepted_and_unproven_versions_refused() {
        // Setup installs 2.2.5 (this line) and 2.1.1 (the 2.1.0 release);
        // both ran the live driver proof in tests/oc2_live.rs.
        for version in [
            "2.0.29", "2.0.30", "2.1.0", "2.1.1", "2.2.0", "2.2.5", "2.2.17",
        ] {
            let mut accepted = info();
            accepted["version"] = json!(version);
            assert!(validate_info(&accepted).is_ok(), "{version}");
        }
        for version in [
            "2.0.28",
            "2.0.4",
            "2.3.0",
            "3.0.0",
            "1.9.9",
            "2.2",
            "2.2.5.1",
            "2.2.5-beta.1",
            "2.2.5+local",
            "0.0.0-beta-18600",
            "v2.2.5",
            "2..5",
            "2.02.5x",
            "",
            " 2.2.5",
        ] {
            let mut refused = info();
            refused["version"] = json!(version);
            assert!(validate_info(&refused).is_err(), "{version:?}");
        }
        let mut number = info();
        number["version"] = json!(2.2);
        assert!(validate_info(&number).is_err());
    }
    #[test]
    fn captured_2_2_5_schema_passes_the_driver_contract() {
        let doc: Value = serde_json::from_str(include_str!(
            "../tests/fixtures/oc2-2.2.5-driver-openapi.json"
        ))
        .unwrap();
        assert!(validate_openapi(&doc).is_ok());
        for (path, method, _) in OPERATIONS {
            let mut changed = doc.clone();
            changed["paths"][path][method]["operationId"] = json!("changed");
            assert!(validate_openapi(&changed).is_err(), "{path}");
        }
    }
    #[test]
    fn policy_is_exact_replacement_and_shell_is_oc2_action() {
        let readonly = role_permissions(true);
        let worker = role_permissions(false);
        assert_eq!(
            readonly[0],
            json!({"action":"*","resource":"*","effect":"deny"})
        );
        assert!(worker
            .as_array()
            .unwrap()
            .iter()
            .any(|r| r["action"] == "shell"));
        assert!(!worker
            .as_array()
            .unwrap()
            .iter()
            .any(|r| r["action"] == "bash"));
        assert!(confirm_policy(&json!({"permissions":readonly}), &readonly).is_ok());
        assert!(confirm_policy(&json!({"permissions":worker}), &readonly).is_err());
        let mut prefixed = worker.as_array().unwrap().clone();
        prefixed.extend(readonly.as_array().unwrap().clone());
        assert!(confirm_policy(&json!({"permissions":prefixed}), &readonly).is_err());
    }
    #[test]
    fn completion_needs_latest_turn_idle_and_no_pending_or_busy() {
        let session = json!({"outcome":"succeeded","time":{"idle":2}});
        let idle = json!({"data":{}});
        let empty = json!({"data":[]});
        let mut messages = vec![
            json!({"id":"msg_1","type":"user"}),
            json!({"id":"msg_2","type":"assistant","content":[{"type":"text","text":"done"}],"finish":"stop","time":{"completed":1}}),
            json!({"id":"msg_3","type":"idle","outcome":"succeeded","time":{"created":2}}),
        ];
        let run = |m: &[Value], b: &Value, p: &Value| {
            observation("ses_test", &session, m, b, &idle, p, &empty).unwrap()["state"].clone()
        };
        assert_eq!(run(&messages, &idle, &empty), "completed");
        assert_eq!(
            run(
                &messages,
                &json!({"data":{"ses_test":{"type":"running"}}}),
                &empty
            ),
            "running"
        );
        assert_eq!(
            run(
                &messages,
                &idle,
                &json!({"data":[{"sessionID":"ses_test"}]})
            ),
            "blocked"
        );
        assert_eq!(run(&messages[..2], &idle, &empty), "unknown");
        let newer_session = json!({"outcome":"succeeded","time":{"idle":3}});
        assert_eq!(
            observation(
                "ses_test",
                &newer_session,
                &messages,
                &idle,
                &idle,
                &empty,
                &empty
            )
            .unwrap()["state"],
            "unknown"
        );
        messages.push(json!({"id":"msg_4","type":"user"}));
        assert_eq!(run(&messages, &idle, &empty), "unknown");
        assert!(active(&json!({"data":{"ses_test":{"type":"idle"}}})).is_err());
        assert!(pending(&json!({"data":[{}]}), "ses_test").is_err());
        let other = json!({"data":[{"sessionID":"ses_other"}]});
        assert!(!pending(&other, "ses_test").unwrap());
        assert_eq!(
            scoped_pending(&other, "ses_test").unwrap_err().code(),
            "session_scope_mismatch"
        );
        assert!(
            observation("ses_test", &session, &messages, &idle, &idle, &other, &empty).is_err()
        );
    }
    #[test]
    fn provider_auth_failure_exposes_only_allowlisted_model_recovery() {
        let session = json!({"outcome":"failed","time":{"idle":3}});
        let idle = json!({"data":{}});
        let empty = json!({"data":[]});
        // Shape captured on OC2 2.0.29: prompt accepted, selected free model
        // rejected by its provider, then a failed idle message.
        let mut messages = vec![
            json!({"id":"msg_1","type":"user"}),
            json!({"id":"msg_2","type":"assistant","model":{"providerID":"opencode","id":"cat-jam-free"},"content":[],"finish":"error","error":{"type":"provider.auth","status":403,"message":"fixture-provider-secret","body":"fixture-response-secret","headers":{"authorization":"fixture-header-secret"}},"time":{"completed":2}}),
            json!({"id":"msg_3","type":"idle","outcome":"failed","time":{"created":3}}),
        ];
        let run = |messages: &[Value], permissions: &Value| {
            observation(
                "ses_test",
                &session,
                messages,
                &idle,
                &idle,
                permissions,
                &empty,
            )
            .unwrap()
        };
        let result = run(&messages, &empty);
        assert_eq!(result["state"], "failed");
        assert_eq!(result["reason"], "modelNotConfigured");
        assert!(!result.to_string().contains("secret"));
        let running = json!({"data":{"ses_test":{"type":"running"}}});
        for (before, after) in [(&running, &idle), (&idle, &running)] {
            let result = observation(
                "ses_test", &session, &messages, before, after, &empty, &empty,
            )
            .unwrap();
            assert_eq!(result["state"], "running");
            assert!(result.get("reason").is_none());
        }
        let unsettled = run(&messages[..2], &empty);
        assert_eq!(unsettled["state"], "unknown");
        assert!(unsettled.get("reason").is_none());
        for changed in [
            json!({"outcome":"failed","time":{"idle":4}}),
            json!({"outcome":"succeeded","time":{"idle":3}}),
        ] {
            let result = observation(
                "ses_test", &changed, &messages, &idle, &idle, &empty, &empty,
            )
            .unwrap();
            assert_eq!(result["state"], "unknown");
            assert!(result.get("reason").is_none());
        }
        let pending = json!({"data":[{"sessionID":"ses_test"}]});
        let blocked = run(&messages, &pending);
        assert_eq!(blocked["state"], "blocked");
        assert!(blocked.get("reason").is_none());

        for error in [
            json!({"type":"provider.unknown-fixture-secret","message":"fixture-secret"}),
            json!({"type":"permission.rejected","message":"fixture-secret"}),
            json!({"type":"provider.auth.extra","message":"fixture-secret"}),
            json!({"message":"provider.auth fixture-secret"}),
            Value::Null,
        ] {
            messages[1]["error"] = error;
            let result = run(&messages, &empty);
            assert_eq!(result["state"], "failed");
            assert!(result.get("reason").is_none());
            assert!(!result.to_string().contains("secret"));
        }
        messages[1]["error"] = json!({"type":"provider.auth"});
        messages.push(json!({"id":"msg_4","type":"user"}));
        let result = run(&messages, &empty);
        assert_eq!(result["state"], "unknown");
        assert!(result.get("reason").is_none());
    }

    #[test]
    fn events_cannot_supply_idle_without_scope_or_clear_disconnect() {
        assert!(parse_event(
            r#"{"type":"session.status","data":{"sessionID":"ses_test","status":{"type":"idle"}}}"#
        )
        .is_err());
        assert!(parse_event(r#"{"type":"location.shutdown"}"#).is_err());
        assert_eq!(parse_event(r#"{"type":"session.status","location":{"directory":"/repo"},"data":{"sessionID":"ses_test","status":{"type":"busy"}}}"#).unwrap(),GlobalStatusObservation::Status{directory:"/repo".into(),session_id:"ses_test".into(),busy:true});
    }
    #[test]
    fn default_selection_preserves_runtime_identity_and_rejects_unavailable() {
        let good = json!({"location":{"directory":"/repo"},"data":{"id":"chosen","providerID":"provider","enabled":true}});
        assert_eq!(default_model(&good, "/repo").unwrap(), "provider/chosen");
        assert!(default_model(&good, "/other").is_err());
        let mut disabled = good.clone();
        disabled["data"]["enabled"] = json!(false);
        assert_eq!(
            default_model(&disabled, "/repo").unwrap_err().code(),
            "modelNotConfigured"
        );
        disabled["data"] = Value::Null;
        assert_eq!(
            default_model(&disabled, "/repo").unwrap_err().code(),
            "modelNotConfigured"
        );
    }
    #[tokio::test]
    async fn explicit_selection_does_not_contact_server_or_substitute() {
        let client =
            OpenCode2Client::new("http://127.0.0.1:1", "opencode", "fixture-only").unwrap();
        assert_eq!(
            client
                .resolve_model("/repo", "chosen/specific")
                .await
                .unwrap(),
            "chosen/specific"
        );
    }
    #[tokio::test]
    async fn model_recovery_requires_accepted_turn_and_empty_inbox() {
        use axum::{extract::Request, Router};
        for (queued, expected_user, expected_state) in [
            (false, "msg_1", "failed"),
            (true, "msg_1", "unknown"),
            (false, "msg_other", "unknown"),
        ] {
            let router = Router::new().fallback(move |request: Request| async move {
                assert_eq!(request.method(), Method::GET);
                let response = match request.uri().path() {
                    "/api/info" => info(),
                    "/openapi.json" => spec(),
                    "/api/session/active" => json!({"data":{}}),
                    "/api/session/ses_test" => json!({"data":{"id":"ses_test","location":{"directory":"/repo"},"outcome":"failed","time":{"idle":3}}}),
                    "/api/session/ses_test/message" => json!({"data":[
                        {"id":"msg_1","type":"user"},
                        {"id":"msg_2","type":"assistant","content":[],"finish":"error","error":{"type":"provider.auth","status":403,"message":"fixture-secret"}},
                        {"id":"msg_3","type":"idle","outcome":"failed","time":{"created":3}}
                    ],"cursor":{}}),
                    "/api/session/ses_test/permission" => json!({"data":[]}),
                    "/api/session/ses_test/inbox" => if queued { json!({"data":[{"sessionID":"ses_test"}]}) } else { json!({"data":[]}) },
                    "/api/form" => json!({"location":{"directory":"/repo"},"data":[]}),
                    _ => panic!("unexpected observation route"),
                };
                axum::Json(response)
            });
            let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
            let addr = listener.local_addr().unwrap();
            let task = tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });
            let client =
                OpenCode2Client::new(&format!("http://{addr}"), "opencode", "fixture-only")
                    .unwrap();
            client
                .accepted
                .lock()
                .unwrap()
                .insert("ses_test".into(), expected_user.into());
            let observed = client.observe("/repo", "ses_test").await.unwrap();
            assert_eq!(observed["state"], expected_state);
            assert_eq!(observed.get("reason").is_some(), expected_state == "failed");
            assert!(!observed.to_string().contains("secret"));
            task.abort();
        }
    }

    #[tokio::test]
    async fn wire_prompt_readback_pagination_and_uncertainty() {
        use axum::{extract::Request, response::IntoResponse, Router};
        use std::sync::Arc;
        for uncertain in [false, true] {
            let calls = Arc::new(Mutex::new(Vec::<String>::new()));
            let recorded = calls.clone();
            let router=Router::new().fallback(move |request: Request| {
                let calls=recorded.clone();async move {
                    let path=request.uri().path().to_owned();let method=request.method().to_string();
                    let query=request.uri().query().unwrap_or("").to_owned();
                    if path!="/api/info" && path!="/openapi.json" && path!="/api/session/active" {
                        assert!(query.contains("location%5Bdirectory%5D=%2Frepo"));
                    }
                    assert_eq!(request.headers().get("authorization").unwrap(),"Basic b3BlbmNvZGU6Zml4dHVyZS1vbmx5");
                    calls.lock().unwrap().push(format!("{method} {path}"));
                    if path == "/api/session/ses_test/model" {
                        let bytes = axum::body::to_bytes(request.into_body(), 8192).await.unwrap();
                        let sent: Value = serde_json::from_slice(&bytes).unwrap();
                        assert_eq!(sent, json!({"model":{"providerID":"zai-coding-plan","id":"glm-5.3-flash"}}));
                    }
                    let body=match path.as_str() {
                        "/api/info"=>info(),"/openapi.json"=>spec(),"/api/session/active"=>json!({"data":{}}),
                        "/api/model/default"=>json!({"location":{"directory":"/repo"},"data":{"id":"model","providerID":"provider","enabled":true}}),
                        "/api/session/ses_test" if method=="GET"=>json!({"data":{"id":"ses_test","location":{"directory":"/repo"},"permissions":if calls.lock().unwrap().iter().filter(|s|s.as_str()=="PATCH /api/session/ses_test").count() >= 2 {deny_all()} else {role_permissions(true)},"model":{"id":"glm-5.3-flash","providerID":"zai-coding-plan"},"agent":"build","outcome":"succeeded","time":{"idle":2}}}),
                        "/api/experimental/session/ses_test/instructions/entries"=>json!({"data":[{"key":"aiteam-role","value":"AI Team role: planner.\ninstructions"}]}),
                        "/api/session/ses_test/prompt" if uncertain=>return StatusCode::INTERNAL_SERVER_ERROR.into_response(),
                        "/api/session/ses_test/prompt"=>json!({"data":{"id":"msg_1","sessionID":"ses_test","type":"user"}}),
                        "/api/session/ses_test/message" if query.contains("cursor=")=>json!({"data":[],"cursor":{}}),
                        "/api/session/ses_test/message"=>json!({"data":[{"id":"msg_1","type":"user"},{"id":"msg_2","type":"assistant","content":[{"type":"text","text":"done"}],"finish":"stop","time":{"completed":1}},{"id":"msg_3","type":"idle","outcome":"succeeded","time":{"created":2}}],"cursor":{"next":"opaque"}}),
                        "/api/session/ses_test/permission"|"/api/session/ses_test/inbox"=>json!({"data":[]}),
                        "/api/form"=>json!({"location":{"directory":"/repo"},"data":[]}),
                        "/api/session/ses_test/interrupt"=>json!({"interrupted":true}),
                        _ if method!="GET"=>return StatusCode::NO_CONTENT.into_response(),
                        _=>return StatusCode::NOT_FOUND.into_response(),
                    }; axum::Json(body).into_response()
                }
            });
            let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
            let addr = listener.local_addr().unwrap();
            let task = tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });
            let client =
                OpenCode2Client::new(&format!("http://{addr}"), "opencode", "fixture-only")
                    .unwrap();
            assert_eq!(
                client.resolve_model("/repo", "").await.unwrap(),
                "provider/model"
            );
            let result = client
                .prompt(
                    "/repo",
                    "ses_test",
                    "planner",
                    "zai-coding-plan/glm-5.3-flash",
                    "instructions",
                    "text",
                    true,
                )
                .await;
            if uncertain {
                assert_eq!(result.unwrap_err().code(), "transport_uncertain");
            } else {
                assert_eq!(result.unwrap()["accepted"], true);
                assert_eq!(
                    client.observe("/repo", "ses_test").await.unwrap()["state"],
                    "completed"
                );
                client.abort("/repo", "ses_test").await.unwrap();
                assert_eq!(
                    calls
                        .lock()
                        .unwrap()
                        .iter()
                        .filter(|s| s.as_str() == "GET /api/session/ses_test/message")
                        .count(),
                    2
                );
            }
            assert_eq!(
                calls
                    .lock()
                    .unwrap()
                    .iter()
                    .filter(|s| s.as_str() == "POST /api/session/ses_test/prompt")
                    .count(),
                1
            );
            task.abort();
        }
    }
    #[tokio::test]
    async fn abort_revokes_and_cancels_queued_work_before_idle_interrupt() {
        use axum::{extract::Request, response::IntoResponse, Router};
        use std::sync::Arc;
        let calls = Arc::new(Mutex::new(Vec::<String>::new()));
        let recorded = calls.clone();
        let router=Router::new().fallback(move |request: Request| {
            let calls=recorded.clone();async move {
                let path=request.uri().path().to_owned();let method=request.method().to_string();
                calls.lock().unwrap().push(format!("{method} {path}"));
                let called=|entry: &str|calls.lock().unwrap().iter().any(|v|v==entry);
                let body=match path.as_str() {
                    "/api/info"=>info(),"/openapi.json"=>spec(),"/api/session/active"=>json!({"data":{}}),
                    "/api/session/ses_test" if method=="GET"=>json!({"data":{"id":"ses_test","location":{"directory":"/repo"},"permissions":if called("PATCH /api/session/ses_test") {deny_all()} else {role_permissions(false)}}}),
                    "/api/session/ses_test/inbox"=>if called("DELETE /api/session/ses_test/inbox/msg_queued") {json!({"data":[]})} else {json!({"data":[{"id":"msg_queued","sessionID":"ses_test","type":"user"}]})},
                    "/api/session/ses_test/interrupt"=>{assert!(called("DELETE /api/session/ses_test/inbox/msg_queued"));json!({"interrupted":false})},
                    _ if method!="GET"=>return StatusCode::NO_CONTENT.into_response(),
                    _=>return StatusCode::NOT_FOUND.into_response(),
                };axum::Json(body).into_response()
            }
        });
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        let task = tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });
        let client =
            OpenCode2Client::new(&format!("http://{addr}"), "opencode", "fixture-only").unwrap();
        client.abort("/repo", "ses_test").await.unwrap();
        let recorded = calls.lock().unwrap();
        assert_eq!(
            recorded
                .iter()
                .filter(|s| s.as_str() == "DELETE /api/session/ses_test/inbox/msg_queued")
                .count(),
            1
        );
        assert_eq!(
            recorded
                .iter()
                .filter(|s| s.as_str() == "POST /api/session/ses_test/interrupt")
                .count(),
            1
        );
        task.abort();
    }
    #[tokio::test]
    async fn wire_policy_mismatch_prevents_agent_model_and_prompt_mutations() {
        use axum::{extract::Request, response::IntoResponse, Router};
        use std::sync::Arc;
        let calls = Arc::new(Mutex::new(Vec::<String>::new()));
        let recorded = calls.clone();
        let router=Router::new().fallback(move |request: Request| {
            let calls=recorded.clone(); async move {
                let path=request.uri().path().to_owned();let method=request.method().to_string();
                calls.lock().unwrap().push(format!("{method} {path}"));
                let body=match path.as_str() {
                    "/api/info"=>info(),"/openapi.json"=>spec(),"/api/session/active"=>json!({"data":{}}),
                    "/api/session/ses_test" if method=="GET"=>json!({"data":{"id":"ses_test","location":{"directory":"/repo"},"permissions":[]}}),
                    "/api/session/ses_test" if method=="PATCH"=>return StatusCode::NO_CONTENT.into_response(),
                    _=>return StatusCode::NOT_FOUND.into_response(),
                }; axum::Json(body).into_response()
            }
        });
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        let task = tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });
        let client =
            OpenCode2Client::new(&format!("http://{addr}"), "opencode", "fixture-only").unwrap();
        assert_eq!(
            client
                .prompt(
                    "/repo",
                    "ses_test",
                    "planner",
                    "provider/model",
                    "instructions",
                    "text",
                    true
                )
                .await
                .unwrap_err()
                .code(),
            "permission_policy_unconfirmed"
        );
        assert_eq!(
            calls
                .lock()
                .unwrap()
                .iter()
                .filter(|s| s.starts_with("POST"))
                .count(),
            0
        );
        task.abort();
    }

    /// A fake OpenCode 2 whose process can be restarted, crash-relaunched or
    /// replaced by another server on the same port.
    struct Restartable {
        pid: std::sync::atomic::AtomicU64,
        tmp: Mutex<String>,
        sessions: Mutex<BTreeSet<String>>,
        schema_ok: std::sync::atomic::AtomicBool,
        password: Mutex<String>,
        schema_reads: std::sync::atomic::AtomicUsize,
    }
    async fn restartable() -> (
        OpenCode2Client,
        std::sync::Arc<Restartable>,
        tokio::task::JoinHandle<()>,
    ) {
        use axum::{extract::Request, response::IntoResponse, Router};
        use std::sync::{atomic::Ordering, Arc};
        let state = Arc::new(Restartable {
            pid: 100.into(),
            tmp: Mutex::new("/data/oc/tmp".into()),
            sessions: Mutex::new(BTreeSet::new()),
            schema_ok: true.into(),
            password: Mutex::new("fixture-only".into()),
            schema_reads: 0.into(),
        });
        let shared = state.clone();
        let router = Router::new().fallback(move |request: Request| {
            let state = shared.clone();
            async move {
                let expected = if state.password.lock().unwrap().as_str() == "fixture-only" {
                    "Basic b3BlbmNvZGU6Zml4dHVyZS1vbmx5"
                } else {
                    "Basic refuses-every-client"
                };
                if request
                    .headers()
                    .get("authorization")
                    .and_then(|v| v.to_str().ok())
                    != Some(expected)
                {
                    return StatusCode::UNAUTHORIZED.into_response();
                }
                let path = request.uri().path().to_owned();
                let directory = Url::parse(&format!("http://fixture{}", request.uri()))
                    .unwrap()
                    .query_pairs()
                    .find(|(k, _)| k == "location[directory]")
                    .map(|(_, v)| v.into_owned())
                    .unwrap_or_default();
                let body = match (request.method().as_str(), path.as_str()) {
                    ("GET", "/api/info") => json!({"version":"2.1.1",
                        "pid":state.pid.load(Ordering::SeqCst),"urls":[],
                        "paths":{"tmp":*state.tmp.lock().unwrap()}}),
                    ("GET", "/openapi.json") => {
                        state.schema_reads.fetch_add(1, Ordering::SeqCst);
                        let mut doc = spec();
                        if !state.schema_ok.load(Ordering::SeqCst) {
                            doc["paths"]["/api/session/active"]["get"]["operationId"] =
                                json!("changed");
                        }
                        doc
                    }
                    ("GET", "/api/session/active") => json!({"data":{}}),
                    ("POST", "/api/session") => {
                        state.sessions.lock().unwrap().insert("ses_team".into());
                        json!({"data":{"id":"ses_team"}})
                    }
                    ("GET", p) if p.starts_with("/api/session/ses_") => {
                        let id = p.trim_start_matches("/api/session/");
                        if !state.sessions.lock().unwrap().contains(id) {
                            return StatusCode::NOT_FOUND.into_response();
                        }
                        json!({"data":{"id":id,"location":{"directory":directory},
                            "permissions":deny_all()}})
                    }
                    _ => return StatusCode::NOT_FOUND.into_response(),
                };
                axum::Json(body).into_response()
            }
        });
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        let task = tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });
        let client =
            OpenCode2Client::new(&format!("http://{addr}"), "opencode", "fixture-only").unwrap();
        (client, state, task)
    }

    #[tokio::test]
    async fn an_app_restart_of_the_same_server_is_proven_again_and_accepted() {
        use std::sync::atomic::Ordering;
        let (client, server, task) = restartable().await;
        client.verify().await.unwrap();
        assert_eq!(server.schema_reads.load(Ordering::SeqCst), 1);
        // Settings › This phone › restart: same files, new process.
        server.pid.store(200, Ordering::SeqCst);
        client.verify().await.unwrap();
        assert_eq!(server.schema_reads.load(Ordering::SeqCst), 2);
        // The new process is pinned: no third proof for it.
        client.verify().await.unwrap();
        assert!(client.server_idle_except(&[]).await.unwrap());
        assert_eq!(server.schema_reads.load(Ordering::SeqCst), 2);
        task.abort();
    }

    #[tokio::test]
    async fn a_relaunch_after_a_crash_must_still_hold_the_teams_sessions() {
        use std::sync::atomic::Ordering;
        let (client, server, task) = restartable().await;
        assert_eq!(
            client.create_session("/repo", "planner").await.unwrap(),
            "ses_team"
        );
        server.pid.store(300, Ordering::SeqCst);
        client.verify().await.unwrap();
        // Relaunched over an empty session store: not the same server.
        server.pid.store(301, Ordering::SeqCst);
        server.sessions.lock().unwrap().clear();
        assert_eq!(
            client.verify().await.unwrap_err().code(),
            "server_identity_changed"
        );
        task.abort();
    }

    #[tokio::test]
    async fn a_foreign_server_on_the_port_never_takes_over() {
        use std::sync::atomic::Ordering;
        let (client, server, task) = restartable().await;
        client.verify().await.unwrap();
        // Another OpenCode 2 with its own files answers on the same port.
        server.pid.store(400, Ordering::SeqCst);
        *server.tmp.lock().unwrap() = "/elsewhere/tmp".into();
        for _ in 0..2 {
            assert_eq!(
                client.verify().await.unwrap_err().code(),
                "server_identity_changed"
            );
            assert!(client.server_idle_except(&[]).await.is_err());
        }
        // One that does not take the app's password is refused outright.
        *server.tmp.lock().unwrap() = "/data/oc/tmp".into();
        *server.password.lock().unwrap() = "someone-else".into();
        assert_eq!(
            client.verify().await.unwrap_err().code(),
            "authentication_failed"
        );
        // One whose schema the driver was not proven against is refused.
        *server.password.lock().unwrap() = "fixture-only".into();
        server.schema_ok.store(false, Ordering::SeqCst);
        assert!(client.verify().await.is_err());
        // The real server coming back (its pid, its files) is accepted.
        server.schema_ok.store(true, Ordering::SeqCst);
        server.pid.store(100, Ordering::SeqCst);
        client.verify().await.unwrap();
        task.abort();
    }
}
