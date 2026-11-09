use crate::{
    attestation::{self, AttestationExpectation, BoundaryTier, VerifiedBoundary},
    chat::{ChatHeartbeat, ChatLedger},
    config::{Config, ServerCredentials},
    opencode::OpenCodeClient,
    repository::RepositoryAuthority,
    store::Store,
};
use axum::{
    extract::{DefaultBodyLimit, Query, Request, State},
    http::{header, StatusCode},
    middleware::{self, Next},
    response::{IntoResponse, Response},
    routing::{delete, get, post},
    Json, Router,
};
use serde::Deserialize;
use serde_json::{json, Value};
use std::{
    fs,
    sync::{Arc, Mutex},
    time::Duration,
};
use subtle::ConstantTimeEq;

pub struct Engine {
    pub config: Config,
    pub store: Mutex<Store>,
    pub repositories: Mutex<RepositoryAuthority>,
    pub server: OpenCodeClient,
    token: Vec<u8>,
    protocol: Mutex<Value>,
    pub(crate) chat: tokio::sync::RwLock<ChatLedger>,
    pub(crate) observer_connected: Mutex<bool>,
    pub(crate) parent_pid: i32,
    lane_admission: tokio::sync::Mutex<()>,
    merge_publication: tokio::sync::Mutex<()>,
    boundary: Option<BoundaryAuthority>,
    deleting: std::sync::atomic::AtomicBool,
}
type Shared = Arc<Engine>;
#[derive(Debug)]
struct ApiError(StatusCode, &'static str);
impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        (self.0, Json(json!({"code":self.1}))).into_response()
    }
}
fn internal(code: &'static str) -> ApiError {
    ApiError(StatusCode::CONFLICT, code)
}

pub async fn serve(config: Config, pins: Option<LaunchPins>) -> Result<(), &'static str> {
    let token = fs::read_to_string(&config.auth_token_file).map_err(|_| "authUnavailable")?;
    let token = token.trim().as_bytes().to_vec();
    if token.len() != 64 || !token.iter().all(u8::is_ascii_hexdigit) {
        return Err("authInvalid");
    }
    let raw =
        fs::read(&config.server_credential_file).map_err(|_| "serverCredentialsUnavailable")?;
    let credentials: ServerCredentials =
        serde_json::from_slice(&raw).map_err(|_| "serverCredentialsInvalid")?;
    let server = OpenCodeClient::new(
        &config.server_base_url,
        &credentials.username,
        &credentials.password,
    )
    .map_err(|_| "serverConfigInvalid")?;
    let store = Store::open(&config.private_root.join("data"), &config.profile_id)
        .map_err(|_| "storeUnavailable")?;
    store.recover().map_err(|_| "recoveryFailed")?;
    let repositories = RepositoryAuthority::new(
        config.private_root.join("repos"),
        config.worker_root.clone(),
    )
    .map_err(|_| "repositoryUnavailable")?;
    let boundary = BoundaryAuthority::start(&config, pins);
    let chat = crate::admission::load_directories(&config);
    let engine = Arc::new(Engine {
        boundary,
        deleting: std::sync::atomic::AtomicBool::new(false),
        lane_admission: tokio::sync::Mutex::new(()),
        merge_publication: tokio::sync::Mutex::new(()),
        chat: tokio::sync::RwLock::new(chat),
        observer_connected: Mutex::new(false),
        parent_pid: unsafe { libc::getppid() },
        config,
        store: Mutex::new(store),
        repositories: Mutex::new(repositories),
        server,
        token,
        protocol: Mutex::new(
            json!({"capabilities":{"execution":false},"blockers":["protocolUnverified"]}),
        ),
    });
    let address = std::net::SocketAddr::from(([127, 0, 0, 1], engine.config.port));
    let listener = tokio::net::TcpListener::bind(address)
        .await
        .map_err(|_| "listenUnavailable")?;
    // The private child pipe introduces its already-bound listener before
    // native sends any bearer token over loopback.
    crate::startup::publish_ready(
        listener
            .local_addr()
            .map_err(|_| "listenUnavailable")?
            .port(),
        &engine.config.profile_id,
        std::str::from_utf8(&engine.token).map_err(|_| "authInvalid")?,
    )?;
    let task_engine = engine.clone();
    let background = tokio::spawn(async move { reconcile(task_engine).await });
    let observer = tokio::spawn(crate::admission::observe(engine.clone()));
    let result = axum::serve(listener, router(engine.clone()))
        .with_graceful_shutdown(shutdown())
        .await;
    background.abort();
    observer.abort();
    // On graceful shutdown, mark unfinished operations interrupted durably.
    // A hard process death is recovered by the same reconcile on next startup.
    if let Ok(store) = engine.store.lock() {
        let _ = store.recover();
    }
    result.map_err(|_| "serverStopped")
}
async fn shutdown() {
    #[cfg(unix)]
    {
        let mut term = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
            .expect("signal handler");
        tokio::select! { _ = term.recv() => {}, _ = tokio::signal::ctrl_c() => {} }
    }
    #[cfg(not(unix))]
    {
        let _ = tokio::signal::ctrl_c().await;
    }
}
pub fn router(engine: Shared) -> Router {
    Router::new()
        .route("/v1/health", get(health))
        .route("/v1/workspace", get(workspace))
        .route("/v1/commands", post(command))
        .route("/v1/chatBusy", post(chat_busy))
        .route("/v1/events", get(events))
        .route("/v1/profile", delete(delete_profile))
        .route_layer(middleware::from_fn_with_state(engine.clone(), authorize))
        .layer(DefaultBodyLimit::max(1024 * 1024))
        .with_state(engine)
}
async fn authorize(State(engine): State<Shared>, request: Request, next: Next) -> Response {
    // Never put auth material in routes, debug output, errors or redirects.
    let supplied = request
        .headers()
        .get(header::AUTHORIZATION)
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "))
        .unwrap_or("")
        .as_bytes();
    if supplied.len() != engine.token.len() || !bool::from(supplied.ct_eq(&engine.token)) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(json!({"code":"unauthorized"})),
        )
            .into_response();
    }
    let mut response = next.run(request).await;
    response
        .headers_mut()
        .insert(header::CACHE_CONTROL, "no-store".parse().unwrap());
    response
}
const STORE_ACTIONS: &[&str] = &[
    "createProject",
    "createQuickTask",
    "saveSpecDraft",
    "approveSpec",
    "retryPlan",
    "approvePlan",
    "acceptPhase",
    "acceptMilestone",
    "updateDefaults",
    "updateSettings",
    "pauseProject",
    "resumeProject",
    "stopProject",
    "pauseTask",
    "resumeTask",
    "stopTask",
    "acknowledgeDigest",
    "deleteProject",
    "saveRole",
    "deleteRole",
    "updateServer",
];
pub struct LaunchPins {
    pub trusted_public_key_sha256: String,
    pub generation: String,
    pub policy_sha256: String,
}
struct BoundaryAuthority {
    pins: LaunchPins,
    parent: i32,
    executable: std::path::PathBuf,
    receipt: VerifiedBoundary,
}
impl BoundaryAuthority {
    fn expectation<'a>(&'a self, config: &'a Config) -> Option<AttestationExpectation<'a>> {
        if config.boundary.generation.as_deref() != Some(&self.pins.generation) {
            return None;
        }
        let receipt_file = config.boundary.receipt_file.as_deref()?;
        let public_key_file = config.boundary.public_key_file.as_deref()?;
        for file in [receipt_file, public_key_file] {
            if !file.is_absolute() || !file.starts_with(&config.private_root) {
                return None;
            }
        }
        Some(AttestationExpectation {
            receipt_file,
            public_key_file,
            profile_id: &config.profile_id,
            generation: &self.pins.generation,
            trusted_public_key_sha256: &self.pins.trusted_public_key_sha256,
            expected_parent_pid: self.parent,
            executable_path: &self.executable,
            expected_policy_sha256: &self.pins.policy_sha256,
        })
    }
    fn start(config: &Config, pins: Option<LaunchPins>) -> Option<Self> {
        let pins = pins?;
        let executable = std::env::current_exe().ok()?;
        let mut authority = Self {
            pins,
            parent: unsafe { libc::getppid() },
            executable,
            receipt: VerifiedBoundary {
                tier: BoundaryTier::Landlock,
                generation: String::new(),
                policy_sha256: String::new(),
                receipt_sha256: String::new(),
            },
        };
        authority.receipt = attestation::verify(&authority.expectation(config)?).ok()?;
        Some(authority)
    }
}
fn boundary_verified(e: &Engine) -> bool {
    boundary_tier(e).is_some()
}
fn boundary_tier(e: &Engine) -> Option<BoundaryTier> {
    e.boundary
        .as_ref()
        .and_then(|b| {
            b.expectation(&e.config)
                .and_then(|expected| attestation::verify_current(&expected, &b.receipt).ok())
        })
        .map(|receipt| receipt.tier)
}
fn execution_enabled(engine: &Engine) -> bool {
    !engine.deleting.load(std::sync::atomic::Ordering::SeqCst)
        && boundary_verified(engine)
        && engine.protocol.lock().ok().is_some_and(|p| {
            p["capabilities"]["executionDriver"] == true
                && p["pinnedVersion"] == true
                && p["openapiVerified"] == true
        })
}
async fn chat_busy(
    State(e): State<Shared>,
    input: Result<Json<ChatHeartbeat>, axum::extract::rejection::JsonRejection>,
) -> Result<Json<Value>, ApiError> {
    let Json(heartbeat) = input.map_err(|_| internal("heartbeatInvalid"))?;
    Ok(Json(
        crate::admission::accept(&e, heartbeat)
            .await
            .map_err(internal)?,
    ))
}
fn command_actions(e: &Engine) -> Vec<&'static str> {
    let mut actions: Vec<_> = STORE_ACTIONS
        .iter()
        .copied()
        .filter(|action| {
            boundary_verified(e) || !matches!(*action, "createProject" | "createQuickTask")
        })
        .collect();
    if execution_enabled(e) {
        actions.push("promote");
    }
    actions
}
async fn health(State(e): State<Shared>) -> Json<Value> {
    let verified_tier = boundary_tier(&e);
    let tier = verified_tier.map_or("none", BoundaryTier::as_str);
    let protocol = e.protocol.lock().map(|v| v.clone()).unwrap_or(json!({}));
    let ledger = e.chat.read().await;
    let admission = crate::admission::label(ledger.admission(
        crate::admission::now_ms(),
        crate::admission::app_alive(&e),
        &team_sessions(&e),
    ));
    Json(
        json!({"schemaVersion":1,"engineVersion":env!("CARGO_PKG_VERSION"),"profileId":e.config.profile_id,"boundaryTier":tier,
        "capabilities":{"execution":execution_enabled(&e),"boundary":verified_tier.is_some(),"boundaryTier":tier,
            "oc2":protocol["capabilities"]["oc2"] == true,
            "oc2Verified":protocol["capabilities"]["oc2"] == true && protocol["pinnedVersion"] == true && protocol["openapiVerified"] == true},
        "commandActions":command_actions(&e),"boundaryReason":if verified_tier.is_some() {"boundary_attested"} else if e.boundary.is_some() {"attestation_rejected"} else {e.config.boundary.reason.as_str()},
        "readinessReason":readiness_reason(&e),"restartRequired":e.config.boundary.restart_required,
        "admission":admission,"chatAuthority":"phoneAppAndKnownDirectories","globalAdmissionAuthority":false,
        "boundaryGeneration":e.boundary.as_ref().map(|b|b.pins.generation.as_str()),"eventWindow":e.store.lock().ok().and_then(|store|store.event_window().ok()),"chargingTelemetry":false,"protocol":protocol}),
    )
}
fn protocol_driver_ready(protocol: &Value) -> bool {
    protocol["pinnedVersion"] == true
        && protocol["openapiVerified"] == true
        && protocol["capabilities"]["executionDriver"] == true
}
fn protocol_driver_reason(protocol: &Value) -> &str {
    if protocol_driver_ready(protocol) {
        ""
    } else {
        protocol["error"].as_str().unwrap_or("protocolUnverified")
    }
}
fn readiness_reason(e: &Engine) -> String {
    if boundary_tier(e).is_none() {
        return "boundaryUnavailable".into();
    }
    e.protocol
        .lock()
        .map(|p| protocol_driver_reason(&p).to_owned())
        .unwrap_or_else(|_| "protocolUnverified".into())
}
fn protocol_retry_delay(protocol: &Value) -> Duration {
    Duration::from_secs(if protocol_driver_ready(protocol) {
        30
    } else {
        2
    })
}
async fn workspace(State(e): State<Shared>) -> Result<Json<Value>, ApiError> {
    let mut workspace = e
        .store
        .lock()
        .map_err(|_| internal("storeUnavailable"))?
        .workspace()
        .map_err(|_| internal("storeUnavailable"))?;
    let protocol = e.protocol.lock().map(|p| p.clone()).unwrap_or(json!({}));
    let ledger = e.chat.read().await;
    let admission = ledger.admission(
        crate::admission::now_ms(),
        crate::admission::app_alive(&e),
        &team_sessions(&e),
    );
    if let Some(servers) = workspace["servers"].as_array_mut() {
        for server in servers.iter_mut().filter(|s| s["id"] == "phone") {
            server["online"] = json!(protocol_driver_ready(&protocol));
            server["chatWaiting"] = json!(admission != crate::chat::ChatAdmission::Idle);
            server["reason"] = json!(if !protocol_driver_ready(&protocol) {
                protocol_driver_reason(&protocol)
            } else {
                match admission {
                    crate::chat::ChatAdmission::Busy => "chatBusy",
                    crate::chat::ChatAdmission::Idle => "",
                    _ => "chatStatusUnknown",
                }
            });
        }
    }
    Ok(Json(workspace))
}
#[derive(Deserialize)]
struct EventQuery {
    #[serde(default)]
    after: i64,
    #[serde(default = "event_limit")]
    limit: usize,
}
fn event_limit() -> usize {
    100
}
async fn events(
    State(e): State<Shared>,
    Query(q): Query<EventQuery>,
) -> Result<Response, ApiError> {
    if q.after < 0 || q.limit == 0 || q.limit > 500 {
        return Err(internal("queryInvalid"));
    }
    let s = e.store.lock().map_err(|_| internal("storeUnavailable"))?;
    match s.events(q.after, q.limit) {
        Ok(mut items) => {
            for item in &mut items {
                item["type"] = item["kind"].clone();
            }
            Ok(Json(json!(items)).into_response())
        }
        Err(error) if error.code() == "cursorExpired" => Ok((
            StatusCode::CONFLICT,
            Json(json!({"code":"cursorExpired","resetRequired":true,
                "eventWindow":s.event_window().map_err(|_|internal("storeUnavailable"))?})),
        )
            .into_response()),
        Err(_) => Err(internal("storeUnavailable")),
    }
}
async fn command(
    State(e): State<Shared>,
    input: Result<Json<Value>, axum::extract::rejection::JsonRejection>,
) -> Result<Json<Value>, ApiError> {
    let Json(mut c) = input.map_err(|_| internal("commandInvalid"))?;
    // Serialize command-side imports and sweeps with starting reservations.
    let _command_fence = e.lane_admission.lock().await;
    let action_value = c["action"]
        .as_str()
        .ok_or(internal("commandInvalid"))?
        .to_owned();
    let action = action_value.as_str();
    if action == "promote" {
        if !execution_enabled(&e) {
            return Err(internal("executionUnavailable"));
        }
        if c["confirmed"] != true {
            return Err(internal("confirmationRequired"));
        }
        let store = e.store.lock().map_err(|_| internal("storeUnavailable"))?;
        let state = store
            .workspace()
            .map_err(|_| internal("storeUnavailable"))?;
        let project = state["projects"]
            .as_array()
            .and_then(|ps| ps.iter().find(|p| p["id"] == c["projectId"]))
            .ok_or(internal("projectMissing"))?;
        let replay = project["receipts"]
            .as_array()
            .is_some_and(|rs| rs.iter().any(|r| r["id"] == c["requestId"]));
        if !replay && c["expectedRevision"] != project["revision"] {
            return Err(internal("staleRevision"));
        }
        let repos = project["repos"].as_array().ok_or(internal("repoInvalid"))?;
        if repos.len() != 1 {
            return Err(internal("crossRepoPromotionUnavailable"));
        }
        let repo_id = repos[0]["id"].as_str().ok_or(internal("repoInvalid"))?;
        if c["targetId"].as_str().unwrap_or("").is_empty() {
            c["targetId"] = json!(repo_id);
        }
        if c["targetId"] != repo_id {
            return Err(internal("repoInvalid"));
        }
        if let Some(result) = store
            .command_result(&c)
            .map_err(|error| internal(error.code()))?
        {
            return Ok(Json(result));
        }
        let receipt = e
            .repositories
            .lock()
            .map_err(|_| internal("repositoryUnavailable"))?
            .promote(
                repo_id,
                c["expectedDevCommit"].as_str().unwrap_or(""),
                c["expectedMainCommit"].as_str().unwrap_or(""),
                true,
                c["requestId"].as_str().unwrap_or(""),
            )
            .map_err(|error| internal(error.code()))?;
        return Ok(Json(
            store
                .record_promotion(&c, &receipt)
                .map_err(|_| internal("receiptUncertain"))?,
        ));
    }
    if !STORE_ACTIONS.contains(&action) {
        // A promotion/merge can only enter via reviewed, typed authority calls.
        // No generic shell, ref update or unauthenticated write endpoint exists.
        return Ok(Json(
            json!({"accepted":false,"code":"unsupported","projectId":c["projectId"],"revision":0,"replayed":false}),
        ));
    }
    let previous_result = {
        let store = e.store.lock().map_err(|_| internal("storeUnavailable"))?;
        store
            .command_result(&c)
            .map_err(|error| internal(error.code()))?
    };
    if let Some(result) = previous_result {
        let mut result = result;
        if action == "deleteProject" && result["accepted"] == true {
            result["cleanupPending"] = json!(collect_unused_repositories(&e).is_err());
        }
        return Ok(Json(result));
    }
    {
        let store = e.store.lock().map_err(|_| internal("storeUnavailable"))?;
        if store.validate_command(&c).is_err() {
            // Persist the rejection before touching canonical storage.
            return Ok(Json(
                store.execute(&c).map_err(|error| internal(error.code()))?,
            ));
        }
    }
    if matches!(action, "approveSpec" | "retryPlan")
        || action == "createQuickTask" && c["confirmed"] == true
    {
        let (directory, requested) = {
            let store = e.store.lock().map_err(|_| internal("storeUnavailable"))?;
            let workspace = store
                .workspace()
                .map_err(|_| internal("storeUnavailable"))?;
            let repos = if action == "createQuickTask" {
                &c["repos"]
            } else {
                &workspace["projects"]
                    .as_array()
                    .and_then(|projects| projects.iter().find(|p| p["id"] == c["projectId"]))
                    .ok_or(internal("projectMissing"))?["repos"]
            };
            let directory = repos
                .as_array()
                .and_then(|repos| repos.first())
                .and_then(|repo| repo["path"].as_str())
                .ok_or(internal("repoInvalid"))?
                .to_owned();
            let role_id = if action == "createQuickTask" {
                c["roleId"]
                    .as_str()
                    .filter(|s| !s.is_empty())
                    .unwrap_or("worker")
            } else {
                "planner"
            };
            let requested = workspace["roles"]
                .as_array()
                .and_then(|roles| roles.iter().find(|role| role["id"] == role_id))
                .and_then(|role| role["model"].as_str())
                .ok_or(internal("invalid_role"))?
                .to_owned();
            (directory, requested)
        };
        // Refuse before recording a queued planner or creating a session. A
        // later config change is checked again at actual session admission.
        resolved_server_model(&e, &directory, &requested)
            .await
            .map_err(internal)?;
    }
    let mut imported = vec![];
    if matches!(action, "createProject" | "createQuickTask") {
        // Never put canonical Git authority beside an unconfined legacy
        // server. Disabled scheduling alone cannot protect raw refs on disk.
        if !boundary_verified(&e) {
            return Err(internal("boundaryUnavailable"));
        }
        imported = match import_requested_repositories(&e, &c) {
            Ok(value) => value,
            Err(error) => {
                let _ = collect_unused_repositories(&e);
                return Err(error);
            }
        };
    }
    // Capture sessions before deleteProject removes its durable jobs.
    let interrupted_jobs = if matches!(
        action,
        "pauseProject" | "stopProject" | "pauseTask" | "stopTask" | "deleteProject"
    ) {
        e.store
            .lock()
            .map_err(|_| internal("storeUnavailable"))?
            .jobs()
            .map_err(|_| internal("storeUnavailable"))?
    } else {
        vec![]
    };
    let result = {
        let store = e.store.lock().map_err(|_| internal("storeUnavailable"))?;
        if imported.is_empty() {
            store.execute(&c)
        } else {
            store.execute_with_repositories(&c, &json!(imported))
        }
        .map_err(|error| internal(error.code()))?
    };
    if result["accepted"] == true
        && matches!(
            action,
            "pauseProject" | "stopProject" | "pauseTask" | "stopTask" | "deleteProject"
        )
    {
        for job in interrupted_jobs.iter().filter(|j| {
            j["projectId"] == c["projectId"]
                && (!matches!(action, "pauseTask" | "stopTask") || j["taskId"] == c["targetId"])
        }) {
            if let (Some(directory), Some(ids)) =
                (job["directory"].as_str(), job["sessionIds"].as_object())
            {
                for id in ids.values().filter_map(Value::as_str) {
                    let _ = e.server.abort(directory, id).await;
                }
            }
        }
    }
    let mut result = result;
    if action == "deleteProject" && result["accepted"] == true
        || matches!(action, "createProject" | "createQuickTask") && result["accepted"] != true
    {
        // A durable accepted delete stays accepted even if storage cleanup
        // needs a retry; never invite a duplicate command after committing it.
        result["cleanupPending"] = json!(collect_unused_repositories(&e).is_err());
    }
    Ok(Json(result))
}
fn collect_unused_repositories(e: &Engine) -> Result<Value, ApiError> {
    let store = e.store.lock().map_err(|_| internal("storeUnavailable"))?;
    let workspace = store
        .workspace()
        .map_err(|_| internal("storeUnavailable"))?;
    let retained: Vec<String> = workspace["projects"]
        .as_array()
        .ok_or(internal("storeUnavailable"))?
        .iter()
        .flat_map(|p| p["repos"].as_array().into_iter().flatten())
        .filter_map(|r| r["id"].as_str().map(str::to_owned))
        .collect();
    e.repositories
        .lock()
        .map_err(|_| internal("repositoryUnavailable"))?
        .collect_unreferenced_repositories(&retained)
        .map_err(|_| internal("repositoryCleanupPending"))
}
fn import_requested_repositories(e: &Engine, c: &Value) -> Result<Vec<Value>, ApiError> {
    let repos = c["repos"].as_array().ok_or(internal("reposRequired"))?;
    if repos.is_empty() || repos.len() > 32 {
        return Err(internal("reposRequired"));
    }
    let mut imported = Vec::new();
    for repo in repos {
        let id = repo["id"].as_str().ok_or(internal("repoInvalid"))?;
        let source = e
            .config
            .source_path(repo["path"].as_str().ok_or(internal("repoInvalid"))?)
            .map_err(internal)?;
        let refs = e
            .repositories
            .lock()
            .map_err(|_| internal("repositoryUnavailable"))?
            .import_repo_for_request(
                id,
                &source,
                c["requestId"]
                    .as_str()
                    .ok_or(internal("requestIdRequired"))?,
            )
            .map_err(|error| internal(error.code()))?;
        let mut edited = repo.clone();
        edited["devCommit"] = refs["devCommit"].clone();
        edited["mainCommit"] = refs["mainCommit"].clone();
        imported.push(edited);
    }
    Ok(imported)
}
async fn delete_profile(State(e): State<Shared>) -> Result<Json<Value>, ApiError> {
    e.deleting.store(true, std::sync::atomic::Ordering::SeqCst);
    let _dispatch_fence = e.chat.write().await;
    // Store deletion is a durable tombstone, serialized with every job write.
    // Native owner stops processes and sweeps canonical/worker files afterward.
    let jobs = e
        .store
        .lock()
        .map_err(|_| internal("storeUnavailable"))?
        .jobs()
        .or_else(|error| {
            if error.code() == "profileDeleted" {
                Ok(vec![])
            } else {
                Err(error)
            }
        })
        .map_err(|_| internal("storeUnavailable"))?;
    e.store
        .lock()
        .map_err(|_| internal("storeUnavailable"))?
        .delete_profile()
        .map_err(|_| internal("deleteFailed"))?;
    for job in jobs {
        if let (Some(directory), Some(ids)) =
            (job["directory"].as_str(), job["sessionIds"].as_object())
        {
            for id in ids.values().filter_map(Value::as_str) {
                let _ = e.server.abort(directory, id).await;
            }
        }
    }
    Ok(Json(json!({"deleted":true})))
}
async fn reconcile(e: Shared) {
    let mut ticks = tokio::time::interval(Duration::from_secs(2));
    let mut active = std::collections::HashSet::new();
    let mut tasks = tokio::task::JoinSet::new();
    let mut next_verify = tokio::time::Instant::now();
    loop {
        ticks.tick().await;
        if e.deleting.load(std::sync::atomic::Ordering::SeqCst) {
            let tombstoned = e
                .store
                .lock()
                .ok()
                .and_then(|store| store.workspace().err())
                .is_some_and(|error| error.code() == "profileDeleted");
            if tombstoned {
                tasks.abort_all();
                while tasks.join_next().await.is_some() {}
                return;
            }
            // DELETE is still waiting for the dispatch fence. Cancelling a
            // request before its ACK could let it start after an early abort.
            continue;
        }
        while let Some(result) = tasks.try_join_next() {
            if let Ok(id) = result {
                active.remove(&id);
            }
        }
        if tokio::time::Instant::now() >= next_verify {
            let protocol = e.server.verify().await.unwrap_or_else(|error| json!({"capabilities":{"executionDriver":false,"execution":false,"oc2":true},"error":error.code(),"blockers":[error.code()]}));
            let retry_delay = protocol_retry_delay(&protocol);
            if let Ok(mut value) = e.protocol.lock() {
                *value = protocol;
            }
            next_verify = tokio::time::Instant::now() + retry_delay;
        }
        crate::admission::reconcile_busy(&e).await;
        if execution_enabled(&e) {
            if let Ok(store) = e.store.lock() {
                let _ = store.reconcile_restart_jobs();
            }
        }
        // Publish interrupted canonical merges before any later task may
        // advance dev. The private journal provides the original before/after
        // refs; observation cannot guess an earlier receipt from current refs.
        if execution_enabled(&e) {
            let pending = e
                .store
                .lock()
                .ok()
                .and_then(|s| s.jobs().ok())
                .unwrap_or_default();
            for job in pending
                .iter()
                .filter(|j| j["stage"] == "resuming" && j["resumeStage"] == "merging")
            {
                if let Err(reason) = recover_merge_publication(&e, job).await {
                    let _ = patch(
                        &e,
                        job["id"].as_str().unwrap_or(""),
                        "resuming",
                        json!({"stage":"interrupted","reason":reason}),
                    );
                }
            }
        }
        let jobs = e
            .store
            .lock()
            .ok()
            .and_then(|s| s.jobs().ok())
            .unwrap_or_default();
        for job in jobs {
            let id = job["id"].as_str().unwrap_or("").to_owned();
            if id.is_empty()
                || active.contains(&id)
                || !matches!(job["stage"].as_str(), Some("queued" | "resuming"))
            {
                continue;
            }
            if !execution_enabled(&e) {
                let reason = readiness_reason(&e);
                if job["reason"] != reason {
                    if let Ok(s) = e.store.lock() {
                        let _ = s.update_job(
                            &id,
                            job["stage"].as_str().unwrap_or("queued"),
                            &json!({"reason":reason}),
                        );
                    }
                }
                continue;
            }
            let ledger = e.chat.read().await;
            if job["stage"] != "resuming"
                && !crate::admission::idle(&e, &ledger).await.unwrap_or(false)
            {
                let reason = if ledger.admission(
                    crate::admission::now_ms(),
                    crate::admission::app_alive(&e),
                    &team_sessions(&e),
                ) == crate::chat::ChatAdmission::Busy
                {
                    "chatBusy"
                } else {
                    "chatStatusUnknown"
                };
                drop(ledger);
                if job["reason"] != reason {
                    if let Ok(store) = e.store.lock() {
                        let _ = store.update_job(
                            &id,
                            job["stage"].as_str().unwrap_or("queued"),
                            &json!({"reason":reason}),
                        );
                    }
                }
                continue;
            }
            drop(ledger);
            // The native proof and protocol admission prerequisites are checked
            // again at every stage. No SSE event is treated as completion.
            active.insert(id.clone());
            let owned = e.clone();
            tasks.spawn(async move {
                let _ = run_job(owned, job).await;
                id
            });
        }
    }
}
pub(crate) fn team_sessions(e: &Engine) -> Vec<String> {
    e.store
        .lock()
        .ok()
        .and_then(|s| s.jobs().ok())
        .unwrap_or_default()
        .iter()
        .flat_map(|j| {
            j["sessionIds"]
                .as_object()
                .into_iter()
                .flat_map(|o| o.values())
        })
        .filter_map(|v| v.as_str().map(str::to_owned))
        .collect()
}
struct AdmissionRetry {
    usage_unknown_since: Option<tokio::time::Instant>,
    usage_grace: Duration,
    interval: Duration,
}
impl Default for AdmissionRetry {
    fn default() -> Self {
        Self {
            usage_unknown_since: None,
            usage_grace: Duration::from_secs(30),
            interval: Duration::from_secs(2),
        }
    }
}
impl AdmissionRetry {
    fn may_wait(&mut self, reason: &'static str) -> bool {
        match reason {
            "chatBusy" | "chatStatusUnknown" => {
                self.usage_unknown_since = None;
                true
            }
            "totalUsageUnknown" | "dailyUsageUnknown" | "tokenUsageUnknown" => {
                self.usage_unknown_since
                    .get_or_insert_with(tokio::time::Instant::now)
                    .elapsed()
                    < self.usage_grace
            }
            _ => false,
        }
    }
}
fn record_admission_reason(
    e: &Engine,
    requested: &Value,
    reason: &str,
) -> Result<(), &'static str> {
    let store = e.store.lock().map_err(|_| "storeUnavailable")?;
    let current = store
        .jobs()
        .map_err(|_| "storeUnavailable")?
        .into_iter()
        .find(|j| j["id"] == requested["id"])
        .ok_or("jobMissing")?;
    let stage = current["stage"].as_str().ok_or("jobInvalid")?;
    if !crate::scheduler::active_stage(stage) && !matches!(stage, "queued" | "mergeReady") {
        return Err("jobNotActive");
    }
    let previous = current["reason"].as_str().unwrap_or("");
    if previous == reason
        || (reason.is_empty()
            && !matches!(
                previous,
                "chatBusy"
                    | "chatStatusUnknown"
                    | "totalUsageUnknown"
                    | "dailyUsageUnknown"
                    | "tokenUsageUnknown"
            ))
    {
        return Ok(());
    }
    store
        .update_job(
            current["id"].as_str().ok_or("jobInvalid")?,
            current["stage"].as_str().ok_or("jobInvalid")?,
            &json!({"reason":reason}),
        )
        .map_err(|error| error.code())?;
    Ok(())
}
async fn stage_admitted(e: &Engine, requested: &Value) -> Result<(), &'static str> {
    stage_admitted_retry(e, requested, AdmissionRetry::default()).await
}
async fn stage_admitted_retry(
    e: &Engine,
    requested: &Value,
    mut retry: AdmissionRetry,
) -> Result<(), &'static str> {
    if !execution_enabled(e) {
        return Err("executionUnavailable");
    }
    loop {
        let ledger = e.chat.read().await;
        let result = stage_admitted_with_ledger(e, requested, &ledger).await;
        drop(ledger);
        match result {
            Ok(()) => {
                record_admission_reason(e, requested, "")?;
                return Ok(());
            }
            Err(reason) if retry.may_wait(reason) => {
                record_admission_reason(e, requested, reason)?;
                // Another lane's first cumulative usage may arrive after its
                // dispatch. Refetch briefly; never substitute zero or dispatch
                // through unknown budgets. Person-chat waits remain unbounded.
                tokio::time::sleep(retry.interval).await;
            }
            Err(reason) => return Err(reason),
        }
    }
}
async fn stage_admitted_with_ledger(
    e: &Engine,
    requested: &Value,
    ledger: &ChatLedger,
) -> Result<(), &'static str> {
    if e.deleting.load(std::sync::atomic::Ordering::SeqCst) {
        return Err("profileDeleted");
    }
    if !execution_enabled(e) {
        return Err("executionUnavailable");
    }
    {
        let store = e.store.lock().map_err(|_| "storeUnavailable")?;
        let jobs = store.jobs().map_err(|_| "storeUnavailable")?;
        let current = jobs
            .iter()
            .find(|j| j["id"] == requested["id"])
            .ok_or("jobMissing")?;
        if matches!(
            current["stage"].as_str(),
            Some("paused" | "stopped" | "interrupted" | "failed")
        ) {
            return Err("jobNotActive");
        }
    }
    let idle = crate::admission::idle(e, ledger).await?;
    let store = e.store.lock().map_err(|_| "storeUnavailable")?;
    let state = store.workspace().map_err(|_| "storeUnavailable")?;
    let jobs = store.jobs().map_err(|_| "storeUnavailable")?;
    let current = jobs
        .iter()
        .find(|j| j["id"] == requested["id"])
        .ok_or("jobMissing")?;
    if matches!(
        current["stage"].as_str(),
        Some("paused" | "stopped" | "interrupted" | "failed")
    ) {
        return Err("jobNotActive");
    }
    let project = state["projects"]
        .as_array()
        .and_then(|ps| ps.iter().find(|p| p["id"] == current["projectId"]))
        .ok_or("projectMissing")?;
    let mut project = project.clone();
    let project_jobs: Vec<&Value> = jobs
        .iter()
        .filter(|j| j["projectId"] == current["projectId"])
        .collect();
    let observed_cost = project_jobs
        .iter()
        .try_fold(0.0, |sum, j| Some(sum + accounted_cost(j)?));
    let observed_daily_cost = project_jobs.iter().try_fold(0.0, |sum, j| {
        Some(sum + accounted_daily_cost(j, &utc_day())?)
    });
    project["spendDay"] = json!(utc_day());
    let others: Vec<Value> = jobs
        .iter()
        .filter(|j| j["id"] != current["id"])
        .cloned()
        .collect();
    let mut candidate = current.clone();
    candidate["stage"] = json!("queued");
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_err(|_| "clockUnknown")?
        .as_millis() as u64;
    let context = crate::scheduler::AdmissionContext {
        now_ms: now,
        chat_idle: Some(idle),
        chat_observed_ms: Some(now),
        chat_max_age_ms: 5000,
        charging: None,
        server_online: true,
        lane_cap: 32,
        utc_day: utc_day(),
        observed_total_cost: observed_cost,
        observed_daily_cost,
        observed_task_tokens: accounted_tokens(current),
    };
    match crate::scheduler::evaluate(&project, &candidate, &others, &context) {
        crate::scheduler::Admission::Admit => Ok(()),
        crate::scheduler::Admission::Wait(code) | crate::scheduler::Admission::Pause(code) => {
            Err(code)
        }
    }
}
fn fresh_undispatched_role(job: &Value, role: &str, session: &Value) -> bool {
    session.as_str().is_some_and(|id| !id.is_empty())
        && job["freshSessionIds"]
            .as_object()
            .is_some_and(|fresh| fresh.get(role) == Some(session))
        && (job["promptDispatch"].is_null()
            || job["promptDispatch"]
                .as_object()
                .is_some_and(|d| !d.contains_key(role)))
}
fn accounted_cost(job: &Value) -> Option<f64> {
    let sessions = job["sessionIds"].as_object()?;
    sessions
        .iter()
        .try_fold(0.0, |sum, (role, id)| {
            let cost = job["sessionUsage"][role]["cost"]
                .as_f64()
                .filter(|n| n.is_finite() && *n >= 0.0)
                .or_else(|| fresh_undispatched_role(job, role, id).then_some(0.0))?;
            Some(sum + cost)
        })
        .filter(|n| n.is_finite())
}
fn accounted_tokens(job: &Value) -> Option<u64> {
    job["sessionIds"]
        .as_object()?
        .iter()
        .try_fold(0u64, |sum, (role, id)| {
            let tokens = job["sessionUsage"][role]["tokens"]
                .as_u64()
                .or_else(|| fresh_undispatched_role(job, role, id).then_some(0))?;
            sum.checked_add(tokens)
        })
}
fn accounted_daily_cost(job: &Value, day: &str) -> Option<f64> {
    job["sessionIds"]
        .as_object()?
        .iter()
        .try_fold(0.0, |sum, (role, id)| {
            let usage = &job["sessionUsage"][role];
            let cost = if fresh_undispatched_role(job, role, id) {
                0.0
            } else if usage["day"] == day {
                usage["dailyCost"]
                    .as_f64()
                    .filter(|n| n.is_finite() && *n >= 0.0)?
            } else if matches!(job["stage"].as_str(), Some("completed" | "merged"))
                && usage["day"].as_str().is_some_and(|old| old < day)
            {
                0.0
            } else {
                return None;
            };
            Some(sum + cost)
        })
        .filter(|n| n.is_finite())
}
#[allow(clippy::too_many_arguments)]
async fn admitted_prompt(
    e: &Engine,
    job: &Value,
    directory: &str,
    session: &str,
    role: &str,
    model: &str,
    instructions: &str,
    prompt: &str,
    checkpoint_role: &str,
    read_only: bool,
) -> Result<(), &'static str> {
    admitted_prompt_on(
        e,
        "promptDispatch",
        job,
        directory,
        session,
        role,
        model,
        instructions,
        prompt,
        checkpoint_role,
        read_only,
    )
    .await
}
/// [admitted_prompt] on a chosen dispatch ledger: `promptDispatch` for a
/// step's first prompt, `restartDispatch` for its one resend after OpenCode
/// restarted under it.
#[allow(clippy::too_many_arguments)]
async fn admitted_prompt_on(
    e: &Engine,
    ledger_key: &str,
    job: &Value,
    directory: &str,
    session: &str,
    role: &str,
    model: &str,
    instructions: &str,
    prompt: &str,
    checkpoint_role: &str,
    read_only: bool,
) -> Result<(), &'static str> {
    if model.is_empty() {
        return Err("modelNotConfigured");
    }
    crate::opencode::validate_model(model).map_err(|error| error.code())?;
    if instructions.is_empty() || prompt.is_empty() {
        return Err("invalid_role");
    }
    let mut retry = AdmissionRetry::default();
    loop {
        let ledger = e.chat.read().await;
        // Hold through actual HTTP dispatch, never while waiting for the person.
        match stage_admitted_with_ledger(e, job, &ledger).await {
            Err(reason) if retry.may_wait(reason) => {
                drop(ledger);
                record_admission_reason(e, job, reason)?;
                tokio::time::sleep(retry.interval).await;
            }
            Err(code) => return Err(code),
            Ok(()) => {
                record_admission_reason(e, job, "")?;
                let id = job["id"].as_str().ok_or("jobInvalid")?;
                record_dispatch_on(e, ledger_key, id, checkpoint_role, "dispatching").await?;
                let dispatch = tokio::time::timeout(
                    Duration::from_secs(5),
                    e.server.prompt(
                        directory,
                        session,
                        role,
                        model,
                        instructions,
                        prompt,
                        read_only,
                    ),
                )
                .await;
                drop(ledger);
                if active_stage(e, id).is_err() {
                    let _ = e.server.abort(directory, session).await;
                    return Err("jobNotActive");
                }
                match dispatch {
                    Ok(Ok(_)) => {
                        if record_dispatch_on(e, ledger_key, id, checkpoint_role, "dispatched")
                            .await
                            .is_err()
                        {
                            let _ = e.server.abort(directory, session).await;
                            return Err("jobNotActive");
                        }
                        return Ok(());
                    }
                    Ok(Err(error)) if error.code() != "transport_uncertain" => {
                        return Err(error.code())
                    }
                    _ => return Err("promptUncertain"),
                }
            }
        }
    }
}
fn active_stage(e: &Engine, id: &str) -> Result<String, &'static str> {
    if e.deleting.load(std::sync::atomic::Ordering::SeqCst) {
        return Err("profileDeleted");
    }
    let store = e.store.lock().map_err(|_| "storeUnavailable")?;
    let jobs = store.jobs().map_err(|_| "jobChanged")?;
    let stage = jobs
        .iter()
        .find(|j| j["id"] == id)
        .and_then(|j| j["stage"].as_str())
        .ok_or("jobChanged")?;
    if matches!(
        stage,
        "paused" | "stopped" | "interrupted" | "failed" | "completed"
    ) {
        return Err("jobNotActive");
    }
    Ok(stage.to_owned())
}
async fn record_dispatch_on(
    e: &Engine,
    ledger_key: &str,
    id: &str,
    role: &str,
    state: &str,
) -> Result<(), &'static str> {
    let stage = active_stage(e, id)?;
    let mut value = json!({ledger_key:{role:state}});
    if state == "dispatched" {
        // The process that accepted the turn. If the turn later dies under
        // another proven pid, OpenCode restarted under it (see await_completion).
        // Read after the prompt, so it is never older than the accepting pid.
        if let Some(pid) = e.server.server_pid().await {
            value["dispatchServerPid"] = json!({role:pid});
        }
    }
    patch(e, id, &stage, value).map(|_| ())
}
fn utc_day() -> String {
    #[cfg(unix)]
    {
        let seconds = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as libc::time_t;
        let mut time = std::mem::MaybeUninit::<libc::tm>::uninit();
        if unsafe { libc::gmtime_r(&seconds, time.as_mut_ptr()) }.is_null() {
            return String::new();
        }
        let time = unsafe { time.assume_init() };
        format!(
            "{:04}-{:02}-{:02}",
            time.tm_year + 1900,
            time.tm_mon + 1,
            time.tm_mday
        )
    }
    #[cfg(not(unix))]
    {
        String::new()
    }
}

fn patch(e: &Engine, id: &str, stage: &str, value: Value) -> Result<Value, &'static str> {
    e.store
        .lock()
        .map_err(|_| "storeUnavailable")?
        .update_job(id, stage, &value)
        .map_err(|error| error.code())
}
async fn run_job(e: Shared, job: Value) -> Result<(), &'static str> {
    let result = task_pipeline(&e, &job).await;
    if let Err(reason) = result {
        let id = job["id"].as_str().ok_or("jobInvalid")?;
        if let Ok(s) = e.store.lock() {
            if let Ok(jobs) = s.jobs() {
                if let Some(current) = jobs.iter().find(|j| j["id"] == id) {
                    let stage = current["stage"].as_str().unwrap_or("unknown");
                    if stage == "queued"
                        && matches!(
                            reason,
                            "laneCap"
                                | "dependencyPending"
                                | "projectNotRunning"
                                | "chatBusy"
                                | "chatStatusUnknown"
                        )
                    {
                        let _ = s.update_job(id, stage, &json!({"reason":reason}));
                    } else if !matches!(
                        stage,
                        "completed" | "stopped" | "paused" | "interrupted" | "failed"
                    ) {
                        let _ = s.update_job(
                            id,
                            stage,
                            &json!({"stage":"interrupted","reason":reason}),
                        );
                    }
                }
            }
        }
    }
    result
}
async fn resolved_server_model(
    e: &Engine,
    directory: &str,
    requested: &str,
) -> Result<String, &'static str> {
    tokio::time::timeout(
        Duration::from_secs(8),
        e.server.resolve_model(directory, requested),
    )
    .await
    .map_err(|_| "transport_unavailable")?
    .map_err(|error| error.code())
}

async fn selected_job_model(
    e: &Engine,
    job: &Value,
    directory: &str,
    role: &str,
) -> Result<String, &'static str> {
    let id = job["id"].as_str().ok_or("jobInvalid")?;
    // Use the latest durable checkpoint even when the caller holds an older
    // job snapshot. Reconnect must never switch models after dispatch.
    {
        let store = e.store.lock().map_err(|_| "storeUnavailable")?;
        let jobs = store.jobs().map_err(|_| "jobChanged")?;
        let current = jobs
            .iter()
            .find(|job| job["id"] == id)
            .ok_or("jobChanged")?;
        if let Some(model) = current["resolvedModels"][role]
            .as_str()
            .filter(|s| !s.is_empty())
        {
            crate::opencode::validate_model(model).map_err(|error| error.code())?;
            return Ok(model.to_owned());
        }
    }
    let requested = if role == "checker" {
        &job["checkerRole"]["model"]
    } else {
        &job["model"]
    };
    let model =
        resolved_server_model(e, directory, requested.as_str().ok_or("invalid_model")?).await?;
    let store = e.store.lock().map_err(|_| "storeUnavailable")?;
    let jobs = store.jobs().map_err(|_| "jobChanged")?;
    let current = jobs
        .iter()
        .find(|job| job["id"] == id)
        .ok_or("jobChanged")?;
    let stage = current["stage"].as_str().ok_or("jobChanged")?;
    if !matches!(
        stage,
        "queued" | "starting" | "running" | "checking" | "resuming"
    ) {
        return Err("jobChanged");
    }
    store
        .update_job(id, stage, &json!({"resolvedModels":{role:model}}))
        .map_err(|error| error.code())?;
    Ok(model)
}

fn project_worker_source(e: &Engine, job: &Value) -> Result<String, &'static str> {
    let store = e.store.lock().map_err(|_| "storeUnavailable")?;
    let workspace = store.workspace().map_err(|_| "storeUnavailable")?;
    workspace["projects"]
        .as_array()
        .and_then(|projects| projects.iter().find(|p| p["id"] == job["projectId"]))
        .and_then(|project| project["repos"].as_array())
        .and_then(|repos| repos.iter().find(|repo| repo["id"] == job["repoId"]))
        .and_then(|repo| repo["path"].as_str())
        .map(str::to_owned)
        .ok_or("repoInvalid")
}

async fn task_pipeline(e: &Engine, job: &Value) -> Result<(), &'static str> {
    let id = job["id"].as_str().ok_or("jobInvalid")?;
    crate::opencode::validate_model(job["model"].as_str().ok_or("invalid_model")?)
        .map_err(|error| error.code())?;
    if job["instructions"].as_str().is_none_or(str::is_empty) {
        return Err("invalid_role");
    }
    if job["kind"] != "planner" {
        crate::opencode::validate_model(
            job["checkerRole"]["model"]
                .as_str()
                .ok_or("checkerRoleMissing")?,
        )
        .map_err(|error| error.code())?;
    }
    let task = job["taskId"]
        .as_str()
        .filter(|s| !s.is_empty())
        .unwrap_or(id);
    let repo = job["repoId"].as_str().ok_or("repoInvalid")?;
    if job["stage"] == "resuming" {
        return resume_job(e, job).await;
    }
    stage_admitted(e, job).await?;
    let planner = job["kind"] == "planner";
    let selected = if planner { "planner" } else { "worker" };
    let source = project_worker_source(e, job)?;
    let model = selected_job_model(e, job, &source, selected).await?;
    {
        // The durable starting reservation and cap check are one serialized
        // admission. Concurrent queued futures cannot both take the last lane.
        let _reservation = e.lane_admission.lock().await;
        stage_admitted(e, job).await?;
        patch(e, id, "queued", json!({"stage":"starting"}))?;
    }
    if !undispatched_clone_allowed(job) {
        return Err("recoveryNeedsReview");
    }
    let work = e
        .repositories
        .lock()
        .map_err(|_| "repositoryUnavailable")?
        .prepare_fresh_worker(repo, task)
        .map_err(|_| "workerCloneFailed")?;
    let directory = format!("{}/{}/{}", e.config.guest_worker_root, repo, task);
    let dev = work["devCommit"].as_str().ok_or("repoInvalid")?;
    let role = if planner {
        "planner"
    } else {
        job["roleId"].as_str().unwrap_or("worker")
    };
    let instructions = job["instructions"].as_str().unwrap_or("");
    let session = e
        .server
        .create_session(&directory, role)
        .await
        .map_err(|_| "sessionCreateUncertain")?;
    let sessions = if planner {
        json!({"planner":session})
    } else {
        json!({"worker":session})
    };
    if patch(
        e,
        id,
        "starting",
        json!({"stage":"running","directory":directory,"sessionIds":sessions,"freshSessionIds":sessions,"expectedDevCommit":dev}),
    ).is_err() {
        let _ = e.server.abort(&directory, &session).await;
        return Err("jobChanged");
    }
    stage_admitted(e, job).await?;
    let request = if planner {
        planner_request(job)
    } else {
        worker_request(job, work["branch"].as_str().ok_or("repoInvalid")?)
    };
    admitted_prompt(
        e,
        job,
        &directory,
        &session,
        role,
        &model,
        instructions,
        &request,
        if planner { "planner" } else { "worker" },
        planner || job["readOnly"] == true,
    )
    .await?;
    let output = await_completion(
        e,
        id,
        if planner { "planner" } else { "worker" },
        &directory,
        &session,
    )
    .await?;
    if planner {
        let plan = structured_server_output(&output)?;
        validate_plan(&plan, repo)?;
        patch(e, id, "running", json!({"stage":"completed","plan":plan}))?;
        return Ok(());
    }
    check_and_merge(e, job, &session).await
}
async fn collect_against_current_dev(
    e: &Engine,
    repo: &str,
    task: &str,
) -> Result<Value, &'static str> {
    // Collection validates its expected dev. Keep that read and collection
    // behind publication so a parallel integration cannot change the ref in
    // the gap. Release before any checker request or admission wait.
    let _publication = e.merge_publication.lock().await;
    let expected_dev = current_dev(e, repo)?;
    e.repositories
        .lock()
        .map_err(|_| "repositoryUnavailable")?
        .collect_worker(repo, task, &expected_dev)
        .map_err(|error| error.code())
}
async fn check_and_merge(e: &Engine, job: &Value, session: &str) -> Result<(), &'static str> {
    let id = job["id"].as_str().ok_or("jobInvalid")?;
    let task = job["taskId"]
        .as_str()
        .filter(|s| !s.is_empty())
        .unwrap_or(id);
    let repo = job["repoId"].as_str().ok_or("repoInvalid")?;
    let directory = format!("{}/{}/{}", e.config.guest_worker_root, repo, task);
    let collected = collect_against_current_dev(e, repo, task).await?;
    let commit = collected["taskCommit"].as_str().ok_or("repoInvalid")?;
    stage_admitted(e, job).await?;
    let model = selected_job_model(e, job, &directory, "checker").await?;
    let checker = e
        .server
        .create_session(&directory, "checker")
        .await
        .map_err(|_| "sessionCreateUncertain")?;
    if patch(
        e,
        id,
        "running",
        json!({"stage":"checking","sessionIds":{"worker":session,"checker":checker},"freshSessionIds":{"checker":checker},"taskCommit":commit}),
    ).is_err() {
        let _ = e.server.abort(&directory, &checker).await;
        return Err("jobChanged");
    }
    let check = checker_request(job);
    stage_admitted(e, job).await?;
    admitted_prompt(
        e,
        job,
        &directory,
        &checker,
        "checker",
        &model,
        job["checkerRole"]["instructions"]
            .as_str()
            .ok_or("checkerRoleMissing")?,
        &check,
        "checker",
        true,
    )
    .await?;
    let checked = await_completion(e, id, "checker", &directory, &checker).await?;
    let verification = structured_server_output(&checked)?;
    let passed = validate_check(&verification, &job["criteria"])?;
    patch(
        e,
        id,
        "checking",
        json!({"stage":if passed {"mergeReady"} else {"needsFix"},"findings":verification["findings"],"criterionResults":verification["criterionResults"]}),
    )?;
    if !passed {
        return Ok(());
    }
    // The checker must not have changed the worker head; integration consumes
    // the exact commit captured before checking and refuses stale dev.
    let current = collect_against_current_dev(e, repo, task).await?;
    if current["taskCommit"] != commit {
        return Err("checkedCommitChanged");
    }
    stage_admitted(e, job).await?;
    patch(e, id, "mergeReady", json!({"stage":"merging"}))?;
    merge_and_publish(e, job, commit).await
}
fn undispatched_clone_allowed(job: &Value) -> bool {
    job["stage"] == "queued"
        && job["sessionIds"]
            .as_object()
            .is_some_and(|ids| ids.is_empty())
        && (job["promptDispatch"].is_null()
            || job["promptDispatch"]
                .as_object()
                .is_some_and(|ids| ids.is_empty()))
        && job["reason"] != "sessionCreateUncertain"
}
fn current_dev(e: &Engine, repo: &str) -> Result<String, &'static str> {
    if !boundary_verified(e) {
        return Err("boundaryUnavailable");
    }
    let refs = e
        .repositories
        .lock()
        .map_err(|_| "repositoryUnavailable")?
        .refs(repo)
        .map_err(|_| "repoInvalid")?;
    refs["devCommit"]
        .as_str()
        .map(str::to_owned)
        .ok_or("repoInvalid")
}
fn merge_evidence(e: &Engine, id: &str) -> Result<(Value, String), &'static str> {
    use sha2::{Digest, Sha256};
    let job = e
        .store
        .lock()
        .map_err(|_| "storeUnavailable")?
        .jobs()
        .map_err(|_| "storeUnavailable")?
        .into_iter()
        .find(|j| j["id"] == id)
        .ok_or("jobMissing")?;
    let proof = json!({"checkerSessionId":job["sessionIds"]["checker"],
        "criteria":job["criteria"],"findings":job["findings"],"criterionResults":job["criterionResults"]});
    if !proof["checkerSessionId"].is_string() || !validate_check(&proof, &job["criteria"])? {
        return Err("recoveryNeedsReview");
    }
    let fingerprint = format!(
        "{:x}",
        Sha256::digest(serde_json::to_vec(&proof).map_err(|_| "checkInvalid")?)
    );
    Ok((job, fingerprint))
}
async fn merge_and_publish(
    e: &Engine,
    job: &Value,
    expected_task: &str,
) -> Result<(), &'static str> {
    let id = job["id"].as_str().ok_or("jobInvalid")?;
    let repo = job["repoId"].as_str().ok_or("repoInvalid")?;
    let task = job["taskId"].as_str().ok_or("jobInvalid")?;
    let _publication = e.merge_publication.lock().await;
    let (_, fingerprint) = merge_evidence(e, id)?;
    let expected_dev = current_dev(e, repo)?;
    let receipt = e
        .repositories
        .lock()
        .map_err(|_| "repositoryUnavailable")?
        .merge_dev_for_evidence(repo, task, &expected_dev, expected_task, id, &fingerprint)
        .map_err(|error| error.code())?;
    patch(
        e,
        id,
        "merging",
        json!({"stage":"completed","mergedCommit":receipt["devCommit"],"repoReceipt":receipt}),
    )?;
    Ok(())
}
async fn recover_merge_publication(e: &Engine, job: &Value) -> Result<bool, &'static str> {
    let id = job["id"].as_str().ok_or("jobInvalid")?;
    if job["resumeStage"] == "merging" {
        let _publication = e.merge_publication.lock().await;
        let (current, fingerprint) = merge_evidence(e, id)?;
        let repo = current["repoId"].as_str().ok_or("repoInvalid")?;
        let task = current["taskId"].as_str().ok_or("jobInvalid")?;
        let commit = current["taskCommit"]
            .as_str()
            .ok_or("recoveryNeedsReview")?;
        if !boundary_verified(e) {
            return Err("boundaryUnavailable");
        }
        let receipt = e
            .repositories
            .lock()
            .map_err(|_| "repositoryUnavailable")?
            .recover_merge_dev(repo, task, commit, id, &fingerprint)
            .map_err(|error| error.code())?;
        if let Some(receipt) = receipt {
            patch(e, id, "resuming", json!({"stage":"merging"}))?;
            patch(
                e,
                id,
                "merging",
                json!({"stage":"completed","mergedCommit":receipt["devCommit"],"repoReceipt":receipt}),
            )?;
            return Ok(true);
        }
    }
    Ok(false)
}
async fn resume_job(e: &Engine, job: &Value) -> Result<(), &'static str> {
    // Only refetch an already recorded session. A lost prompt acknowledgment
    // cannot lead to a replacement prompt, session or duplicate provider cost.
    let id = job["id"].as_str().ok_or("jobInvalid")?;
    if recover_merge_publication(e, job).await? {
        return Ok(());
    }
    let directory = job["directory"].as_str().ok_or("recoveryNeedsReview")?;
    let role = if job["sessionIds"]["checker"].is_string() {
        "checker"
    } else if job["kind"] == "planner" {
        "planner"
    } else {
        "worker"
    };
    let session = job["sessionIds"][role]
        .as_str()
        .ok_or("recoveryNeedsReview")?;
    let current = current_job(e, id)?;
    if restart_resend_due(&current, role) {
        resend_after_restart(e, &current, role, directory, session).await?;
    }
    let observed = await_completion(e, id, role, directory, session).await?;
    if !boundary_verified(e) {
        return Err("boundaryUnavailable");
    }
    if role == "planner" {
        let plan = structured_server_output(&observed)?;
        validate_plan(&plan, job["repoId"].as_str().ok_or("repoInvalid")?)?;
        patch(e, id, "resuming", json!({"stage":"completed","plan":plan}))?;
        return Ok(());
    }
    // Explicit resume observes the original worker, then creates a fresh
    // checker. It never resends the original worker prompt.
    if role == "worker" {
        patch(e, id, "resuming", json!({"stage":"running"}))?;
        return check_and_merge(e, job, session).await;
    }
    let checked = structured_server_output(&observed)?;
    if !validate_check(&checked, &job["criteria"])? {
        patch(e, id, "resuming", json!({"stage":"checking"}))?;
        patch(
            e,
            id,
            "checking",
            json!({"stage":"needsFix","findings":checked["findings"],"criterionResults":checked["criterionResults"]}),
        )?;
        return Ok(());
    }
    let repo = job["repoId"].as_str().ok_or("repoInvalid")?;
    let task = job["taskId"].as_str().ok_or("jobInvalid")?;
    let commit = job["taskCommit"].as_str().ok_or("recoveryNeedsReview")?;
    let current = collect_against_current_dev(e, repo, task).await?;
    if current["taskCommit"] != commit {
        return Err("checkedCommitChanged");
    }
    patch(
        e,
        id,
        "resuming",
        json!({"stage":"mergeReady","findings":checked["findings"],"criterionResults":checked["criterionResults"]}),
    )?;
    stage_admitted(e, job).await?;
    patch(e, id, "mergeReady", json!({"stage":"merging"}))?;
    merge_and_publish(e, job, commit).await
}
fn current_job(e: &Engine, id: &str) -> Result<Value, &'static str> {
    e.store
        .lock()
        .map_err(|_| "storeUnavailable")?
        .jobs()
        .map_err(|_| "jobChanged")?
        .into_iter()
        .find(|job| job["id"] == id)
        .ok_or("jobChanged")
}
/// A restart round was opened for [role] (by Resume, or by the engine itself
/// within its limit) and nothing of it was sent yet. A round whose send is
/// "dispatching" or "dispatched" is only observed, never sent twice.
fn restart_resend_due(job: &Value, role: &str) -> bool {
    job["restartResend"] == role && job["restartDispatch"][role].is_null()
}
const RESTART_NOTE: &str = "OpenCode restarted while you were on this step, so \
     your last turn was cut off before it finished. First look at what is already \
     done (files, commits, your earlier messages here) and continue from there. \
     Do not redo or duplicate work that is already finished. The original request \
     follows.";
/// Sends the step's own request again into the same session, once, after the
/// turn died under a restarted OpenCode. The request is rebuilt from the job's
/// approved inputs (never from model output), on the restart dispatch ledger.
async fn resend_after_restart(
    e: &Engine,
    job: &Value,
    role: &str,
    directory: &str,
    session: &str,
) -> Result<(), &'static str> {
    let model = selected_job_model(e, job, directory, role).await?;
    let task = job["taskId"]
        .as_str()
        .filter(|s| !s.is_empty())
        .or(job["id"].as_str())
        .ok_or("jobInvalid")?;
    let (agent, instructions, request, read_only) = match role {
        "planner" => {
            // The planner saw the job as it was queued; never runtime state.
            let mut input = job.clone();
            if let Some(fields) = input.as_object_mut() {
                for key in [
                    "reason",
                    "resumeStage",
                    "freshSessionIds",
                    "promptDispatch",
                    "restartDispatch",
                    "dispatchServerPid",
                    "restartResend",
                    "restartResends",
                    "sessionDispatchDays",
                    "resolvedModels",
                    "usageRevision",
                ] {
                    fields.remove(key);
                }
            }
            input["stage"] = json!("queued");
            input["directory"] = Value::Null;
            input["sessionIds"] = json!({});
            input["sessionUsage"] = json!({});
            (
                "planner",
                job["instructions"].as_str().unwrap_or(""),
                planner_request(&input),
                true,
            )
        }
        "worker" => (
            job["roleId"].as_str().unwrap_or("worker"),
            job["instructions"].as_str().unwrap_or(""),
            worker_request(job, &crate::repository::task_branch(task)),
            job["readOnly"] == true,
        ),
        _ => (
            "checker",
            job["checkerRole"]["instructions"]
                .as_str()
                .ok_or("checkerRoleMissing")?,
            checker_request(job),
            true,
        ),
    };
    admitted_prompt_on(
        e,
        "restartDispatch",
        job,
        directory,
        session,
        agent,
        &model,
        instructions,
        &format!("{RESTART_NOTE}\n\n{request}"),
        role,
        read_only,
    )
    .await
}
/// `serverRestarted` when the turn for [role] was accepted by one OpenCode
/// process and is now dead under another the client proved to be the same
/// server (an app restart or a crash relaunch); otherwise [fallback].
async fn restart_or(e: &Engine, job_id: &str, role: &str, fallback: &'static str) -> &'static str {
    let dispatched = current_job(e, job_id)
        .ok()
        .and_then(|job| job["dispatchServerPid"][role].as_u64());
    match (dispatched, e.server.server_pid().await) {
        (Some(before), Some(now)) if before != now => "serverRestarted",
        _ => fallback,
    }
}
async fn await_completion(
    e: &Engine,
    job_id: &str,
    role: &str,
    directory: &str,
    id: &str,
) -> Result<Value, &'static str> {
    let accepted_at = tokio::time::Instant::now();
    let mut seen_running = false;
    let mut errors = 0;
    for _ in 0..1800 {
        // One hour; expiration leaves a durable uncertain checkpoint.
        active_stage(e, job_id)?;
        let observed = match e.server.observe(directory, id).await {
            Ok(value) => {
                errors = 0;
                value
            }
            // A restart from Settings or a crash relaunch takes seconds; wait
            // it out so the turn's fate is read from the server that came back.
            Err(_) if errors < 15 => {
                errors += 1;
                tokio::time::sleep(Duration::from_secs(2)).await;
                continue;
            }
            Err(_) => return Err("sessionUnknown"),
        };
        {
            let store = e.store.lock().map_err(|_| "storeUnavailable")?;
            let jobs = store.jobs().map_err(|_| "jobChanged")?;
            let current = jobs
                .iter()
                .find(|job| job["id"] == job_id)
                .ok_or("jobChanged")?;
            let stage = current["stage"].as_str().ok_or("jobChanged")?;
            if matches!(stage, "paused" | "stopped" | "interrupted") {
                return Err("jobChanged");
            }
            let usage = observed_session_usage(current, role, &observed["usage"], &utc_day());
            if current["sessionUsage"][role] != usage {
                store
                    .update_job(job_id, stage, &json!({"sessionUsage":{role:usage}}))
                    .map_err(|_| "usageUncertain")?;
            }
        }
        match observed["state"].as_str() {
            Some("completed") => return Ok(observed),
            Some("running") => {
                seen_running = true;
                tokio::time::sleep(Duration::from_secs(2)).await
            }
            Some("blocked") => return Err("needsAnswer"),
            Some("failed") => {
                let reason = observed_failure_reason(&observed);
                if reason != "sessionFailed" {
                    return Err(reason);
                }
                return Err(restart_or(e, job_id, role, reason).await);
            }
            Some("unknown") if !seen_running && accepted_at.elapsed() < Duration::from_secs(30) => {
                // A 204 may precede both status and turn persistence. Missing
                // evidence stays pending briefly, never completed or resent.
                tokio::time::sleep(Duration::from_secs(2)).await;
            }
            Some("unknown") => return Err(restart_or(e, job_id, role, "sessionUnknown").await),
            _ => return Err("sessionUnknown"),
        }
    }
    Err("sessionUncertain")
}

fn observed_failure_reason(observed: &Value) -> &'static str {
    // Only the OC2 driver currently supplies this allowlisted recovery hint.
    // Never forward arbitrary server errors into durable jobs or UI details.
    if observed["state"] == "failed" && observed["reason"] == "modelNotConfigured" {
        "modelNotConfigured"
    } else {
        "sessionFailed"
    }
}
fn observed_session_usage(job: &Value, role: &str, observed: &Value, day: &str) -> Value {
    let mut out = normalized_usage(observed);
    let previous = &job["sessionUsage"][role];
    let cost = out["cost"].as_f64();
    // A first idle/busy poll may have no cost yet. The immutable fresh-session
    // and first-dispatch date still prove every eventual cumulative dollar was
    // incurred today; missing initial usage must not poison that proof forever.
    let same_day_origin = job["sessionDispatchDays"][role] == day
        && job["freshSessionIds"][role] == job["sessionIds"][role]
        && job["freshSessionIds"][role].is_string();
    let daily = if same_day_origin {
        cost.filter(|next| previous["cost"].as_f64().is_none_or(|old| *next >= old))
    } else if previous["day"] == day {
        previous["dailyCost"]
            .as_f64()
            .zip(previous["cost"].as_f64())
            .zip(cost)
            .and_then(|((daily, old), next)| (next >= old).then_some(daily + next - old))
    } else {
        None
    };
    out["day"] = json!(day);
    out["dailyCost"] = json!(daily.filter(|n| n.is_finite() && *n >= 0.0));
    out
}
fn normalized_usage(usage: &Value) -> Value {
    // Preserve the protocol's explicit cumulative total; no token arithmetic.
    json!({"cost":usage["cost"].as_f64().filter(|n| n.is_finite() && *n >= 0.0),
           "tokens":usage["tokens"]["total"].as_u64()})
}
// Model-authored JSON must not silently overwrite an earlier finding/status.
fn strict_json(text: &str) -> Result<Value, &'static str> {
    struct StrictValue(Value);
    impl<'de> serde::Deserialize<'de> for StrictValue {
        fn deserialize<D: serde::Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
            struct Visitor;
            impl<'de> serde::de::Visitor<'de> for Visitor {
                type Value = StrictValue;
                fn expecting(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
                    f.write_str("JSON without duplicate object keys")
                }
                fn visit_bool<E: serde::de::Error>(self, v: bool) -> Result<Self::Value, E> {
                    Ok(StrictValue(json!(v)))
                }
                fn visit_i64<E: serde::de::Error>(self, v: i64) -> Result<Self::Value, E> {
                    Ok(StrictValue(json!(v)))
                }
                fn visit_u64<E: serde::de::Error>(self, v: u64) -> Result<Self::Value, E> {
                    Ok(StrictValue(json!(v)))
                }
                fn visit_f64<E: serde::de::Error>(self, v: f64) -> Result<Self::Value, E> {
                    serde_json::Number::from_f64(v)
                        .map(|n| StrictValue(Value::Number(n)))
                        .ok_or_else(|| E::custom("invalid number"))
                }
                fn visit_str<E: serde::de::Error>(self, v: &str) -> Result<Self::Value, E> {
                    Ok(StrictValue(json!(v)))
                }
                fn visit_unit<E: serde::de::Error>(self) -> Result<Self::Value, E> {
                    Ok(StrictValue(Value::Null))
                }
                fn visit_seq<A: serde::de::SeqAccess<'de>>(
                    self,
                    mut a: A,
                ) -> Result<Self::Value, A::Error> {
                    let mut values = Vec::new();
                    while let Some(value) = a.next_element::<StrictValue>()? {
                        values.push(value.0);
                    }
                    Ok(StrictValue(Value::Array(values)))
                }
                fn visit_map<A: serde::de::MapAccess<'de>>(
                    self,
                    mut a: A,
                ) -> Result<Self::Value, A::Error> {
                    let mut map = serde_json::Map::new();
                    while let Some(key) = a.next_key::<String>()? {
                        if map.contains_key(&key) {
                            return Err(serde::de::Error::custom("duplicate object key"));
                        }
                        map.insert(key, a.next_value::<StrictValue>()?.0);
                    }
                    Ok(StrictValue(Value::Object(map)))
                }
            }
            d.deserialize_any(Visitor)
        }
    }
    let mut parser = serde_json::Deserializer::from_str(text);
    let value = <StrictValue as serde::Deserialize>::deserialize(&mut parser)
        .map_err(|_| "structuredOutputInvalid")?;
    parser.end().map_err(|_| "structuredOutputInvalid")?;
    Ok(value.0)
}
fn structured_server_output(value: &Value) -> Result<Value, &'static str> {
    match structured_output(value) {
        Ok(parsed) => return Ok(parsed),
        Err(_) => {}
    }
    // OC2 can successfully finish with an inspection summary followed by an
    // unfenced verdict. Accept only one trailing object at a fresh line; never
    // search through candidate objects or choose a verdict from competing text.
    // The caller still requires settled completion and validates the exact
    // checker criteria (or planner schema) before granting any authority.
    let raw = value["text"].as_str().ok_or("structuredOutputInvalid")?;
    if raw.len() > 1024 * 1024 || raw.contains("```") {
        return Err("structuredOutputInvalid");
    }
    let text = raw.trim();
    let first_structure = text
        .find(['{', '}', '[', ']'])
        .ok_or("structuredOutputInvalid")?;
    let (prefix, body) = text.split_at(first_structure);
    if !body.starts_with('{')
        || prefix.trim().is_empty()
        || !prefix.trim_end_matches([' ', '\t', '\r']).ends_with('\n')
    {
        return Err("structuredOutputInvalid");
    }
    // Parsing the entire suffix preserves duplicate-key rejection, handles
    // escaped braces in strings, and rejects trailing prose or another object.
    strict_json(body)
}
pub fn structured_output(value: &Value) -> Result<Value, &'static str> {
    let text = value["text"].as_str().ok_or("structuredOutputInvalid")?;
    if text.len() > 1024 * 1024 {
        return Err("structuredOutputInvalid");
    }
    let text = text.trim();
    if let Ok(value) = strict_json(text) {
        return Ok(value);
    }
    // A model may append explanatory prose to its explicitly fenced verdict.
    // Accept exactly one JSON fence, never scan prose for an object or choose
    // between multiple candidate verdicts. Schema/criteria validation follows.
    let (before, fenced) = text.split_once("```").ok_or("structuredOutputInvalid")?;
    let fenced = fenced
        .strip_prefix("json\n")
        .or_else(|| fenced.strip_prefix('\n'))
        .ok_or("structuredOutputInvalid")?;
    let (body, after) = fenced.split_once("```").ok_or("structuredOutputInvalid")?;
    if after.contains("```")
        || [before, after]
            .iter()
            .any(|prose| prose.contains(['{', '}', '[', ']']))
    {
        return Err("structuredOutputInvalid");
    }
    strict_json(body.trim())
}
/// Require durable evidence that a read-only checker can inspect without
/// executing commands or inferring that a proposed test actually ran.
fn verification_report_path(job: &Value) -> String {
    job["taskId"]
        .as_str()
        .filter(|id| crate::config::valid_id(id))
        .map(|id| format!(".aiteam-verification/{id}.md"))
        .unwrap_or_else(|| ".aiteam-verification.md".into())
}
pub fn worker_request(job: &Value, branch: &str) -> String {
    let report = verification_report_path(job);
    format!(
        "Implement this task on branch {branch}. Keep edits in this isolated clone. \
         Commit completed changes to that task branch, without editing main or dev. \
         Run the acceptance checks that are feasible and authorized for this task. \
         Record the actual executed acceptance checks in {report} and \
         commit that task-verification report with your changes. For each original \
         criterion, record the exact commands, working directory, exit codes, and \
         observed results/output, or the concrete committed files inspected for a \
         static criterion. Distinguish passed, failed, skipped, and not-run checks; \
         explain any unavailable check. Never fabricate evidence, command execution, \
         results, or a passing check. A proposed command is not an executed check. \
         Do not include credentials or secrets in the report. The checker is read-only \
         and cannot execute your checks; it needs this committed evidence. \
         Acceptance criteria: {}. Task: {}",
        job["criteria"], job["title"]
    )
}

/// Keep criterion verdicts distinct from unresolved defects. Successful
/// evidence must not become a finding: every finding intentionally blocks merge.
pub fn checker_request(job: &Value) -> String {
    let report = verification_report_path(job);
    let passed_example = json!({
        "findings": [],
        "criterionResults": [{"criterion": "<exact original criterion>", "status": "met"}]
    });
    let defect_example = json!({
        "findings": [{"id": "finding-1", "severity": "major",
            "criterion": "<exact original criterion>",
            "location": report,
            "text": "Required executed-check evidence is missing.", "status": "open"}],
        "criterionResults": [{"criterion": "<exact original criterion>", "status": "unmet"}]
    });
    format!(
        "Read-only verification. Inspect committed task changes and the committed \
         {report} task-verification report against every original \
         acceptance criterion: {}. For a task dispatched by an older engine, an \
         existing legacy .aiteam-verification.md report may be inspected only if it \
         contains actual evidence for this exact task and its original criteria. \
         Never substitute another task report. Use only read, glob, and grep tools. Do not modify \
         files or run commands, tests, shell tools, or subagents. For a criterion \
         requiring executed checks, establish what actually ran from the committed \
         evidence, including the exact command, exit code, and observed result. \
         Do not claim unexecuted checks passed or fabricate evidence. Missing, skipped, \
         failed, or inconclusive required executed-check evidence means status unmet. \
         For static criteria, inspect the committed files themselves. \
         Return only one JSON object with findings and criterionResults arrays. \
         criterionResults must contain exactly one entry for each original criterion, \
         copying its criterion string exactly without rewriting, combining, or omitting it. \
         Only criterionResults.status may be met, unmet, or notApplicable. \
         findings contains actual unresolved defects only: each finding has id, severity \
         (critical/major/minor), criterion (the exact affected original string), location, \
         text, and status open. Do not include successful observations or successful \
         acceptance evidence in findings. When all criteria pass and no defects remain, \
         return findings as an empty array. Do not waive findings, declare them fixed, \
         or use notApplicable to claim acceptance; any finding or any result other than \
         met blocks merge. These are illustrative JSON examples, not your verdict; \
         replace the placeholder with original criteria and assess each one: \
         all-pass shape: {passed_example}; unresolved-defect shape: {defect_example}.",
        job["criteria"]
    )
}

pub fn planner_request(job: &Value) -> String {
    let schema = json!({"type":"object","required":["spec","phases","tasks"],"properties":{
        "spec":{"type":"object"},
        "phases":{"type":"array","minItems":1,"items":{"type":"object","required":["id","title","milestoneId"]}},
        "tasks":{"type":"array","minItems":1,"items":{"type":"object","required":["id","title","phaseId","roleId","repoId","serverId","criteria","dependsOn"],"properties":{
            "roleId":{"enum":job["planningRoles"].as_array().map(|roles| roles.iter().map(|r| r["id"].clone()).collect::<Vec<_>>()).unwrap_or_default()},
            "title":{"type":"string","minLength":1},"serverId":{"const":"phone"},"repoId":{"const":job["repoId"]},
            "criteria":{"type":"array","minItems":1,"items":{"type":"string","minLength":1}},"dependsOn":{"type":"array","items":{"type":"string"}}
        }}}
    }});
    format!("Plan the approved specification. Return only a JSON object conforming to this schema: {schema}. Copy spec from the approved input. Every task must have a nonempty title, reference an existing phase and configured worker role, and use the input repoId and phone server. Supply authored proposals only; omit runtime status, findings and usage. Do not modify files. Input: {job}")
}
pub fn validate_plan(value: &Value, repo: &str) -> Result<(), &'static str> {
    let tasks = value["tasks"].as_array().ok_or("planInvalid")?;
    if !value["spec"].is_object()
        || !value["phases"].is_array()
        || tasks.is_empty()
        || tasks.len() > 1000
    {
        return Err("planInvalid");
    }
    let mut ids = std::collections::HashSet::new();
    for t in tasks {
        if t["title"].as_str().is_none_or(|s| s.trim().is_empty()) {
            return Err("planTaskTitleRequired");
        }
        let id = t["id"].as_str().ok_or("planInvalid")?;
        if !crate::config::valid_id(id)
            || !ids.insert(id)
            || t["repoId"] != repo
            || !t["roleId"].is_string()
            || t["criteria"]
                .as_array()
                .is_none_or(|a| a.is_empty() || a.iter().any(|v| !v.is_string()))
        {
            return Err("planInvalid");
        }
    }
    for t in tasks {
        for dep in t["dependsOn"].as_array().ok_or("planInvalid")? {
            let dep = dep.as_str().ok_or("planInvalid")?;
            if !ids.contains(dep) || t["id"] == dep {
                return Err("planInvalid");
            }
        }
    }
    // Cyclic dependency plans are rejected; no infinite waiting queue.
    let mut done = std::collections::HashSet::new();
    for _ in 0..tasks.len() {
        for t in tasks {
            if t["dependsOn"]
                .as_array()
                .unwrap()
                .iter()
                .all(|d| done.contains(d.as_str().unwrap()))
            {
                done.insert(t["id"].as_str().unwrap());
            }
        }
    }
    if done.len() != tasks.len() {
        return Err("planInvalid");
    }
    Ok(())
}
pub fn validate_check(value: &Value, criteria: &Value) -> Result<bool, &'static str> {
    let expected = criteria.as_array().ok_or("checkInvalid")?;
    let results = value["criterionResults"].as_array().ok_or("checkInvalid")?;
    let findings = value["findings"].as_array().ok_or("checkInvalid")?;
    if expected.is_empty() || results.len() != expected.len() {
        return Err("checkInvalid");
    }
    let mut met = true;
    let mut seen = std::collections::HashSet::new();
    for result in results {
        let criterion = result["criterion"].as_str().ok_or("checkInvalid")?;
        if !seen.insert(criterion) || !expected.iter().any(|c| c == criterion) {
            return Err("checkInvalid");
        }
        match result["status"].as_str() {
            Some("met") => {}
            Some("unmet" | "notApplicable") => met = false,
            _ => return Err("checkInvalid"),
        }
    }
    for f in findings {
        if !matches!(f["severity"].as_str(), Some("critical" | "major" | "minor"))
            || !f["text"].is_string()
            || !f["criterion"].is_string()
        {
            return Err("checkInvalid");
        }
        // A checker cannot grant its own waiver or declare a finding fixed.
        // Any finding blocks this first slice; the owner resolves it separately.
        met = false;
    }
    Ok(met)
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::Body;
    use tower::ServiceExt;

    fn unapproved_model_project(e: &Engine) -> Value {
        let store = e.store.lock().unwrap();
        let created = store.execute(&json!({"requestId":"create-model","action":"createProject","name":"Fresh model",
            "settings":{"mode":"single","maxLanes":1,"reviewLevel":"milestones","maxFixRounds":0,"chargingOnly":false,"budget":{"chosen":true,"unlimited":true}},
            "spec":{"goal":"Plan without role overrides","milestones":[{"id":"m","title":"Greeting","criteria":["One greeting"]}]},
            "repos":[{"id":"repo","serverId":"phone","path":"/root/projects/fresh","devCommit":"seed","mainCommit":"seed"}]})).unwrap();
        assert_eq!(created["accepted"], true);
        json!({"requestId":"approve-model","action":"approveSpec","projectId":created["projectId"],"expectedRevision":0})
    }

    async fn model_server(
        connected: bool,
    ) -> (
        String,
        Arc<std::sync::atomic::AtomicUsize>,
        tokio::task::JoinHandle<()>,
    ) {
        use std::sync::atomic::{AtomicUsize, Ordering};
        let writes = Arc::new(AtomicUsize::new(0));
        let observed = writes.clone();
        let app = Router::new().fallback(move |request: Request| {
            let observed = observed.clone();
            async move {
                if request.method() != axum::http::Method::GET { observed.fetch_add(1, Ordering::SeqCst); }
                let body = match request.uri().path() {
                    "/api/info" => json!({"version":"2.0.29","pid":1,"urls":[],"paths":{"tmp":"/tmp"}}),
                    "/openapi.json" => serde_json::from_str(include_str!("../tests/fixtures/oc2-2.0.29-driver-openapi.json")).unwrap(),
                    "/api/model/default" => {
                        assert_eq!(request.uri().query(), Some("location%5Bdirectory%5D=%2Froot%2Fprojects%2Ffresh"));
                        json!({"location":{"directory":"/root/projects/fresh"},"data": if connected { json!({"providerID":"zai-coding-plan","id":"glm-5.3","enabled":true}) } else { Value::Null }})
                    },
                    _ => panic!("model resolution must only read info/schema/default model"),
                };
                Json(body).into_response()
            }
        });
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let server = tokio::spawn(async move {
            axum::serve(listener, app).await.unwrap();
        });
        (url, writes, server)
    }

    #[tokio::test]
    async fn model_preflight_refuses_before_queuing_and_accepts_after_provider_setup() {
        use std::sync::atomic::Ordering;
        let root = tempfile::tempdir().unwrap();
        let mut e = fixture(root.path());
        let input = unapproved_model_project(&e);
        let before = e.store.lock().unwrap().workspace().unwrap();
        let (url, writes, server) = model_server(false).await;
        Arc::get_mut(&mut e).unwrap().server =
            OpenCodeClient::new(&url, "opencode", "fixture-only").unwrap();
        let error = command(State(e.clone()), Ok(Json(input.clone())))
            .await
            .err()
            .unwrap();
        assert_eq!(error.1, "modelNotConfigured");
        assert_eq!(e.store.lock().unwrap().workspace().unwrap(), before);
        assert!(e.store.lock().unwrap().jobs().unwrap().is_empty());
        assert_eq!(writes.load(Ordering::SeqCst), 0);
        server.abort();
        let (url, writes, server) = model_server(true).await;
        Arc::get_mut(&mut e).unwrap().server =
            OpenCodeClient::new(&url, "opencode", "fixture-only").unwrap();
        assert_eq!(
            command(State(e.clone()), Ok(Json(input.clone())))
                .await
                .unwrap()
                .0["accepted"],
            true
        );
        let job = e.store.lock().unwrap().jobs().unwrap()[0].clone();
        assert_eq!(job["model"], "");
        assert_eq!(
            selected_job_model(&e, &job, "/root/projects/fresh", "planner")
                .await
                .unwrap(),
            "zai-coding-plan/glm-5.3"
        );
        let bound = e.store.lock().unwrap().jobs().unwrap()[0].clone();
        assert_eq!(
            bound["resolvedModels"]["planner"],
            "zai-coding-plan/glm-5.3"
        );
        assert_eq!(bound["model"], "");
        assert_eq!(bound["sessionIds"], json!({}));
        assert_eq!(writes.load(Ordering::SeqCst), 0);
        server.abort();
        // Both stale in-memory snapshots and accepted command replays retain
        // their durable choice even when the server no longer answers.
        assert_eq!(
            selected_job_model(&e, &job, "/root/projects/fresh", "planner")
                .await
                .unwrap(),
            "zai-coding-plan/glm-5.3"
        );
        assert_eq!(
            command(State(e.clone()), Ok(Json(input))).await.unwrap().0["replayed"],
            true
        );
    }

    #[tokio::test]
    async fn model_resolution_cannot_bind_a_stopped_job() {
        let root = tempfile::tempdir().unwrap();
        let mut e = fixture(root.path());
        let input = unapproved_model_project(&e);
        e.store.lock().unwrap().execute(&input).unwrap();
        let job = e.store.lock().unwrap().jobs().unwrap()[0].clone();
        let id = job["id"].as_str().unwrap();
        e.store
            .lock()
            .unwrap()
            .update_job(id, "queued", &json!({"stage":"stopped"}))
            .unwrap();
        let (url, _, server) = model_server(true).await;
        Arc::get_mut(&mut e).unwrap().server =
            OpenCodeClient::new(&url, "opencode", "fixture-only").unwrap();
        assert_eq!(
            selected_job_model(&e, &job, "/root/projects/fresh", "planner").await,
            Err("jobChanged")
        );
        assert!(e.store.lock().unwrap().jobs().unwrap()[0]["resolvedModels"].is_null());
        server.abort();
    }

    #[tokio::test]
    async fn omitted_model_never_reaches_prompt_dispatch() {
        let root = tempfile::tempdir().unwrap();
        let e = fixture(root.path());
        let (_, id) = running_planner(&e);
        let job = e.store.lock().unwrap().jobs().unwrap()[0].clone();
        assert_eq!(
            admitted_prompt(
                &e,
                &job,
                "/root/projects/fresh",
                "ses_test",
                "planner",
                "",
                "Plan",
                "Goal",
                "planner",
                true
            )
            .await,
            Err("modelNotConfigured")
        );
        assert_eq!(
            e.store
                .lock()
                .unwrap()
                .jobs()
                .unwrap()
                .iter()
                .find(|j| j["id"] == id)
                .unwrap(),
            &job
        );
    }

    fn running_planner(e: &Engine) -> (String, String) {
        let store = e.store.lock().unwrap();
        let created = store.execute(&json!({"requestId":"create-poll","action":"createProject","name":"Poll proof",
            "settings":{"mode":"single","maxLanes":1,"reviewLevel":"milestones","maxFixRounds":2,"chargingOnly":false,"budget":{"chosen":true,"unlimited":true}},
            "spec":{"goal":"Poll safely","milestones":[{"id":"m","title":"Safe","criteria":["Observe exact turn"]}]},
            "repos":[{"id":"repo","serverId":"phone","path":"/root/projects/poll","devCommit":"seed","mainCommit":"seed"}]})).unwrap();
        assert_eq!(created["accepted"], true);
        let project = created["projectId"].as_str().unwrap().to_owned();
        let revision = store.workspace().unwrap()["projects"][0]["revision"]
            .as_u64()
            .unwrap();
        assert_eq!(store.execute(&json!({"requestId":"approve-poll","action":"approveSpec","projectId":project,"expectedRevision":revision})).unwrap()["accepted"],true);
        let job = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
        store
            .update_job(&job, "queued", &json!({"stage":"starting"}))
            .unwrap();
        store.update_job(&job,"starting",&json!({"stage":"running","directory":"/root/projects/poll","sessionIds":{"planner":"ses_test"},"promptDispatch":{"planner":"dispatched"}})).unwrap();
        (project, job)
    }

    async fn poll_server(
        complete: bool,
        fail_first: bool,
    ) -> (
        String,
        Arc<std::sync::atomic::AtomicUsize>,
        Arc<std::sync::atomic::AtomicUsize>,
        tokio::task::JoinHandle<()>,
    ) {
        use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
        let polls = Arc::new(AtomicUsize::new(0));
        let posts = Arc::new(AtomicUsize::new(0));
        let failure = Arc::new(AtomicBool::new(fail_first));
        let counts = polls.clone();
        let writes = posts.clone();
        let app = Router::new().fallback(move |request: Request| {
            let counts = counts.clone(); let writes = writes.clone(); let failure = failure.clone();
            async move {
                if request.method() != axum::http::Method::GET { writes.fetch_add(1, Ordering::SeqCst); }
                if failure.swap(false, Ordering::SeqCst) { return (StatusCode::SERVICE_UNAVAILABLE, Json(json!({}))).into_response(); }
                let body = match request.uri().path() {
                    "/api/info" => json!({"version":"2.0.29","pid":1,"urls":[],"paths":{"tmp":"/tmp"}}),
                    "/openapi.json" => serde_json::from_str(include_str!("../tests/fixtures/oc2-2.0.29-driver-openapi.json")).unwrap(),
                    "/api/session/ses_test" => json!({"data":{"id":"ses_test","location":{"directory":"/root/projects/poll"},"outcome":"succeeded","time":{"idle":3}}}),
                    "/api/session/active" => json!({"data":{}}),
                    "/api/session/ses_test/message" => {
                        let index = counts.fetch_add(1, Ordering::SeqCst);
                        json!({"data":if complete && index > 0 { json!([
                            {"id":"msg_1","sessionID":"ses_test","type":"user"},
                            {"id":"msg_2","sessionID":"ses_test","type":"assistant","finish":"stop","time":{"completed":2},"cost":0.2,"content":[{"type":"text","text":"completed proof"}]},
                            {"id":"msg_3","sessionID":"ses_test","type":"idle","outcome":"succeeded","time":{"created":3}}
                        ]) } else { json!([]) },"cursor":{}})
                    },
                    "/api/session/ses_test/permission" | "/api/session/ses_test/inbox" => json!({"data":[]}),
                    "/api/form" => json!({"location":{"directory":"/root/projects/poll"},"data":[]}),
                    _ => json!({}),
                };
                Json(body).into_response()
            }
        });
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = format!("http://{}", listener.local_addr().unwrap());
        let server = tokio::spawn(async move {
            axum::serve(listener, app).await.unwrap();
        });
        (address, polls, posts, server)
    }

    #[test]
    fn failed_observation_accepts_only_the_fixed_model_recovery_code() {
        assert_eq!(
            observed_failure_reason(&json!({"state":"failed","reason":"modelNotConfigured"})),
            "modelNotConfigured"
        );
        // Missing reasons, unknown strings, raw provider errors, and nonfailed
        // hints cannot escape.
        for observation in [
            json!({"state":"failed"}),
            json!({"state":"failed","reason":"provider.auth"}),
            json!({"state":"failed","reason":"fixture-provider-secret"}),
            json!({"state":"failed","reason":{"code":"modelNotConfigured"}}),
            json!({"state":"running","reason":"modelNotConfigured"}),
        ] {
            assert_eq!(observed_failure_reason(&observation), "sessionFailed");
        }
    }

    #[test]
    fn fresh_session_budget_zero_requires_durable_provenance_and_no_dispatch() {
        let fresh =
            json!({"sessionIds":{"planner":"ses_new"},"freshSessionIds":{"planner":"ses_new"}});
        assert_eq!(accounted_cost(&fresh), Some(0.0));
        assert_eq!(accounted_tokens(&fresh), Some(0));
        assert_eq!(accounted_daily_cost(&fresh, "2026-10-01"), Some(0.0));
        for patch in [
            json!({"freshSessionIds":null}),
            json!({"freshSessionIds":{"planner":"other"}}),
            json!({"promptDispatch":{"planner":"dispatching"}}),
            json!({"promptDispatch":{"planner":"dispatched"}}),
            json!({"promptDispatch":[]}),
            json!({"promptDispatch":{"planner":"invalid"}}),
        ] {
            let mut job = fresh.clone();
            for (key, value) in patch.as_object().unwrap() {
                job[key] = value.clone();
            }
            assert_eq!(accounted_cost(&job), None);
            assert_eq!(accounted_tokens(&job), None);
            assert_eq!(accounted_daily_cost(&job, "2026-10-01"), None);
        }
    }

    #[test]
    fn fresh_checker_keeps_observed_worker_cost_and_task_tokens() {
        let mut job = json!({"sessionIds":{"worker":"ses_worker","checker":"ses_checker"},
            "freshSessionIds":{"checker":"ses_checker"},"promptDispatch":{"worker":"dispatched"},
            "sessionUsage":{"worker":{"cost":0.7,"tokens":123,"day":"2026-10-01","dailyCost":0.7}}});
        assert_eq!(accounted_cost(&job), Some(0.7));
        assert_eq!(accounted_tokens(&job), Some(123));
        assert_eq!(accounted_daily_cost(&job, "2026-10-01"), Some(0.7));
        job["promptDispatch"]["checker"] = json!("dispatching");
        assert_eq!(accounted_cost(&job), None);
        assert_eq!(accounted_daily_cost(&job, "2026-10-01"), None);
    }

    #[test]
    fn daily_usage_requires_same_day_provenance_and_preserves_unknown_cross_day() {
        let mut job = json!({"sessionIds":{"worker":"ses_worker"},"freshSessionIds":{"worker":"ses_worker"},
            "sessionDispatchDays":{"worker":"2026-10-01"}});
        let first = observed_session_usage(
            &job,
            "worker",
            &json!({"cost":0.4,"tokens":{"total":10}}),
            "2026-10-01",
        );
        assert_eq!(first["dailyCost"], 0.4);
        job["sessionUsage"] = json!({"worker":first});
        let second = observed_session_usage(
            &job,
            "worker",
            &json!({"cost":0.7,"tokens":{"total":20}}),
            "2026-10-01",
        );
        assert_eq!(second["dailyCost"], 0.7);
        let tomorrow = observed_session_usage(
            &job,
            "worker",
            &json!({"cost":0.9,"tokens":{"total":30}}),
            "2026-10-02",
        );
        assert!(tomorrow["dailyCost"].is_null());
        job["sessionUsage"]["worker"] = tomorrow;
        assert!(observed_session_usage(
            &job,
            "worker",
            &json!({"cost":1.1,"tokens":{"total":40}}),
            "2026-10-02"
        )["dailyCost"]
            .is_null());
        job["sessionUsage"] = json!({});
        job["freshSessionIds"] = Value::Null;
        assert!(observed_session_usage(
            &job,
            "worker",
            &json!({"cost":0.4,"tokens":{"total":10}}),
            "2026-10-01"
        )["dailyCost"]
            .is_null());
    }

    #[test]
    fn first_unknown_usage_poll_does_not_poison_known_same_day_dispatch() {
        let mut job = json!({"sessionIds":{"worker":"ses_worker"},"freshSessionIds":{"worker":"ses_worker"},
            "sessionDispatchDays":{"worker":"2026-10-01"}});
        let unknown = observed_session_usage(&job, "worker", &json!({}), "2026-10-01");
        assert!(unknown["dailyCost"].is_null());
        job["sessionUsage"] = json!({"worker":unknown});
        let known = observed_session_usage(
            &job,
            "worker",
            &json!({"cost":0.4,"tokens":{"total":10}}),
            "2026-10-01",
        );
        assert_eq!(known["dailyCost"], 0.4);
        assert!(
            observed_session_usage(&job, "worker", &json!({"cost":0.4}), "2026-10-02")["dailyCost"]
                .is_null()
        );
        job["freshSessionIds"] = Value::Null;
        assert!(
            observed_session_usage(&job, "worker", &json!({"cost":0.4}), "2026-10-01")["dailyCost"]
                .is_null()
        );
    }

    async fn limited_parallel_fixture(e: &Engine) -> (String, Value) {
        let (first, second) = {
            let store = e.store.lock().unwrap();
            let created = store.execute(&json!({"requestId":"parallel-create","action":"createProject","name":"Limited parallel",
                "settings":{"mode":"parallel","maxLanes":2,"reviewLevel":"milestones","maxFixRounds":0,"chargingOnly":false,
                    "budget":{"chosen":true,"unlimited":false,"daily":2.0,"total":5.0,"taskTokens":null}},
                "spec":{"goal":"Respect known budgets","milestones":[{"id":"m","title":"Safe","criteria":["Verified"]}]},
                "repos":[{"id":"repo","serverId":"phone","path":"/root/work/parallel","devCommit":"seed","mainCommit":"seed"}]})).unwrap();
            let project = created["projectId"].as_str().unwrap();
            assert_eq!(store.execute(&json!({"requestId":"parallel-spec","action":"approveSpec","projectId":project,"expectedRevision":0})).unwrap()["accepted"],true);
            let planner = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
            store
                .update_job(&planner, "queued", &json!({"stage":"starting"}))
                .unwrap();
            store
                .update_job(&planner, "starting", &json!({"stage":"running"}))
                .unwrap();
            store.update_job(&planner,"running",&json!({"stage":"completed","plan":{"phases":[{"id":"phase","title":"Build","milestoneId":"m"}],
                "tasks":[{"id":"one","title":"One","phaseId":"phase","roleId":"worker","repoId":"repo","serverId":"phone","criteria":["Verified"],"dependsOn":[]},
                    {"id":"two","title":"Two","phaseId":"phase","roleId":"worker","repoId":"repo","serverId":"phone","criteria":["Verified"],"dependsOn":[]}]}})).unwrap();
            let revision = store.workspace().unwrap()["projects"][0]["revision"].clone();
            assert_eq!(store.execute(&json!({"requestId":"parallel-plan","action":"approvePlan","projectId":project,"expectedRevision":revision})).unwrap()["accepted"],true);
            let jobs = store.jobs().unwrap();
            let first = jobs.iter().find(|j| j["taskId"] == "one").unwrap()["id"]
                .as_str()
                .unwrap()
                .to_owned();
            let second = jobs.iter().find(|j| j["taskId"] == "two").unwrap()["id"]
                .as_str()
                .unwrap()
                .to_owned();
            for (id, session, dispatched) in
                [(&first, "ses_one", true), (&second, "ses_two", false)]
            {
                store
                    .update_job(id, "queued", &json!({"stage":"starting"}))
                    .unwrap();
                store.update_job(id,"starting",&json!({"stage":"running","directory":"/root/work/parallel","sessionIds":{"worker":session},"freshSessionIds":{"worker":session}})).unwrap();
                if dispatched {
                    store
                        .update_job(
                            id,
                            "running",
                            &json!({"promptDispatch":{"worker":"dispatched"}}),
                        )
                        .unwrap();
                }
            }
            let requested = store
                .jobs()
                .unwrap()
                .into_iter()
                .find(|j| j["id"] == second)
                .unwrap();
            (first, requested)
        };
        let now = crate::admission::now_ms();
        crate::admission::accept(
            e,
            ChatHeartbeat {
                until: now + 25_000,
                session_ids: vec![],
                directories: vec![],
                known: true,
                app_instance: "parallel-app".into(),
                sequence: 1,
            },
        )
        .await
        .unwrap();
        (first, second)
    }

    #[tokio::test]
    async fn parallel_prompt_waits_for_actual_other_lane_usage_before_dispatch() {
        use std::sync::atomic::{AtomicUsize, Ordering};
        let root = tempfile::tempdir().unwrap();
        let mut e = attested_fixture(root.path(), "proot");
        let posts = Arc::new(AtomicUsize::new(0));
        let writes = posts.clone();
        let session = Arc::new(Mutex::new(
            json!({"id":"ses_two","location":{"directory":"/root/work/parallel"},"agent":"build","model":{"providerID":"zai","id":"glm-5.3"}}),
        ));
        let instruction = Arc::new(Mutex::new(Value::Null));
        let app = Router::new().fallback(move |request: Request| {
            let writes = writes.clone();
            let session = session.clone();
            let instruction = instruction.clone();
            async move {
                let path=request.uri().path().to_owned();
                let method=request.method().clone();
                let body = axum::body::to_bytes(request.into_body(), 65536).await.unwrap();
                let value: Value = serde_json::from_slice(&body).unwrap_or(Value::Null);
                let response = match (method.as_str(), path.as_str()) {
                    ("GET", "/api/info") => json!({"version":"2.0.29","pid":1,"urls":[],"paths":{"tmp":"/tmp"}}),
                    ("GET", "/openapi.json") => serde_json::from_str(include_str!("../tests/fixtures/oc2-2.0.29-driver-openapi.json")).unwrap(),
                    ("GET", "/api/session/active") => json!({"data":{}}),
                    ("GET", "/api/session/ses_two") => json!({"data":session.lock().unwrap().clone()}),
                    ("PATCH", "/api/session/ses_two") => { session.lock().unwrap()["permissions"] = value["permissions"].clone(); return StatusCode::NO_CONTENT.into_response(); },
                    ("PUT", "/api/experimental/session/ses_two/instructions/entries/aiteam-role") => { *instruction.lock().unwrap()=value["value"].clone(); return StatusCode::NO_CONTENT.into_response(); },
                    ("GET", "/api/experimental/session/ses_two/instructions/entries") => json!({"data":[{"key":"aiteam-role","value":instruction.lock().unwrap().clone()}]}),
                    ("POST", "/api/session/ses_two/prompt") => { writes.fetch_add(1, Ordering::SeqCst); json!({"data":{"id":"msg_prompt","sessionID":"ses_two","type":"user"}}) },
                    ("POST", "/api/session/ses_two/agent" | "/api/session/ses_two/model") => return StatusCode::NO_CONTENT.into_response(),
                    _ => return StatusCode::NOT_FOUND.into_response(),
                };
                Json(response).into_response()
            }
        });
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let server = tokio::spawn(async move {
            axum::serve(listener, app).await.unwrap();
        });
        Arc::get_mut(&mut e).unwrap().server =
            OpenCodeClient::new(&url, "opencode", "fixture-only").unwrap();
        let (first, second) = limited_parallel_fixture(&e).await;
        let prompt = admitted_prompt(
            &e,
            &second,
            "/root/work/parallel",
            "ses_two",
            "worker",
            "zai/glm-5.3",
            "Do the approved task",
            "Task",
            "worker",
            false,
        );
        let usage = async {
            tokio::time::sleep(Duration::from_millis(40)).await;
            assert_eq!(
                posts.load(Ordering::SeqCst),
                0,
                "unknown budgets must not dispatch"
            );
            let store = e.store.lock().unwrap();
            let current = store
                .jobs()
                .unwrap()
                .into_iter()
                .find(|j| j["id"] == second["id"])
                .unwrap();
            assert_eq!(current["reason"], "totalUsageUnknown");
            assert!(current["promptDispatch"].is_null());
            store.update_job(&first,"running",&json!({"sessionUsage":{"worker":{"cost":0.2,"tokens":12,"day":utc_day(),"dailyCost":0.2}}})).unwrap();
        };
        let (result, ()) = tokio::join!(prompt, usage);
        server.abort();
        assert_eq!(result, Ok(()));
        assert_eq!(posts.load(Ordering::SeqCst), 1);
        let current = e
            .store
            .lock()
            .unwrap()
            .jobs()
            .unwrap()
            .into_iter()
            .find(|j| j["id"] == second["id"])
            .unwrap();
        assert_eq!(current["promptDispatch"]["worker"], "dispatched");
        assert_eq!(current["reason"], "");
    }

    #[test]
    fn late_admission_wait_cannot_overwrite_user_pause_or_stop_checkpoint() {
        for action in ["pauseProject", "stopProject"] {
            let root = tempfile::tempdir().unwrap();
            let e = fixture(root.path());
            let (project, id) = running_planner(&e);
            let requested = e.store.lock().unwrap().jobs().unwrap()[0].clone();
            {
                let store = e.store.lock().unwrap();
                let revision = store.workspace().unwrap()["projects"][0]["revision"].clone();
                assert_eq!(store.execute(&json!({"requestId":action,"action":action,"projectId":project,"expectedRevision":revision,"confirmed":true})).unwrap()["accepted"],true);
            }
            let before = e.store.lock().unwrap().jobs().unwrap();
            assert_eq!(
                record_admission_reason(&e, &requested, "totalUsageUnknown"),
                Err("jobNotActive")
            );
            assert_eq!(e.store.lock().unwrap().jobs().unwrap(), before);
            assert_eq!(before[0]["id"], id);
        }
    }

    #[tokio::test]
    async fn runner_error_cannot_overwrite_user_pause_during_pending_budget_admission() {
        let root = tempfile::tempdir().unwrap();
        let e = attested_fixture(root.path(), "proot");
        let (_, requested) = limited_parallel_fixture(&e).await;
        let job = requested["id"].as_str().unwrap().to_owned();
        let run = run_job(e.clone(), requested.clone());
        let pause = async {
            tokio::time::sleep(Duration::from_millis(40)).await;
            let store = e.store.lock().unwrap();
            let project = store.workspace().unwrap()["projects"][0].clone();
            assert_eq!(
                store
                    .execute(
                        &json!({"requestId":"pause-during-admission","action":"pauseProject",
                "projectId":project["id"],"expectedRevision":project["revision"]})
                    )
                    .unwrap()["accepted"],
                true
            );
            let paused = store
                .jobs()
                .unwrap()
                .into_iter()
                .find(|j| j["id"] == job)
                .unwrap();
            assert_eq!(paused["stage"], "interrupted");
            assert_eq!(paused["reason"], "pauseNeedsReconciliation");
            paused
        };
        let (result, paused) = tokio::join!(run, pause);
        assert_eq!(result, Err("jobNotActive"));
        let current = e
            .store
            .lock()
            .unwrap()
            .jobs()
            .unwrap()
            .into_iter()
            .find(|j| j["id"] == job)
            .unwrap();
        assert_eq!(
            current, paused,
            "runner completion must preserve the authoritative pause checkpoint"
        );
    }

    #[tokio::test]
    async fn persistent_parallel_usage_unknown_is_bounded_and_never_fabricated_zero() {
        let root = tempfile::tempdir().unwrap();
        let e = attested_fixture(root.path(), "proot");
        let (_, second) = limited_parallel_fixture(&e).await;
        let retry = AdmissionRetry {
            usage_unknown_since: None,
            usage_grace: Duration::from_millis(30),
            interval: Duration::from_millis(5),
        };
        let result = tokio::time::timeout(
            Duration::from_secs(2),
            stage_admitted_retry(&e, &second, retry),
        )
        .await
        .unwrap();
        assert_eq!(result, Err("totalUsageUnknown"));
        let current = e
            .store
            .lock()
            .unwrap()
            .jobs()
            .unwrap()
            .into_iter()
            .find(|j| j["id"] == second["id"])
            .unwrap();
        assert_eq!(current["reason"], "totalUsageUnknown");
        assert!(current["promptDispatch"].is_null());
        assert_eq!(
            accounted_cost(&current),
            Some(0.0),
            "second undispatched lane is zero; first lane remains unknown"
        );
    }

    /// An OC2 server whose `ses_test` turn died: the latest assistant message
    /// ended in an error and the session is idle and not running. It reports
    /// [pid]; every non-GET request is counted.
    async fn dead_turn_server(
        pid: u64,
    ) -> (
        String,
        Arc<std::sync::atomic::AtomicUsize>,
        tokio::task::JoinHandle<()>,
    ) {
        use std::sync::atomic::{AtomicUsize, Ordering};
        let posts = Arc::new(AtomicUsize::new(0));
        let writes = posts.clone();
        let app = Router::new().fallback(move |request: Request| {
            let writes = writes.clone();
            async move {
                if request.method() != axum::http::Method::GET { writes.fetch_add(1, Ordering::SeqCst); }
                let body = match request.uri().path() {
                    "/api/info" => json!({"version":"2.0.29","pid":pid,"urls":[],"paths":{"tmp":"/tmp"}}),
                    "/openapi.json" => serde_json::from_str(include_str!("../tests/fixtures/oc2-2.0.29-driver-openapi.json")).unwrap(),
                    "/api/session/ses_test" => json!({"data":{"id":"ses_test","location":{"directory":"/root/projects/poll"},"outcome":"failed","time":{"idle":3}}}),
                    "/api/session/active" => json!({"data":{}}),
                    "/api/session/ses_test/message" => json!({"data":[
                        {"id":"msg_1","sessionID":"ses_test","type":"user"},
                        {"id":"msg_2","sessionID":"ses_test","type":"assistant","finish":"error","error":{"type":"unknown"},"content":[{"type":"text","text":"half"}]},
                        {"id":"msg_3","sessionID":"ses_test","type":"idle","outcome":"failed","time":{"created":3}}
                    ],"cursor":{}}),
                    "/api/session/ses_test/permission" | "/api/session/ses_test/inbox" => json!({"data":[]}),
                    "/api/form" => json!({"location":{"directory":"/root/projects/poll"},"data":[]}),
                    _ => json!({}),
                };
                Json(body).into_response()
            }
        });
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = format!("http://{}", listener.local_addr().unwrap());
        let server = tokio::spawn(async move {
            axum::serve(listener, app).await.unwrap();
        });
        (address, posts, server)
    }

    #[tokio::test]
    async fn a_turn_killed_by_an_opencode_restart_is_named_and_a_plain_failure_is_not() {
        // The planner's turn was accepted by OpenCode pid 1. Found dead under
        // pid 2, the same server came back after a restart: serverRestarted.
        // Found dead under the pid that accepted it: an ordinary failure.
        for (now, expected) in [(2, "serverRestarted"), (1, "sessionFailed")] {
            let root = tempfile::tempdir().unwrap();
            let mut e = fixture(root.path());
            let (url, posts, server) = dead_turn_server(now).await;
            Arc::get_mut(&mut e).unwrap().server =
                OpenCodeClient::new(&url, "opencode", "fixture-only").unwrap();
            let (_, job) = running_planner(&e);
            e.store
                .lock()
                .unwrap()
                .update_job(&job, "running", &json!({"dispatchServerPid":{"planner":1}}))
                .unwrap();
            let result =
                await_completion(&e, &job, "planner", "/root/projects/poll", "ses_test").await;
            server.abort();
            assert_eq!(result.unwrap_err(), expected);
            // Reading a dead turn never sends anything.
            assert_eq!(posts.load(std::sync::atomic::Ordering::SeqCst), 0);
        }
    }

    #[test]
    fn a_restart_cut_step_is_sent_again_once_per_round_and_then_left_to_the_person() {
        let root = tempfile::tempdir().unwrap();
        let e = fixture(root.path());
        let (project, job) = running_planner(&e);
        let store = e.store.lock().unwrap();
        let cut = |store: &Store| {
            let stage = store.jobs().unwrap()[0]["stage"].as_str().unwrap().to_owned();
            store
                .update_job(&job, &stage, &json!({"stage":"interrupted","reason":"serverRestarted"}))
                .unwrap();
        };
        let resent = |store: &Store| {
            // The daemon's send of the round: dispatching, then dispatched.
            for state in ["dispatching", "dispatched"] {
                store
                    .update_job(&job, "resuming", &json!({"restartDispatch":{"planner":state}}))
                    .unwrap();
            }
        };
        // A restart-cut step is not resent behind the person's back unless
        // the round is opened, and the original prompt is never redispatched.
        assert_eq!(
            store
                .update_job(&job, "running", &json!({"restartDispatch":{"planner":"dispatching"}}))
                .unwrap_err()
                .code(),
            "invalidJobPatch"
        );
        for round in 1..=crate::store::AUTO_RESTART_RESENDS {
            cut(&store);
            let timeline = store.workspace().unwrap()["projects"][0]["timeline"].clone();
            assert!(timeline.as_array().unwrap().last().unwrap()["text"]
                .as_str()
                .unwrap()
                .contains("cut off when OpenCode restarted"));
            store.reconcile_restart_jobs().unwrap();
            let resumed = store.jobs().unwrap()[0].clone();
            assert_eq!(resumed["stage"], "resuming", "round {round}");
            assert_eq!(resumed["reason"], "restartResend");
            assert_eq!(resumed["restartResend"], "planner");
            assert_eq!(resumed["restartResends"]["planner"], round);
            assert_eq!(resumed["promptDispatch"]["planner"], "dispatched");
            assert!(restart_resend_due(&resumed, "planner"));
            resent(&store);
            let sent = store.jobs().unwrap()[0].clone();
            assert!(!restart_resend_due(&sent, "planner"));
            // Within a round the step is never sent twice.
            assert_eq!(
                store
                    .update_job(&job, "resuming", &json!({"restartDispatch":{"planner":"dispatching"}}))
                    .unwrap_err()
                    .code(),
                "promptAlreadyDispatched"
            );
            // Another engine pass does not open a second round by itself.
            store.reconcile_restart_jobs().unwrap();
            assert!(!restart_resend_due(&store.jobs().unwrap()[0], "planner"));
        }
        // Past the limit the engine stops and leaves the choice to the person.
        cut(&store);
        store.reconcile_restart_jobs().unwrap();
        let held = store.jobs().unwrap()[0].clone();
        assert_eq!(held["stage"], "interrupted");
        assert_eq!(held["reason"], "serverRestarted");
        // Resume still opens a round.
        let revision = store.workspace().unwrap()["projects"][0]["revision"].clone();
        assert_eq!(store.execute(&json!({"requestId":"resume-cut","action":"resumeProject","projectId":project,"expectedRevision":revision})).unwrap()["accepted"], true);
        let resumed = store.jobs().unwrap()[0].clone();
        assert_eq!(resumed["stage"], "resuming");
        assert!(restart_resend_due(&resumed, "planner"));
    }

    #[test]
    fn an_engine_death_mid_resend_observes_instead_of_sending_again() {
        let root = tempfile::tempdir().unwrap();
        let e = fixture(root.path());
        let (_, job) = running_planner(&e);
        let store = e.store.lock().unwrap();
        store
            .update_job(&job, "running", &json!({"stage":"interrupted","reason":"serverRestarted"}))
            .unwrap();
        store.reconcile_restart_jobs().unwrap();
        store
            .update_job(&job, "resuming", &json!({"restartDispatch":{"planner":"dispatching"}}))
            .unwrap();
        // The engine dies with the resend's acknowledgment unknown.
        store.recover().unwrap();
        store.reconcile_restart_jobs().unwrap();
        let after = store.jobs().unwrap()[0].clone();
        assert_eq!(after["stage"], "resuming");
        assert_eq!(after["restartResends"]["planner"], 1);
        assert!(!restart_resend_due(&after, "planner"));
    }

    #[tokio::test]
    async fn ready_restart_refetches_exact_session_without_new_prompt_or_clone() {
        let root = tempfile::tempdir().unwrap();
        let mut e = fixture(root.path());
        let (url, polls, posts, server) = poll_server(true, false).await;
        Arc::get_mut(&mut e).unwrap().server =
            OpenCodeClient::new(&url, "opencode", "fixture-only").unwrap();
        let (_, id) = running_planner(&e);
        {
            let store = e.store.lock().unwrap();
            store.recover().unwrap();
            store.reconcile_restart_jobs().unwrap();
            assert_eq!(store.jobs().unwrap()[0]["stage"], "resuming");
        }
        let result = await_completion(&e, &id, "planner", "/root/projects/poll", "ses_test").await;
        server.abort();
        assert_eq!(result.unwrap()["state"], "completed");
        assert!(polls.load(std::sync::atomic::Ordering::SeqCst) >= 2);
        assert_eq!(posts.load(std::sync::atomic::Ordering::SeqCst), 0);
        assert_eq!(
            e.store.lock().unwrap().jobs().unwrap()[0]["sessionIds"]["planner"],
            "ses_test"
        );
    }

    #[tokio::test]
    async fn accepted_async_turn_can_appear_after_first_idle_poll_without_resubmission() {
        let root = tempfile::tempdir().unwrap();
        let mut e = fixture(root.path());
        let (url, polls, posts, server) = poll_server(true, false).await;
        Arc::get_mut(&mut e).unwrap().server =
            OpenCodeClient::new(&url, "opencode", "fixture-only").unwrap();
        let (_, job) = running_planner(&e);
        let result = await_completion(&e, &job, "planner", "/root/projects/poll", "ses_test").await;
        server.abort();
        assert_eq!(result.unwrap()["state"], "completed");
        assert!(polls.load(std::sync::atomic::Ordering::SeqCst) >= 2);
        assert_eq!(posts.load(std::sync::atomic::Ordering::SeqCst), 0);
        assert_eq!(
            e.store.lock().unwrap().jobs().unwrap()[0]["sessionIds"]["planner"],
            "ses_test"
        );
    }

    #[tokio::test]
    async fn transient_observe_failure_refetches_same_session_without_new_prompt() {
        let root = tempfile::tempdir().unwrap();
        let mut e = fixture(root.path());
        let (url, _, posts, server) = poll_server(true, true).await;
        Arc::get_mut(&mut e).unwrap().server =
            OpenCodeClient::new(&url, "opencode", "fixture-only").unwrap();
        let (_, job) = running_planner(&e);
        let result = await_completion(&e, &job, "planner", "/root/projects/poll", "ses_test").await;
        server.abort();
        assert_eq!(result.unwrap()["state"], "completed");
        assert_eq!(posts.load(std::sync::atomic::Ordering::SeqCst), 0);
    }

    #[tokio::test]
    async fn stop_remains_effective_while_unknown_turn_is_in_grace_window() {
        let root = tempfile::tempdir().unwrap();
        let mut e = fixture(root.path());
        let (url, _, posts, server) = poll_server(false, false).await;
        Arc::get_mut(&mut e).unwrap().server =
            OpenCodeClient::new(&url, "opencode", "fixture-only").unwrap();
        let (project, job) = running_planner(&e);
        let observation = await_completion(&e, &job, "planner", "/root/projects/poll", "ses_test");
        let stop = async {
            tokio::time::sleep(Duration::from_millis(100)).await;
            let store = e.store.lock().unwrap();
            let revision = store.workspace().unwrap()["projects"][0]["revision"].clone();
            store.execute(&json!({"requestId":"stop-poll","action":"stopProject","projectId":project,"expectedRevision":revision,"confirmed":true})).unwrap()
        };
        let (result, stopped) = tokio::join!(observation, stop);
        server.abort();
        assert_eq!(stopped["accepted"], true);
        assert_eq!(result.unwrap_err(), "jobNotActive");
        assert_eq!(posts.load(std::sync::atomic::Ordering::SeqCst), 0);
    }

    #[test]
    fn fresh_clone_reset_never_reuses_a_dispatched_or_uncertain_checkpoint() {
        let pristine = json!({"stage":"queued","sessionIds":{},"promptDispatch":{}});
        assert!(undispatched_clone_allowed(&pristine));
        for patch in [
            json!({"sessionIds":{"worker":"ses_known"}}),
            json!({"promptDispatch":{"worker":"dispatching"}}),
            json!({"stage":"resuming"}),
            json!({"reason":"sessionCreateUncertain"}),
        ] {
            let mut candidate = pristine.clone();
            for (key, value) in patch.as_object().unwrap() {
                candidate[key] = value.clone();
            }
            assert!(!undispatched_clone_allowed(&candidate));
        }
    }
    #[tokio::test]
    async fn rejected_create_is_durable_before_import_and_delete_sweeps_only_unreferenced_repo() {
        let root = tempfile::tempdir().unwrap();
        let e = fixture(root.path());
        let settings = json!({"mode":"single","maxLanes":1,"reviewLevel":"milestones","maxFixRounds":2,"chargingOnly":false,"budget":{"chosen":false,"unlimited":true}});
        let mut create = json!({"requestId":"bad-create","action":"createProject","name":"GC regression","settings":settings,
            "spec":{"goal":"Verify deletion","milestones":[{"title":"GC","criteria":["Deleted repo removed"]}]},
            "repos":[{"id":"repo","serverId":"phone","path":"/root/projects/source"}]});
        let rejected = command(State(e.clone()), Ok(Json(create.clone())))
            .await
            .unwrap()
            .0;
        assert_eq!(rejected["accepted"], false);
        assert_eq!(rejected["code"], "chooseBudget");
        assert_eq!(
            e.store
                .lock()
                .unwrap()
                .command_result(&create)
                .unwrap()
                .unwrap()["replayed"],
            true
        );
        assert_eq!(
            fs::read_dir(e.config.private_root.join("repos/repos"))
                .unwrap()
                .count(),
            0
        );
        let source = git2::Repository::init(root.path().join("source")).unwrap();
        source.set_head("refs/heads/main").unwrap();
        let tree_id = source.index().unwrap().write_tree().unwrap();
        let tree = source.find_tree(tree_id).unwrap();
        let signature = git2::Signature::now("Fixture", "fixture@localhost").unwrap();
        source
            .commit(Some("HEAD"), &signature, &signature, "Seed", &tree, &[])
            .unwrap();
        e.repositories
            .lock()
            .unwrap()
            .import_repo("repo", source.workdir().unwrap())
            .unwrap();
        create["requestId"] = json!("valid-create");
        create["settings"]["budget"]["chosen"] = json!(true);
        let created = e.store.lock().unwrap().execute(&create).unwrap();
        let id = created["projectId"].clone();
        let delete = json!({"requestId":"delete-gc","action":"deleteProject","projectId":id,"expectedRevision":0,"confirmed":true});
        let result = command(State(e.clone()), Ok(Json(delete.clone())))
            .await
            .unwrap()
            .0;
        assert_eq!(result["accepted"], true);
        assert_eq!(result["cleanupPending"], false);
        assert!(e.repositories.lock().unwrap().refs("repo").is_err());
        let replay = command(State(e.clone()), Ok(Json(delete))).await.unwrap().0;
        assert_eq!(replay["accepted"], true);
        assert_eq!(replay["replayed"], true);
    }
    /// Issue #111/#112 Run A: the status stream dropped (OpenCode's
    /// protected restart) before the person opened any chat project, so the
    /// engine knew no chat folder. Reconciling needed one, so admission
    /// stayed "chat status unknown" even under a fresh, known, idle
    /// heartbeat from the app, until a chat project was opened.
    #[tokio::test]
    async fn busy_evidence_reconciles_with_no_chat_folder_known() {
        use crate::chat::ChatAdmission;
        use axum::{response::IntoResponse, routing::get};
        let spec: Value = serde_json::from_str(include_str!(
            "../tests/fixtures/oc2-2.0.29-driver-openapi.json"
        ))
        .unwrap();
        let active = Arc::new(Mutex::new(json!({"data":{}})));
        let served = active.clone();
        let app = Router::new()
            .route(
                "/api/info",
                get(|| async {
                    Json(json!({"version":"2.1.1","pid":7,"urls":[],"paths":{"tmp":"/tmp"}}))
                }),
            )
            .route(
                "/openapi.json",
                get(move || {
                    let spec = spec.clone();
                    async move { Json(spec).into_response() }
                }),
            )
            .route(
                "/api/session/active",
                get(move || {
                    let active = served.lock().unwrap().clone();
                    async move { Json(active) }
                }),
            );
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        let task = tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
        let temp = tempfile::tempdir().unwrap();
        let mut shared = fixture(temp.path());
        Arc::get_mut(&mut shared).unwrap().server =
            OpenCodeClient::new(&format!("http://{address}"), "opencode", "fixture-only").unwrap();
        let e = shared;
        e.chat.write().await.observation_unknown();
        *e.observer_connected.lock().unwrap() = true;
        let now = crate::admission::now_ms();
        let heartbeat = |sequence| crate::chat::ChatHeartbeat {
            until: crate::admission::now_ms() + 20_000,
            session_ids: vec![],
            directories: vec![],
            known: true,
            app_instance: "app-one".into(),
            sequence,
        };
        e.chat.write().await.accept(heartbeat(1), now).unwrap();
        assert_eq!(
            e.chat.read().await.admission(now, Some(true), &[]),
            ChatAdmission::Unknown
        );
        // A person's reply running anywhere on the server keeps it busy.
        *active.lock().unwrap() = json!({"data":{"ses_person":{"type":"running"}}});
        crate::admission::reconcile_busy(&e).await;
        assert_eq!(
            e.chat.read().await.admission(now, Some(true), &[]),
            ChatAdmission::Busy
        );
        *active.lock().unwrap() = json!({"data":{}});
        crate::admission::reconcile_busy(&e).await;
        let now = crate::admission::now_ms();
        e.chat.write().await.accept(heartbeat(2), now).unwrap();
        assert_eq!(
            e.chat.read().await.admission(now, Some(true), &[]),
            ChatAdmission::Idle
        );
        // Evidence alone never admits: an unknown app heartbeat stays unknown.
        let mut unknown = heartbeat(3);
        unknown.known = false;
        e.chat.write().await.accept(unknown, now).unwrap();
        assert_eq!(
            e.chat.read().await.admission(now, Some(true), &[]),
            ChatAdmission::Unknown
        );
        task.abort();
    }
    fn fixture(path: &std::path::Path) -> Shared {
        let private = path.join("private");
        let worker = path.join("worker");
        fs::create_dir_all(&private).unwrap();
        fs::create_dir_all(&worker).unwrap();
        let config = Config {
            schema_version: 1,
            profile_id: "profile".into(),
            private_root: private.clone(),
            worker_root: worker.clone(),
            guest_worker_root: "/root/aiteam/work/profile".into(),
            port: 4098,
            auth_token_file: private.join("auth.token"),
            server_credential_file: private.join("credentials"),
            server_base_url: "http://127.0.0.1:4097".into(),
            source_roots: vec![],
            boundary: crate::config::Boundary {
                verified: false,
                reason: "boundary_unverified".into(),
                restart_required: false,
                receipt_file: None,
                public_key_file: None,
                generation: None,
            },
        };
        Arc::new(Engine {
            store: Mutex::new(Store::open(&private, "profile").unwrap()),
            repositories: Mutex::new(
                RepositoryAuthority::new(private.join("repos"), worker).unwrap(),
            ),
            server: OpenCodeClient::new(
                "http://127.0.0.1:4097",
                "opencode",
                "fixture-server-secret",
            )
            .unwrap(),
            token: vec![b'a'; 64],
            protocol: Mutex::new(json!({})),
            boundary: None,
            deleting: std::sync::atomic::AtomicBool::new(false),
            lane_admission: tokio::sync::Mutex::new(()),
            merge_publication: tokio::sync::Mutex::new(()),
            chat: tokio::sync::RwLock::new(ChatLedger::default()),
            observer_connected: Mutex::new(false),
            parent_pid: unsafe { libc::getppid() },
            config,
        })
    }
    #[tokio::test]
    async fn repository_import_refusal_returns_static_typed_code_and_allows_corrected_retry() {
        let temp = tempfile::tempdir().unwrap();
        let projects = temp.path().join("projects");
        let source = projects.join("scratch");
        let repo = git2::Repository::init(&source).unwrap();
        repo.set_head("refs/heads/main").unwrap();
        drop(repo);
        let mut shared = fixture(temp.path());
        Arc::get_mut(&mut shared).unwrap().config.source_roots = vec![crate::config::SourceRoot {
            host_root: projects,
            guest_root: "/root/projects".into(),
        }];
        let command = json!({"requestId":"create-scratch","repos":[{"id":"editor_repo","serverId":"phone","path":"/root/projects/scratch"}]});
        let error = import_requested_repositories(&shared, &command).unwrap_err();
        let response = error.into_response();
        assert_eq!(response.status(), StatusCode::CONFLICT);
        let bytes = axum::body::to_bytes(response.into_body(), 65536)
            .await
            .unwrap();
        assert_eq!(
            serde_json::from_slice::<Value>(&bytes).unwrap(),
            json!({"code":"repository_empty"})
        );
        collect_unused_repositories(&shared).unwrap();
        let repo = git2::Repository::open(&source).unwrap();
        let tree_id = repo.index().unwrap().write_tree().unwrap();
        let tree = repo.find_tree(tree_id).unwrap();
        let signature = git2::Signature::now("QA", "qa@localhost.invalid").unwrap();
        let seed = repo
            .commit(Some("HEAD"), &signature, &signature, "Seed", &tree, &[])
            .unwrap();
        let imported = import_requested_repositories(&shared, &command).unwrap();
        assert_eq!(imported[0]["mainCommit"], seed.to_string());
        assert_eq!(imported[0]["devCommit"], seed.to_string());
    }
    fn attested_fixture(path: &std::path::Path, tier: &str) -> Shared {
        use p256::{
            ecdsa::{signature::Signer, Signature, SigningKey},
            pkcs8::EncodePublicKey,
        };
        use sha2::{Digest, Sha256};
        let mut shared = fixture(path);
        let e = Arc::get_mut(&mut shared).unwrap();
        let binaries = path.join("native");
        fs::create_dir(&binaries).unwrap();
        let executable = binaries.join("libaiteam_engine.so");
        for name in [
            "libaiteam_engine.so",
            "libaiteam_sandbox.so",
            "libaiteam_boundary_probe.so",
        ] {
            fs::write(binaries.join(name), name).unwrap();
        }
        let key = SigningKey::from_bytes((&[9u8; 32]).into()).unwrap();
        let public = key.verifying_key().to_public_key_der().unwrap();
        let key_file = e.config.private_root.join("public.der");
        fs::write(&key_file, public.as_bytes()).unwrap();
        let receipt_file = e.config.private_root.join("receipt.json");
        let generation = uuid::Uuid::new_v4().to_string();
        let payload = serde_json::to_vec(&json!({"schemaVersion":2,"tier":tier,"profileId":"profile","parentPid":e.parent_pid,
            "generation":generation,"bootId":attestation::boot_id().unwrap(),"kernelRelease":attestation::kernel_release().unwrap(),
            "policySha256":"a".repeat(64),"engineSha256":attestation::hash_file(&executable).unwrap(),
            "sandboxSha256":attestation::hash_file(&binaries.join("libaiteam_sandbox.so")).unwrap(),
            "probeSha256":attestation::hash_file(&binaries.join("libaiteam_boundary_probe.so")).unwrap(),
            "issuedAtElapsedMs":attestation::elapsed_ms().unwrap(),"protectedLaunchesRequired":true,
            "controls":{"nativeAttacksDenied":tier == "landlock","prootGitCompatible":true,"fixtureUnchanged":true,"complete":true,
                "canonicalPathsDenied":true,"daemonProcDenied":true,"fdHygiene":true,"parentInspectionDenied":true}})).unwrap();
        let signature: Signature = key.sign(&payload);
        fs::write(&receipt_file, &payload).unwrap();
        fs::write(
            attestation::signature_path(&receipt_file),
            signature.to_der().as_bytes(),
        )
        .unwrap();
        e.config.boundary.receipt_file = Some(receipt_file.clone());
        e.config.boundary.public_key_file = Some(key_file);
        e.config.boundary.generation = Some(generation.clone());
        let mut authority = BoundaryAuthority {
            pins: LaunchPins {
                trusted_public_key_sha256: format!("{:x}", Sha256::digest(public.as_bytes())),
                generation,
                policy_sha256: "a".repeat(64),
            },
            parent: e.parent_pid,
            executable,
            receipt: VerifiedBoundary {
                tier: BoundaryTier::Landlock,
                generation: String::new(),
                policy_sha256: String::new(),
                receipt_sha256: String::new(),
            },
        };
        authority.receipt =
            attestation::verify(&authority.expectation(&e.config).unwrap()).unwrap();
        e.boundary = Some(authority);
        *e.protocol.lock().unwrap() = json!({"pinnedVersion":true,"openapiVerified":true,"capabilities":{"executionDriver":true,"execution":false,"globalAdmissionAuthority":false}});
        shared
    }
    #[test]
    fn exact_native_receipt_composes_driver_capability_and_replacement_closes_it() {
        let temp = tempfile::tempdir().unwrap();
        let e = attested_fixture(temp.path(), "landlock");
        assert!(!e.config.boundary.verified);
        assert!(execution_enabled(&e));
        assert!(command_actions(&e).contains(&"createProject"));
        assert!(command_actions(&e).contains(&"promote"));
        e.deleting.store(true, std::sync::atomic::Ordering::SeqCst);
        assert!(!execution_enabled(&e));
        assert!(!command_actions(&e).contains(&"promote"));
        e.deleting.store(false, std::sync::atomic::Ordering::SeqCst);
        // No permanently cached verified boolean may survive a receipt change.
        fs::write(
            e.config.boundary.receipt_file.as_ref().unwrap(),
            b"replacement",
        )
        .unwrap();
        assert!(!execution_enabled(&e));
        assert!(!command_actions(&e).contains(&"createProject"));
        assert!(!command_actions(&e).contains(&"promote"));
    }
    #[test]
    fn failed_protocol_probes_retry_before_the_setup_readiness_window() {
        let failed =
            json!({"error":"transport_unavailable","capabilities":{"executionDriver":false}});
        assert_eq!(protocol_retry_delay(&failed), Duration::from_secs(2));
        assert_eq!(protocol_driver_reason(&failed), "transport_unavailable");
        let healthy = json!({"pinnedVersion":true,"openapiVerified":true,"capabilities":{"executionDriver":true}});
        assert_eq!(protocol_retry_delay(&healthy), Duration::from_secs(30));
        assert_eq!(protocol_driver_reason(&healthy), "");
    }
    #[tokio::test]
    async fn workspace_reports_live_server_state_and_preserves_command_revision() {
        let temp = tempfile::tempdir().unwrap();
        let e = attested_fixture(temp.path(), "proot");
        let before = e.store.lock().unwrap().workspace().unwrap();
        let observed = workspace(State(e.clone())).await.unwrap().0;
        assert_eq!(observed["servers"][0]["online"], true);
        assert_eq!(e.store.lock().unwrap().workspace().unwrap(), before);
        *e.protocol.lock().unwrap() =
            json!({"error":"authentication_failed","capabilities":{"executionDriver":false}});
        let observed = workspace(State(e.clone())).await.unwrap().0;
        assert_eq!(observed["servers"][0]["online"], false);
        assert_eq!(observed["servers"][0]["reason"], "authentication_failed");
        assert_eq!(
            health(State(e)).await.0["readinessReason"],
            "authentication_failed"
        );
    }
    #[tokio::test]
    async fn invalid_model_is_local_before_any_worker_or_dispatch_checkpoint() {
        let temp = tempfile::tempdir().unwrap();
        let e = fixture(temp.path());
        let job = json!({"id":"job-invalid-model","kind":"planner","model":"not-a-model-identity","instructions":"Plan only","repoId":"repo"});
        assert_eq!(task_pipeline(&e, &job).await, Err("invalid_model"));
        assert!(fs::read_dir(&e.config.worker_root)
            .unwrap()
            .next()
            .is_none());
        assert!(e.store.lock().unwrap().jobs().unwrap().is_empty());
    }
    #[tokio::test]
    async fn health_reports_only_live_signed_boundary_tier_at_both_locations() {
        for tier in ["landlock", "proot"] {
            let temp = tempfile::tempdir().unwrap();
            let e = attested_fixture(temp.path(), tier);
            let value = health(State(e.clone())).await.0;
            assert_eq!(value["boundaryTier"], tier);
            assert_eq!(value["capabilities"]["boundaryTier"], tier);
            assert_eq!(value["capabilities"]["boundary"], true);
            assert_eq!(value["capabilities"]["execution"], true);
            fs::write(
                e.config.boundary.receipt_file.as_ref().unwrap(),
                b"tampered",
            )
            .unwrap();
            let value = health(State(e)).await.0;
            assert_eq!(value["boundaryTier"], "none");
            assert_eq!(value["capabilities"]["boundaryTier"], "none");
            assert_eq!(value["capabilities"]["boundary"], false);
            assert_eq!(value["capabilities"]["execution"], false);
        }
    }
    #[tokio::test]
    async fn heartbeat_ack_waits_for_admission_and_stale_idle_cannot_overwrite_busy() {
        let temp = tempfile::tempdir().unwrap();
        let e = fixture(temp.path());
        let admitted = e.chat.read().await;
        let owned = e.clone();
        let now = crate::admission::now_ms();
        let pending = tokio::spawn(async move {
            crate::admission::accept(
                &owned,
                ChatHeartbeat {
                    until: now + 30_000,
                    session_ids: vec!["person".into()],
                    directories: vec!["/root/person".into()],
                    known: true,
                    app_instance: "app".into(),
                    sequence: 2,
                },
            )
            .await
        });
        tokio::task::yield_now().await;
        assert!(
            !pending.is_finished(),
            "human acknowledgment must await the admitted team dispatch"
        );
        drop(admitted);
        assert_eq!(pending.await.unwrap().unwrap()["admission"], "busy");
        assert_eq!(
            crate::admission::accept(
                &e,
                ChatHeartbeat {
                    until: now + 30_000,
                    session_ids: vec![],
                    directories: vec!["/root/person".into()],
                    known: true,
                    app_instance: "app".into(),
                    sequence: 1,
                }
            )
            .await
            .unwrap_err(),
            "heartbeatStale"
        );
        let ledger = e.chat.read().await;
        assert!(!crate::admission::idle(&e, &ledger).await.unwrap());
        drop(ledger);
        let reloaded = crate::admission::load_directories(&e.config);
        assert_eq!(reloaded.directories(), vec!["/root/person".to_string()]);
        assert_eq!(
            reloaded.admission(now, Some(true), &[]),
            crate::chat::ChatAdmission::Unknown
        );
        e.store.lock().unwrap().delete_profile().unwrap();
        assert_eq!(
            crate::admission::accept(
                &e,
                ChatHeartbeat {
                    until: now + 30_000,
                    session_ids: vec![],
                    directories: vec![],
                    known: true,
                    app_instance: "app".into(),
                    sequence: 3,
                }
            )
            .await
            .unwrap_err(),
            "profileDeleted"
        );
    }
    #[tokio::test]
    async fn all_routes_require_bearer_and_workspace_deletion_is_durable() {
        let temp = tempfile::tempdir().unwrap();
        let e = fixture(temp.path());
        let app = router(e.clone());
        for (method, route) in [
            ("GET", "/v1/health"),
            ("GET", "/v1/workspace"),
            ("GET", "/v1/events"),
            ("POST", "/v1/commands"),
            ("POST", "/v1/chatBusy"),
            ("DELETE", "/v1/profile"),
        ] {
            let response = app
                .clone()
                .oneshot(
                    axum::http::Request::builder()
                        .method(method)
                        .uri(route)
                        .body(Body::empty())
                        .unwrap(),
                )
                .await
                .unwrap();
            assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
        }
        let health = app
            .clone()
            .oneshot(
                axum::http::Request::builder()
                    .uri("/v1/health")
                    .header("Authorization", format!("Bearer {}", "a".repeat(64)))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(health.status(), StatusCode::OK);
        let bytes = axum::body::to_bytes(health.into_body(), 65536)
            .await
            .unwrap();
        let value: Value = serde_json::from_slice(&bytes).unwrap();
        assert_eq!(value["capabilities"]["execution"], false);
        assert_eq!(value["boundaryTier"], "none");
        assert_eq!(value["capabilities"]["boundaryTier"], "none");
        assert_eq!(value["profileId"], "profile");
        assert!(!value["commandActions"]
            .as_array()
            .unwrap()
            .contains(&json!("createProject")));
        let create = app
            .clone()
            .oneshot(
                axum::http::Request::builder()
                    .method("POST")
                    .uri("/v1/commands")
                    .header("Authorization", format!("Bearer {}", "a".repeat(64)))
                    .header("Content-Type", "application/json")
                    .body(Body::from(
                        json!({"requestId":"create","action":"createProject","name":"Valid protected create",
                            "settings":{"mode":"single","maxLanes":1,"reviewLevel":"milestones","maxFixRounds":2,"chargingOnly":false,"budget":{"chosen":true,"unlimited":true}},
                            "spec":{"goal":"Prove boundary","milestones":[{"title":"Protected","criteria":["No import before proof"]}]},
                            "repos":[{"id":"repo","serverId":"phone","path":"/root/projects/source"}]}).to_string(),
                    ))
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(create.status(), StatusCode::CONFLICT);
        assert!(e.store.lock().unwrap().workspace().unwrap()["projects"]
            .as_array()
            .unwrap()
            .is_empty());
        assert!(!String::from_utf8_lossy(&bytes).contains("fixture-server-secret"));
        let response = app
            .clone()
            .oneshot(
                axum::http::Request::builder()
                    .method("DELETE")
                    .uri("/v1/profile")
                    .header("Authorization", format!("Bearer {}", "a".repeat(64)))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::OK);
        assert!(e.store.lock().unwrap().workspace().is_err());
    }
    #[test]
    fn structured_output_accepts_one_trailing_checker_verdict_after_plain_summary() {
        // Shape observed on OC2 2.0.29 / GLM-5.3-Flash: successful final
        // assistant turn, inspection summary, then one unfenced JSON paragraph.
        let criteria = json!([
            "A file named NOTES.md exists at the repository root",
            "NOTES.md contains exactly one line with the text 'hid'",
            "No other files are created or modified"
        ]);
        let verdict = json!({"criterionResults": criteria.as_array().unwrap().iter()
            .map(|criterion| json!({"criterion":criterion,"status":"met"}))
            .collect::<Vec<_>>(), "findings":[]});
        let text = include_str!("../tests/fixtures/oc2_checker_summary_verdict.txt");
        assert_eq!(
            structured_output(&json!({"text":text})),
            Err("structuredOutputInvalid")
        );
        let parsed = structured_server_output(&json!({"text":text})).unwrap();
        assert_eq!(parsed, verdict);
        assert_eq!(validate_check(&parsed, &criteria), Ok(true));
    }

    #[test]
    fn structured_output_trailing_object_preserves_json_strings_and_schema_checks() {
        let criterion = "The example contains literal {braces}, [brackets], and \"quotes\".";
        let verdict =
            json!({"findings":[],"criterionResults":[{"criterion":criterion,"status":"met"}]});
        let parsed =
            structured_server_output(&json!({"text":format!("Inspection complete.\n\n{verdict}")}))
                .unwrap();
        assert_eq!(validate_check(&parsed, &json!([criterion])), Ok(true));
        // Parsing an unrelated object is never acceptance authority: the
        // unchanged checker schema and exact original criteria still decide.
        let unrelated = structured_server_output(
            &json!({"text":"An unrelated example follows.\n\n{\"example\":true}"}),
        )
        .unwrap();
        assert_eq!(
            validate_check(&unrelated, &json!([criterion])),
            Err("checkInvalid")
        );
        assert_eq!(
            validate_check(&parsed, &json!(["A different criterion"])),
            Err("checkInvalid")
        );
    }

    #[test]
    fn structured_output_rejects_ambiguous_or_malformed_unfenced_verdicts() {
        let verdict = r#"{"findings":[],"criterionResults":[{"criterion":"Exact criterion","status":"met"}]}"#;
        for text in [
            format!("First verdict.\n\n{verdict}\n\n{verdict}"),
            format!("Alternative {{proposal}}.\n\n{verdict}"),
            format!("Inspected [report].\n\n{verdict}"),
            format!("Quoted \"{{proposal}}\".\n\n{verdict}"),
            format!("The verdict is {verdict}"),
            format!("Inspection complete.\n\n{verdict}\nTrailing commentary."),
            format!("Inspection complete.\n\n{verdict}\n{{malformed"),
            format!("Inspection complete.\n\n{{malformed\n{verdict}"),
            format!("Inspection complete.\n\n[{verdict}]"),
            format!("```json\n{verdict}\n```\n\n{verdict}"),
            format!("Inspection complete.\n\n{verdict}\n```"),
            "Inspection complete.\n\n{\"findings\":[],\"findings\":[],\"criterionResults\":[]}".into(),
            "Inspection complete.\n\n{\"criterionResults\":[{\"status\":\"unmet\",\"status\":\"met\"}],\"findings\":[]}".into(),
            "Verification complete. All checks passed.".into(),
            format!("{}\n\n{verdict}", "x".repeat(1024 * 1024)),
        ] {
            assert_eq!(structured_server_output(&json!({"text":text})), Err("structuredOutputInvalid"));
        }
    }

    #[test]
    fn checker_prompt_separates_criterion_status_from_defects_and_requires_evidence() {
        let job = json!({"criteria":["Run cargo test --locked", "Keep README unchanged"]});
        let request = checker_request(&job);
        assert!(request.contains(&job["criteria"].to_string()));
        for required in [
            "exactly one entry for each original criterion",
            "copying its criterion string exactly",
            "Only criterionResults.status may be met, unmet, or notApplicable",
            "actual unresolved defects only",
            "status open",
            "Do not include successful observations",
            "findings as an empty array",
            "Use only read, glob, and grep tools",
            "Do not modify files or run commands",
            "Do not claim unexecuted checks passed or fabricate evidence",
            "required executed-check evidence means status unmet",
            "Do not waive findings",
            "any finding or any result other than met blocks merge",
        ] {
            assert!(
                request.contains(required),
                "missing checker policy: {required}"
            );
        }
        let examples = request.split_once("all-pass shape: ").unwrap().1;
        let (passed, defect) = examples.split_once("; unresolved-defect shape: ").unwrap();
        let passed: Value = serde_json::from_str(passed).unwrap();
        let defect: Value = serde_json::from_str(defect.strip_suffix('.').unwrap()).unwrap();
        let criteria = json!(["<exact original criterion>"]);
        assert_eq!(passed["findings"], json!([]));
        assert_eq!(passed["criterionResults"][0]["status"], "met");
        assert!(validate_check(&passed, &criteria).unwrap());
        assert_eq!(defect["findings"][0]["status"], "open");
        assert_eq!(defect["criterionResults"][0]["status"], "unmet");
        assert!(!validate_check(&defect, &criteria).unwrap());
    }

    #[test]
    fn parallel_tasks_use_distinct_committed_verification_reports() {
        let first = json!({"taskId":"task-one","criteria":["Verify one"],"title":"One"});
        let second = json!({"taskId":"task-two","criteria":["Verify two"],"title":"Two"});
        assert!(worker_request(&first, "task/one").contains(".aiteam-verification/task-one.md"));
        assert!(worker_request(&second, "task/two").contains(".aiteam-verification/task-two.md"));
        assert!(checker_request(&first).contains(".aiteam-verification/task-one.md"));
        assert!(!worker_request(&first, "task/one").contains(".aiteam-verification/task-two.md"));
    }

    #[test]
    fn worker_prompt_requires_committed_actual_acceptance_check_evidence() {
        let job = json!({"criteria":["Run the smoke test"],"title":"Add greeting"});
        let request = worker_request(&job, "task/greeting");
        for required in [
            "branch task/greeting",
            "without editing main or dev",
            ".aiteam-verification.md",
            "commit that task-verification report with your changes",
            "actual executed acceptance checks",
            "exact commands, working directory, exit codes",
            "observed results/output",
            "passed, failed, skipped, and not-run checks",
            "Never fabricate evidence",
            "A proposed command is not an executed check",
            "Do not include credentials or secrets",
            "checker is read-only and cannot execute your checks",
        ] {
            assert!(
                request.contains(required),
                "missing worker policy: {required}"
            );
        }
        assert!(request.contains(&job["criteria"].to_string()));
        assert!(request.contains(&job["title"].to_string()));
    }

    #[test]
    fn successful_observation_mislabeled_as_a_finding_still_blocks_merge() {
        let value = json!({
            "findings":[{"criterion":"Run checks", "severity":"minor",
                "text":"All checks passed", "status":"met"}],
            "criterionResults":[{"criterion":"Run checks", "status":"met"}]
        });
        assert!(!validate_check(&value, &json!(["Run checks"])).unwrap());
        let no_findings = json!({
            "findings":[],
            "criterionResults":[{"criterion":"Run checks", "status":"notApplicable"}]
        });
        assert!(!validate_check(&no_findings, &json!(["Run checks"])).unwrap());
    }

    #[test]
    fn checker_cannot_waive_findings_or_invent_token_totals() {
        let check = json!({"criterionResults":[{"criterion":"safe","status":"met"}],"findings":[{"criterion":"safe","text":"Unresolved","severity":"major","status":"ignored"}]});
        assert!(!validate_check(&check, &json!(["safe"])).unwrap());
        assert_eq!(
            normalized_usage(&json!({"tokens":{"input":10,"output":20}}))["tokens"],
            Value::Null
        );
        assert_eq!(
            normalized_usage(&json!({"cost":0.0,"tokens":{"total":0}})),
            json!({"cost":0.0,"tokens":0})
        );
    }
    #[test]
    fn arbitrary_output_and_dependency_cycles_are_refused() {
        assert!(structured_output(&json!({"text":"Done!"})).is_err());
        let plan = json!({"spec":{},"phases":[],"tasks":[{"id":"a","repoId":"r","roleId":"worker","criteria":["A"],"dependsOn":["b"]},{"id":"b","repoId":"r","roleId":"worker","criteria":["B"],"dependsOn":["a"]}]});
        assert!(validate_plan(&plan, "r").is_err());
    }
}
