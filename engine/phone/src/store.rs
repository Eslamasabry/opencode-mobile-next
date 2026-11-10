//! Profile-isolated durable project snapshots and CAS job stages.
use rusqlite::{params, Connection, OptionalExtension, TransactionBehavior};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    collections::HashSet,
    fs,
    path::Path,
    sync::{Mutex, MutexGuard},
    time::{SystemTime, UNIX_EPOCH},
};
use uuid::Uuid;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StoreError(&'static str);
impl StoreError {
    pub fn code(&self) -> &'static str {
        self.0
    }
}
impl std::fmt::Display for StoreError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.0)
    }
}
impl std::error::Error for StoreError {}
impl From<rusqlite::Error> for StoreError {
    fn from(_: rusqlite::Error) -> Self {
        Self("storageUnavailable")
    }
}

pub struct Store {
    conn: Mutex<Connection>,
}
/// Event cursors are bounded independently of command and project history.
pub const EVENT_RETENTION_LIMIT: usize = 10_000;
impl Store {
    pub fn open(root: &Path, profile: &str) -> Result<Self, StoreError> {
        if profile.is_empty()
            || profile.len() > 128
            || !profile
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
        {
            return Err(StoreError("invalidProfile"));
        }
        fs::create_dir_all(root).map_err(|_| StoreError("storageUnavailable"))?;
        let dir = root.join(format!("oc.teamEngine.{profile}"));
        reject_link(&dir)?;
        fs::create_dir_all(&dir).map_err(|_| StoreError("storageUnavailable"))?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&dir, fs::Permissions::from_mode(0o700))
                .map_err(|_| StoreError("storageUnavailable"))?;
        }
        let path = dir.join("state.sqlite3");
        reject_link(&path)?;
        for suffix in ["state.sqlite3-wal", "state.sqlite3-shm"] {
            reject_link(&dir.join(suffix))?;
        }
        let mut conn = Connection::open(path).map_err(StoreError::from)?;
        conn.busy_timeout(std::time::Duration::from_secs(5))?;
        conn.execute_batch("PRAGMA journal_mode=WAL; PRAGMA synchronous=FULL; PRAGMA secure_delete=ON; CREATE TABLE IF NOT EXISTS meta(id INTEGER PRIMARY KEY CHECK(id=1), deleted INTEGER NOT NULL DEFAULT 0); INSERT OR IGNORE INTO meta(id,deleted) VALUES(1,0); CREATE TABLE IF NOT EXISTS workspace(id INTEGER PRIMARY KEY CHECK(id=1), data TEXT NOT NULL); CREATE TABLE IF NOT EXISTS jobs(id TEXT PRIMARY KEY, data TEXT NOT NULL); CREATE TABLE IF NOT EXISTS commands(id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, result TEXT NOT NULL); CREATE TABLE IF NOT EXISTS events(seq INTEGER PRIMARY KEY AUTOINCREMENT, data TEXT NOT NULL);")?;
        conn.execute_batch("CREATE TABLE IF NOT EXISTS event_retention(id INTEGER PRIMARY KEY CHECK(id=1), pruned_through INTEGER NOT NULL DEFAULT 0); INSERT OR IGNORE INTO event_retention(id,pruned_through) VALUES(1,0);")?;
        let tx = conn.transaction_with_behavior(TransactionBehavior::Immediate)?;
        alive(&tx)?;
        prune_events(&tx)?;
        tx.execute(
            "INSERT OR IGNORE INTO workspace(id,data) VALUES(1,?1)",
            [initial_workspace().to_string()],
        )?;
        tx.commit()?;
        Ok(Self {
            conn: Mutex::new(conn),
        })
    }
    fn lock(&self) -> Result<MutexGuard<'_, Connection>, StoreError> {
        self.conn
            .lock()
            .map_err(|_| StoreError("storageUnavailable"))
    }
    pub fn workspace(&self) -> Result<Value, StoreError> {
        let conn = self.lock()?;
        alive(&conn)?;
        let mut workspace = load_workspace(&conn)?;
        let jobs = load_jobs(&conn)?;
        for project in workspace["projects"]
            .as_array_mut()
            .ok_or(StoreError("storageCorrupt"))?
        {
            let project_id = project["id"].clone();
            if let Some(tasks) = project["tasks"].as_array_mut() {
                for task in tasks {
                    if let Some(reason) = jobs
                        .iter()
                        .rev()
                        .find(|job| {
                            job["kind"] == "task"
                                && job["projectId"] == project_id
                                && job["taskId"] == task["id"]
                        })
                        .and_then(|job| job["reason"].as_str())
                    {
                        // Repair legacy stale presentation without rewriting a
                        // checkpoint, its status, or the command revision.
                        task["reason"] = json!(reason);
                    }
                }
            }
            project["mergeQueue"] = checked_merge_queue(project, &jobs);
            let planner = jobs
                .iter()
                .rev()
                .find(|j| j["kind"] == "planner" && j["projectId"] == project["id"]);
            project["planningState"] = planner
                .map(|j| json!({"jobId":j["id"],"stage":j["stage"],"reason":j["reason"].as_str().unwrap_or(""),"updatedAt":j["updatedAt"]}))
                .unwrap_or(Value::Null);
            if let Some(planner) = planner {
                if project["timeline"].is_null()
                    || project["timeline"].as_array().is_some_and(Vec::is_empty)
                {
                    // Legacy checkpoints predate durable timeline rows. Project
                    // their present state without inventing historical events.
                    append_job_timeline(project, planner)?;
                    project["timeline"][0]["id"] = json!(format!(
                        "planner-checkpoint-{}",
                        planner["id"].as_str().unwrap_or("")
                    ));
                }
            }
        }
        Ok(workspace)
    }
    pub fn jobs(&self) -> Result<Vec<Value>, StoreError> {
        let conn = self.lock()?;
        alive(&conn)?;
        load_jobs(&conn)
    }
    pub fn events(&self, after: i64, limit: usize) -> Result<Vec<Value>, StoreError> {
        if after < 0 || limit == 0 || limit > 1000 {
            return Err(StoreError("invalidCursor"));
        }
        let conn = self.lock()?;
        alive(&conn)?;
        let pruned: i64 = conn.query_row(
            "SELECT pruned_through FROM event_retention WHERE id=1",
            [],
            |r| r.get(0),
        )?;
        if after < pruned {
            return Err(StoreError("cursorExpired"));
        }
        let mut stmt =
            conn.prepare("SELECT seq,data FROM events WHERE seq>?1 ORDER BY seq LIMIT ?2")?;
        let rows = stmt.query_map(params![after, limit as i64], |r| {
            Ok((r.get::<_, i64>(0)?, r.get::<_, String>(1)?))
        })?;
        let mut out = Vec::new();
        for row in rows {
            let (seq, data) = row?;
            let mut value = decode(&data)?;
            value["seq"] = json!(seq);
            out.push(value);
        }
        Ok(out)
    }
    /// Clients must disclose a digest gap and refetch workspace after cursor expiry.
    pub fn event_window(&self) -> Result<Value, StoreError> {
        let conn = self.lock()?;
        alive(&conn)?;
        let pruned: i64 = conn.query_row(
            "SELECT pruned_through FROM event_retention WHERE id=1",
            [],
            |r| r.get(0),
        )?;
        let (earliest, latest): (Option<i64>, Option<i64>) =
            conn.query_row("SELECT MIN(seq),MAX(seq) FROM events", [], |r| {
                Ok((r.get(0)?, r.get(1)?))
            })?;
        Ok(
            json!({"retentionLimit":EVENT_RETENTION_LIMIT,"prunedThroughSeq":pruned,"earliestAvailableSeq":earliest,"latestSeq":latest.unwrap_or(pruned)}),
        )
    }
    /// Mutation-free semantic preflight before any repository import side effects.
    /// Replay remains command_result's responsibility, and execute rechecks current state.
    pub fn validate_command(&self, command: &Value) -> Result<(), StoreError> {
        if !command.is_object() || command.to_string().len() > 1_048_576 {
            return Err(StoreError("invalidCommand"));
        }
        if required_str(command, "requestId", "invalidRequestId")?.len() > 128 {
            return Err(StoreError("invalidRequestId"));
        }
        let conn = self.lock()?;
        alive(&conn)?;
        let mut workspace = load_workspace(&conn)?;
        let mut jobs = load_jobs(&conn)?;
        apply_command(&mut workspace, &mut jobs, command)?;
        Ok(())
    }
    pub fn execute(&self, command: &Value) -> Result<Value, StoreError> {
        self.execute_command(command, None)
    }
    /// Read-only replay check before the daemon performs any external side effect.
    pub fn command_result(&self, command: &Value) -> Result<Option<Value>, StoreError> {
        let request = required_str(command, "requestId", "invalidRequestId")?;
        if request.len() > 128 || !command.is_object() || command.to_string().len() > 1_048_576 {
            return Err(StoreError("invalidCommand"));
        }
        let fingerprint = format!("{:x}", Sha256::digest(command.to_string().as_bytes()));
        let conn = self.lock()?;
        alive(&conn)?;
        let old: Option<(String, String)> = conn
            .query_row(
                "SELECT fingerprint,result FROM commands WHERE id=?1",
                [request],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )
            .optional()?;
        match old {
            Some((previous, result)) => {
                if previous != fingerprint {
                    return Err(StoreError("requestIdReuse"));
                }
                let mut result = decode(&result)?;
                result["replayed"] = json!(true);
                Ok(Some(result))
            }
            None => Ok(None),
        }
    }
    /// Imports are trusted daemon values. The replay binding remains the authored command.
    pub fn execute_with_repositories(
        &self,
        command: &Value,
        imported_repos: &Value,
    ) -> Result<Value, StoreError> {
        if !matches!(
            command["action"].as_str(),
            Some("createProject" | "createQuickTask")
        ) {
            return Err(StoreError("invalidRepositoryReceipt"));
        }
        let original = command["repos"]
            .as_array()
            .ok_or(StoreError("invalidPlacement"))?;
        let imported = imported_repos
            .as_array()
            .ok_or(StoreError("invalidRepositoryReceipt"))?;
        if original.len() != imported.len() {
            return Err(StoreError("invalidRepositoryReceipt"));
        }
        for (author, actual) in original.iter().zip(imported) {
            if author["id"] != actual["id"]
                || author["path"] != actual["path"]
                || author["serverId"] != actual["serverId"]
            {
                return Err(StoreError("invalidRepositoryReceipt"));
            }
            required_str(actual, "devCommit", "invalidRepositoryReceipt")?;
            required_str(actual, "mainCommit", "invalidRepositoryReceipt")?;
        }
        self.execute_command(command, Some(imported_repos))
    }
    fn execute_command(
        &self,
        command: &Value,
        imported_repos: Option<&Value>,
    ) -> Result<Value, StoreError> {
        if !command.is_object() || command.to_string().len() > 1_048_576 {
            return Err(StoreError("invalidCommand"));
        }
        let request = required_str(command, "requestId", "invalidRequestId")?;
        if request.len() > 128 {
            return Err(StoreError("invalidRequestId"));
        }
        let _ = required_str(command, "action", "invalidCommand")?;
        let fingerprint = format!("{:x}", Sha256::digest(command.to_string().as_bytes()));
        let mut conn = self.lock()?;
        let tx = conn.transaction_with_behavior(TransactionBehavior::Immediate)?;
        alive(&tx)?;
        let old: Option<(String, String)> = tx
            .query_row(
                "SELECT fingerprint,result FROM commands WHERE id=?1",
                [request],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )
            .optional()?;
        if let Some((previous, result)) = old {
            if previous != fingerprint {
                return Ok(result_json(false, "requestIdReuse", "", 0));
            }
            let mut result = decode(&result)?;
            result["replayed"] = json!(true);
            return Ok(result);
        }
        let mut w = load_workspace(&tx)?;
        let mut jobs = load_jobs(&tx)?;
        let previous_jobs = jobs.clone();
        let mut applied = command.clone();
        if let Some(repos) = imported_repos {
            applied["repos"] = repos.clone();
        }
        let result = match apply_command(&mut w, &mut jobs, &applied) {
            Ok((id, revision)) => {
                for current in &jobs {
                    let previous = previous_jobs.iter().find(|j| j["id"] == current["id"]);
                    if previous.is_none_or(|old| {
                        old["stage"] != current["stage"] || old["reason"] != current["reason"]
                    }) {
                        if let Some(project) = w["projects"]
                            .as_array_mut()
                            .and_then(|ps| ps.iter_mut().find(|p| p["id"] == current["projectId"]))
                        {
                            append_job_timeline(project, current)?;
                        }
                    }
                }
                persist(&tx, &w, &jobs)?;
                event(
                    &tx,
                    "command",
                    command["action"].as_str().unwrap_or(""),
                    &id,
                    "",
                    revision,
                )?;
                result_json(true, "", &id, revision)
            }
            Err(e) => result_json(
                false,
                e.code(),
                command["projectId"].as_str().unwrap_or(""),
                project_revision(&w, command["projectId"].as_str().unwrap_or("")),
            ),
        };
        tx.execute(
            "INSERT INTO commands(id,fingerprint,result) VALUES(?1,?2,?3)",
            params![request, fingerprint, result.to_string()],
        )?;
        tx.commit()?;
        Ok(result)
    }
    pub fn update_job(
        &self,
        id: &str,
        expected_stage: &str,
        patch: &Value,
    ) -> Result<Value, StoreError> {
        let allowed = [
            "stage",
            "reason",
            "directory",
            "sessionIds",
            "expectedDevCommit",
            "expectedMainCommit",
            "taskCommit",
            "mergedCommit",
            "usage",
            "plan",
            "task",
            "findings",
            "criterionResults",
            "repoReceipt",
            "sessionUsage",
            "promptDispatch",
            "restartDispatch",
            "dispatchServerPid",
            "freshSessionIds",
            "resolvedModels",
        ];
        let fields = patch.as_object().ok_or(StoreError("invalidJobPatch"))?;
        if fields.keys().any(|k| !allowed.contains(&k.as_str()))
            || patch.to_string().len() > 1_048_576
        {
            return Err(StoreError("invalidJobPatch"));
        }
        let mut conn = self.lock()?;
        let tx = conn.transaction_with_behavior(TransactionBehavior::Immediate)?;
        alive(&tx)?;
        let mut w = load_workspace(&tx)?;
        let mut jobs = load_jobs(&tx)?;
        let index = jobs
            .iter()
            .position(|j| j["id"] == id)
            .ok_or(StoreError("jobNotFound"))?;
        if jobs[index]["stage"] != expected_stage {
            return Err(StoreError("staleJobStage"));
        }
        let mut j = jobs[index].clone();
        let previous_reason = j["reason"].as_str().unwrap_or("").to_owned();
        let pi = w["projects"]
            .as_array()
            .ok_or(StoreError("storageCorrupt"))?
            .iter()
            .position(|p| p["id"] == j["projectId"])
            .ok_or(StoreError("projectNotFound"))?;
        let plan_catalog = json!({"roles":w["roles"],"servers":w["servers"]});
        let p = &mut w["projects"][pi];
        if p["status"] == "stopped" {
            return Err(StoreError("projectStopped"));
        }
        let stage = patch
            .get("stage")
            .and_then(Value::as_str)
            .unwrap_or(expected_stage);
        if !valid_transition(expected_stage, stage) {
            return Err(StoreError("invalidJobTransition"));
        }
        let usage_only = stage == expected_stage
            && fields
                .keys()
                .all(|key| matches!(key.as_str(), "stage" | "usage" | "sessionUsage"))
            && (fields.contains_key("usage") || fields.contains_key("sessionUsage"));
        if stage == "interrupted" && expected_stage != "interrupted" {
            checkpoint_interruption(&mut j, expected_stage);
        }
        if (p["status"] == "paused" || p["status"] == "pausedBudget")
            && stage != expected_stage
            && !matches!(stage, "interrupted" | "paused" | "stopped")
        {
            return Err(StoreError("projectPaused"));
        }
        if let Some(resolved) = patch.get("resolvedModels") {
            let resolved = resolved
                .as_object()
                .filter(|models| !models.is_empty())
                .ok_or(StoreError("invalidResolvedModel"))?;
            if !j["resolvedModels"].is_null() && !j["resolvedModels"].is_object() {
                return Err(StoreError("storageCorrupt"));
            }
            for (role, value) in resolved {
                let model = value
                    .as_str()
                    .filter(|model| !model.is_empty())
                    .ok_or(StoreError("invalidResolvedModel"))?;
                if !["planner", "worker", "checker"].contains(&role.as_str())
                    || crate::opencode::validate_model(model).is_err()
                {
                    return Err(StoreError("invalidResolvedModel"));
                }
                if let Some(previous) = j["resolvedModels"].get(role) {
                    if previous != value {
                        return Err(StoreError("resolvedModelAlreadyRecorded"));
                    }
                } else if j["sessionIds"].get(role).is_some()
                    || j["promptDispatch"].get(role).is_some()
                    || patch["sessionIds"].get(role).is_some()
                    || patch["promptDispatch"].get(role).is_some()
                {
                    // A provider selection is fixed before the session exists,
                    // including across death and lost dispatch acknowledgments.
                    return Err(StoreError("modelResolutionTooLate"));
                }
            }
        }
        if let Some(fresh) = patch.get("freshSessionIds") {
            for (role, session) in fresh.as_object().ok_or(StoreError("invalidJobPatch"))? {
                if !["planner", "worker", "checker"].contains(&role.as_str())
                    || patch["sessionIds"][role] != *session
                    || session.as_str().filter(|id| !id.is_empty()).is_none()
                    || j["sessionIds"].get(role).is_some()
                    || j["promptDispatch"].get(role).is_some()
                {
                    return Err(StoreError("invalidJobPatch"));
                }
            }
        }
        for (key, value) in fields {
            if matches!(
                key.as_str(),
                "plan"
                    | "task"
                    | "findings"
                    | "criterionResults"
                    | "usage"
                    | "repoReceipt"
                    | "sessionUsage"
            ) {
                continue;
            }
            if key == "resolvedModels" {
                if j["resolvedModels"].is_null() {
                    j["resolvedModels"] = json!({});
                }
                for (role, model) in value
                    .as_object()
                    .ok_or(StoreError("invalidResolvedModel"))?
                {
                    j["resolvedModels"][role] = model.clone();
                }
            } else if key == "freshSessionIds" {
                if j["freshSessionIds"].is_null() {
                    j["freshSessionIds"] = json!({});
                }
                for (role, session) in value.as_object().ok_or(StoreError("invalidJobPatch"))? {
                    j["freshSessionIds"][role] = session.clone();
                }
            } else if key == "promptDispatch" {
                let dispatches = value.as_object().ok_or(StoreError("invalidJobPatch"))?;
                for (role, state) in dispatches {
                    if !["planner", "worker", "checker"].contains(&role.as_str())
                        || j["sessionIds"][role]
                            .as_str()
                            .or_else(|| patch["sessionIds"][role].as_str())
                            .is_none()
                        || !matches!(state.as_str(), Some("dispatching" | "dispatched"))
                    {
                        return Err(StoreError("invalidJobPatch"));
                    }
                    if j["promptDispatch"][role] == "dispatched" && state != "dispatched" {
                        return Err(StoreError("promptAlreadyDispatched"));
                    }
                    if j["promptDispatch"].is_null() {
                        j["promptDispatch"] = json!({});
                    }
                    if j["promptDispatch"].get(role).is_none() {
                        if j["sessionDispatchDays"].is_null() {
                            j["sessionDispatchDays"] = json!({});
                        }
                        j["sessionDispatchDays"][role] = json!(&now()[..10]);
                    }
                    j["promptDispatch"][role] = state.clone();
                }
            } else if key == "restartDispatch" {
                // The one resend after an OpenCode restart has its own ledger:
                // the original prompt stays "dispatched" forever, and a round
                // that reached "dispatched" never goes back to "dispatching".
                for (role, state) in value.as_object().ok_or(StoreError("invalidJobPatch"))? {
                    if !["planner", "worker", "checker"].contains(&role.as_str())
                        || j["sessionIds"][role].as_str().is_none()
                        || j["restartResend"] != role.as_str()
                        || !matches!(state.as_str(), Some("dispatching" | "dispatched"))
                    {
                        return Err(StoreError("invalidJobPatch"));
                    }
                    if j["restartDispatch"][role] == "dispatched" && state != "dispatched" {
                        return Err(StoreError("promptAlreadyDispatched"));
                    }
                    if !j["restartDispatch"].is_object() {
                        j["restartDispatch"] = json!({});
                    }
                    j["restartDispatch"][role] = state.clone();
                }
            } else if key == "dispatchServerPid" {
                for (role, pid) in value.as_object().ok_or(StoreError("invalidJobPatch"))? {
                    if !["planner", "worker", "checker"].contains(&role.as_str()) || !pid.is_u64() {
                        return Err(StoreError("invalidJobPatch"));
                    }
                    if !j["dispatchServerPid"].is_object() {
                        j["dispatchServerPid"] = json!({});
                    }
                    j["dispatchServerPid"][role] = pid.clone();
                }
            } else if key == "sessionIds" {
                let sessions = value.as_object().ok_or(StoreError("invalidJobPatch"))?;
                for (role, session) in sessions {
                    if !["planner", "worker", "checker"].contains(&role.as_str())
                        || session
                            .as_str()
                            .filter(|s| !s.is_empty() && s.len() <= 256)
                            .is_none()
                    {
                        return Err(StoreError("invalidJobPatch"));
                    }
                    if let Some(old) = j["sessionIds"].get(role) {
                        if old != session {
                            return Err(StoreError("sessionAlreadyRecorded"));
                        }
                    }
                    j["sessionIds"][role] = session.clone();
                }
            } else {
                j[key] = value.clone();
            }
        }
        let mut admission_reason_cleared = false;
        if stage != expected_stage
            && !fields.contains_key("reason")
            && matches!(
                stage,
                "starting"
                    | "running"
                    | "checking"
                    | "mergeReady"
                    | "merging"
                    | "completed"
                    | "resuming"
            )
            && matches!(
                j["reason"].as_str(),
                Some(
                    "chatBusy"
                        | "chatStatusUnknown"
                        | "chatStateUnknown"
                        | "laneCap"
                        | "dependencyPending"
                        | "projectNotRunning"
                        | "serverOffline"
                        | "executionUnavailable"
                        | "boundaryUnverified"
                        | "protocolUnverified"
                        | "Protected execution is not verified"
                )
            )
        {
            j["reason"] = json!("");
            admission_reason_cleared = true;
        }
        if let Some(usage) = patch.get("usage").filter(|v| !v.is_null()) {
            update_usage(&mut j, usage)?;
        }
        if let Some(sessions) = patch.get("sessionUsage") {
            for (role, usage) in sessions.as_object().ok_or(StoreError("invalidUsage"))? {
                if !["planner", "worker", "checker"].contains(&role.as_str())
                    || j["sessionIds"][role].as_str().is_none()
                {
                    return Err(StoreError("invalidUsage"));
                }
                let mut session = json!({"usage":j["sessionUsage"].get(role).cloned().unwrap_or(json!({"cost":null,"tokens":null}))});
                update_usage(&mut session, usage)?;
                if j["sessionUsage"].is_null() {
                    j["sessionUsage"] = json!({});
                }
                j["sessionUsage"][role] = session["usage"].clone();
            }
        }
        if patch.get("sessionUsage").is_some()
            || (patch.get("sessionIds").is_some() && patch.get("usage").is_none())
        {
            // Cumulative snapshots are summed once per recorded session. A new
            // session with unknown usage must not reuse the previous role's total.
            let roles: Vec<_> = j["sessionIds"]
                .as_object()
                .ok_or(StoreError("invalidUsage"))?
                .keys()
                .cloned()
                .collect();
            let cost: Option<f64> = roles
                .iter()
                .map(|role| j["sessionUsage"][role]["cost"].as_f64())
                .sum();
            let tokens = roles.iter().try_fold(0u64, |sum, role| {
                sum.checked_add(j["sessionUsage"][role]["tokens"].as_u64()?)
            });
            j["usage"]["cost"] = json!(cost.filter(|n| n.is_finite()));
            j["usage"]["tokens"] = json!(tokens);
            let day = patch["sessionUsage"]
                .as_object()
                .into_iter()
                .flat_map(|roles| roles.values())
                .filter_map(|usage| usage["day"].as_str())
                .max();
            if let Some(day) = day {
                let daily: Option<f64> = roles
                    .iter()
                    .map(|role| {
                        let usage = &j["sessionUsage"][role];
                        if usage["day"] == day {
                            usage["dailyCost"].as_f64()
                        } else {
                            None
                        }
                    })
                    .sum();
                j["usage"]["day"] = json!(day);
                j["usage"]["dailyCost"] = json!(daily.filter(|n| n.is_finite()));
            }
        }
        if let Some(receipt) = patch.get("repoReceipt") {
            if j["kind"] != "task" || stage != "completed" {
                return Err(StoreError("invalidRepositoryReceipt"));
            }
            apply_repo_receipt(p, &j, receipt, "merge")?;
            j["repoReceipt"] = receipt.clone();
        }
        if j["kind"] == "planner" {
            if let Some(plan) = patch.get("plan") {
                if stage != "completed" || !plan.is_object() {
                    return Err(StoreError("invalidPlan"));
                }
                let tasks = plan["tasks"].as_array().ok_or(StoreError("invalidPlan"))?;
                let phases = plan["phases"].as_array().ok_or(StoreError("invalidPlan"))?;
                // The model supplies proposals only. Approval and queue creation remain commands.
                p["tasks"] = json!(tasks
                    .iter()
                    .map(normalize_task)
                    .collect::<Result<Vec<_>, _>>()?);
                p["phases"] = json!(normalize_phases(phases)?);
                validate_plan(&plan_catalog, p)?;
                p["planApproved"] = json!(false);
                p["status"] = json!("needsPlanApproval");
            }
        } else {
            let ti = p["tasks"]
                .as_array()
                .ok_or(StoreError("storageCorrupt"))?
                .iter()
                .position(|t| t["id"] == j["taskId"])
                .ok_or(StoreError("taskNotFound"))?;
            let task = &mut p["tasks"][ti];
            if let Some(tp) = patch.get("task") {
                let task_fields = [
                    "reason",
                    "branch",
                    "steps",
                    "fixRounds",
                    "affected",
                    "findings",
                    "criterionResults",
                    "diff",
                    "messages",
                ];
                for (key, value) in tp.as_object().ok_or(StoreError("invalidTaskPatch"))? {
                    if !task_fields.contains(&key.as_str()) {
                        return Err(StoreError("invalidTaskPatch"));
                    }
                    if key == "findings" {
                        let findings = open_checker_findings(value)?;
                        task[key] = findings.clone();
                        j[key] = findings;
                    } else {
                        task[key] = value.clone();
                    }
                }
            }
            for key in ["findings", "criterionResults"] {
                if let Some(value) = patch.get(key) {
                    if !value.is_array() {
                        return Err(StoreError("invalidTaskPatch"));
                    }
                    let published = if key == "findings" {
                        open_checker_findings(value)?
                    } else {
                        value.clone()
                    };
                    task[key] = published.clone();
                    j[key] = published;
                }
            }
            task["status"] = json!(task_status(stage));
            if !usage_only {
                task["changedAt"] = json!(now());
            }
            if stage == "needsFix" {
                let reason = patch["reason"]
                    .as_str()
                    .filter(|reason| !reason.is_empty())
                    .or_else(|| {
                        patch["task"]["reason"]
                            .as_str()
                            .filter(|reason| !reason.is_empty())
                    })
                    .unwrap_or("checkerFindings");
                j["reason"] = json!(reason);
                task["reason"] = json!(reason);
            } else if let Some(reason) = patch.get("reason") {
                task["reason"] = reason.clone();
            } else if let Some(reason) = patch["task"]
                .get("reason")
                .filter(|reason| reason.is_string())
            {
                j["reason"] = reason.clone();
            } else if admission_reason_cleared {
                task["reason"] = j["reason"].clone();
            }
            if let Some(tokens) = j["usage"]["tokens"].as_u64() {
                task["tokens"] = json!(tokens);
                task["usageReported"] = json!(true);
            }
        }
        j["stage"] = json!(stage);
        j["updatedAt"] = json!(now());
        if !usage_only
            && (stage != expected_stage || j["reason"].as_str().unwrap_or("") != previous_reason)
        {
            append_job_timeline(p, &j)?;
        }
        jobs[index] = j.clone();
        if !usage_only {
            increment_project(p);
        }
        if fields.contains_key("usage") || fields.contains_key("sessionUsage") {
            p["usageRevision"] = json!(p["usageRevision"].as_u64().unwrap_or(0) + 1);
            jobs[index]["usageRevision"] = json!(j["usageRevision"].as_u64().unwrap_or(0) + 1);
            j = jobs[index].clone();
        }
        w["revision"] = json!(w["revision"].as_u64().unwrap_or(0) + 1);
        aggregate_usage(&mut w, &jobs);
        persist(&tx, &w, &jobs)?;
        event(
            &tx,
            if usage_only { "usage" } else { "job" },
            if usage_only { "sessionUsage" } else { stage },
            j["projectId"].as_str().unwrap_or(""),
            j["taskId"].as_str().unwrap_or(""),
            w["projects"][pi]["revision"].as_u64().unwrap_or(0),
        )?;
        tx.commit()?;
        Ok(j)
    }
    /// Called only by the daemon after RepositoryAuthority proves an actual promotion.
    /// Public commands alone never write canonical refs or promotion receipts.
    pub fn record_promotion(&self, command: &Value, receipt: &Value) -> Result<Value, StoreError> {
        if command["action"] != "promote" || command["confirmed"] != true {
            return Err(StoreError("confirmationRequired"));
        }
        let request = required_str(command, "requestId", "invalidRequestId")?;
        if request.len() > 128 || command.to_string().len() > 1_048_576 {
            return Err(StoreError("invalidCommand"));
        }
        let fingerprint = format!("{:x}", Sha256::digest(command.to_string().as_bytes()));
        let mut conn = self.lock()?;
        let tx = conn.transaction_with_behavior(TransactionBehavior::Immediate)?;
        alive(&tx)?;
        let old: Option<(String, String)> = tx
            .query_row(
                "SELECT fingerprint,result FROM commands WHERE id=?1",
                [request],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )
            .optional()?;
        if let Some((previous, result)) = old {
            if previous != fingerprint {
                return Err(StoreError("requestIdReuse"));
            }
            let mut result = decode(&result)?;
            result["replayed"] = json!(true);
            return Ok(result);
        }
        let mut w = load_workspace(&tx)?;
        let jobs = load_jobs(&tx)?;
        let id = required_str(command, "projectId", "projectNotFound")?;
        let pi = w["projects"]
            .as_array()
            .unwrap()
            .iter()
            .position(|p| p["id"] == id)
            .ok_or(StoreError("projectNotFound"))?;
        let p = &mut w["projects"][pi];
        if command["expectedRevision"].as_u64() != p["revision"].as_u64() {
            return Err(StoreError("staleRevision"));
        }
        let repo_id = required_str(command, "targetId", "repoNotFound")?;
        if receipt["receiptId"] != request
            || receipt["repoId"] != repo_id
            || receipt["before"]["devCommit"] != command["expectedDevCommit"]
            || receipt["before"]["mainCommit"] != command["expectedMainCommit"]
            || receipt["after"]["devCommit"] != command["expectedDevCommit"]
            || receipt["after"]["mainCommit"] != command["expectedDevCommit"]
        {
            return Err(StoreError("invalidRepositoryReceipt"));
        }
        let binding = json!({"id":request,"repoId":repo_id});
        apply_repo_receipt(p, &binding, receipt, "promote")?;
        increment_project(p);
        let revision = p["revision"].as_u64().unwrap_or(0);
        w["revision"] = json!(w["revision"].as_u64().unwrap_or(0) + 1);
        let result = result_json(true, "", id, revision);
        persist(&tx, &w, &jobs)?;
        tx.execute(
            "INSERT INTO commands(id,fingerprint,result) VALUES(?1,?2,?3)",
            params![request, fingerprint, result.to_string()],
        )?;
        event(&tx, "repository", "promote", id, "", revision)?;
        tx.commit()?;
        Ok(result)
    }
    pub fn recover(&self) -> Result<(), StoreError> {
        let mut conn = self.lock()?;
        let tx = conn.transaction_with_behavior(TransactionBehavior::Immediate)?;
        alive(&tx)?;
        let mut w = load_workspace(&tx)?;
        let mut jobs = load_jobs(&tx)?;
        let mut changed = HashSet::new();
        for j in &mut jobs {
            if crate::scheduler::active_stage(j["stage"].as_str().unwrap_or(""))
                || j["stage"] == "mergeReady"
            {
                let stage = j["stage"].as_str().unwrap_or("").to_owned();
                checkpoint_interruption(j, &stage);
                j["stage"] = json!("interrupted");
                j["reason"] = json!("restartNeedsReconciliation");
                j["updatedAt"] = json!(now());
                let id = j["projectId"].as_str().unwrap_or("").to_owned();
                if let Some(p) = w["projects"]
                    .as_array_mut()
                    .and_then(|ps| ps.iter_mut().find(|p| p["id"] == id))
                {
                    if let Some(t) = p["tasks"]
                        .as_array_mut()
                        .and_then(|ts| ts.iter_mut().find(|t| t["id"] == j["taskId"]))
                    {
                        t["status"] = json!("interrupted");
                        t["reason"] = json!("restartNeedsReconciliation");
                    }
                    p["status"] = json!("interrupted");
                    append_job_timeline(p, j)?;
                    changed.insert(id);
                }
            }
        }
        for id in &changed {
            if let Some(p) = w["projects"]
                .as_array_mut()
                .and_then(|ps| ps.iter_mut().find(|p| p["id"] == *id))
            {
                increment_project(p);
            }
            event(
                &tx,
                "recovery",
                "interrupted",
                id,
                "",
                project_revision(&w, id),
            )?;
        }
        if !changed.is_empty() {
            w["revision"] = json!(w["revision"].as_u64().unwrap_or(0) + 1);
            persist(&tx, &w, &jobs)?;
        }
        tx.commit()?;
        Ok(())
    }
    /// Ready-daemon attachment may observe only restart-owned checkpoints.
    /// User pause/stop and ambiguous or reviewable failures require a command.
    pub fn reconcile_restart_jobs(&self) -> Result<(), StoreError> {
        let mut conn = self.lock()?;
        let tx = conn.transaction_with_behavior(TransactionBehavior::Immediate)?;
        alive(&tx)?;
        let mut w = load_workspace(&tx)?;
        let mut jobs = load_jobs(&tx)?;
        let mut changed = HashSet::new();
        for j in &mut jobs {
            let restart_cut = restart_cut_resumable(j);
            if j["stage"] != "interrupted"
                || (j["reason"] != "restartNeedsReconciliation" && !restart_cut)
            {
                continue;
            }
            let id = j["projectId"].as_str().unwrap_or("").to_owned();
            let Some(p) = w["projects"]
                .as_array_mut()
                .and_then(|ps| ps.iter_mut().find(|p| p["id"] == id))
            else {
                continue;
            };
            if !matches!(
                p["status"].as_str(),
                Some("interrupted" | "running" | "planning")
            ) {
                continue;
            }
            if resume_job_checkpoint(j).is_err() {
                if restart_cut {
                    // Leave it for the person: Resume is still offered.
                    continue;
                }
                j["reason"] = json!("recoveryNeedsReview");
                j["updatedAt"] = json!(now());
            } else if restart_cut {
                j["reason"] = json!("restartResend");
            }
            append_job_timeline(p, j)?;
            changed.insert(id);
        }
        for id in &changed {
            if let Some(p) = w["projects"]
                .as_array_mut()
                .and_then(|ps| ps.iter_mut().find(|p| p["id"] == *id))
            {
                if p["status"] == "interrupted"
                    && jobs.iter().any(|j| {
                        j["projectId"] == *id
                            && matches!(j["stage"].as_str(), Some("queued" | "resuming"))
                    })
                {
                    p["status"] = json!(if p["planApproved"] == true {
                        "running"
                    } else {
                        "planning"
                    });
                }
                sync_task_stages(p, &jobs);
                increment_project(p);
            }
            event(
                &tx,
                "recovery",
                "reconcile",
                id,
                "",
                project_revision(&w, id),
            )?;
        }
        if !changed.is_empty() {
            w["revision"] = json!(w["revision"].as_u64().unwrap_or(0) + 1);
            persist(&tx, &w, &jobs)?;
        }
        tx.commit()?;
        Ok(())
    }
    pub fn delete_profile(&self) -> Result<(), StoreError> {
        let mut conn = self.lock()?;
        let tx = conn.transaction_with_behavior(TransactionBehavior::Immediate)?;
        // Tombstone stays in the same database so every open connection observes it.
        tx.execute_batch("UPDATE meta SET deleted=1 WHERE id=1; DELETE FROM workspace; DELETE FROM jobs; DELETE FROM commands; DELETE FROM events;")?;
        tx.commit()?;
        conn.execute_batch(
            "PRAGMA wal_checkpoint(TRUNCATE); VACUUM; PRAGMA wal_checkpoint(TRUNCATE);",
        )?;
        Ok(())
    }
}

fn reject_link(path: &Path) -> Result<(), StoreError> {
    match fs::symlink_metadata(path) {
        Ok(m) if m.file_type().is_symlink() => Err(StoreError("unsafeStoragePath")),
        Ok(_) => Ok(()),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(_) => Err(StoreError("storageUnavailable")),
    }
}
fn alive(conn: &Connection) -> Result<(), StoreError> {
    if conn.query_row("SELECT deleted FROM meta WHERE id=1", [], |r| {
        r.get::<_, i64>(0)
    })? != 0
    {
        Err(StoreError("profileDeleted"))
    } else {
        Ok(())
    }
}
fn decode(s: &str) -> Result<Value, StoreError> {
    serde_json::from_str(s).map_err(|_| StoreError("storageCorrupt"))
}
fn load_workspace(conn: &Connection) -> Result<Value, StoreError> {
    decode(
        &conn.query_row("SELECT data FROM workspace WHERE id=1", [], |r| {
            r.get::<_, String>(0)
        })?,
    )
}
fn load_jobs(conn: &Connection) -> Result<Vec<Value>, StoreError> {
    let mut stmt = conn.prepare("SELECT data FROM jobs ORDER BY rowid")?;
    let rows = stmt.query_map([], |r| r.get::<_, String>(0))?;
    let mut out = Vec::new();
    for row in rows {
        out.push(decode(&row?)?);
    }
    Ok(out)
}
fn checked_merge_queue(project: &Value, jobs: &[Value]) -> Value {
    let Some(tasks) = project["tasks"].as_array() else {
        return json!([]);
    };
    json!(jobs
        .iter()
        .filter(|job| job["projectId"] == project["id"] && job["kind"] == "task" && matches!(job["stage"].as_str(), Some("completed" | "merged")))
        .filter_map(|job| {
            let task = tasks.iter().find(|task| task["id"] == job["taskId"])?;
            let evidence = checked_merge_evidence(project, job, task);
            Some(json!({"id":format!("merge-{}",job["id"].as_str().unwrap_or("")),"taskId":task["id"],"repoId":task["repoId"],"status":if evidence.is_ok() {"merged"} else {"blocked"},"reason":evidence.err().unwrap_or(""),"checksPassed":evidence.is_ok()}))
        })
        .collect::<Vec<_>>())
}
fn checked_merge_evidence(project: &Value, job: &Value, task: &Value) -> Result<(), &'static str> {
    if task["status"] != "merged" {
        return Err("mergeNotCompleted");
    }
    if job["repoId"] != task["repoId"]
        || job["serverId"] != task["serverId"]
        || job["criteria"] != task["criteria"]
        || !project["repos"].as_array().is_some_and(|repos| {
            repos
                .iter()
                .any(|repo| repo["id"] == job["repoId"] && repo["serverId"] == job["serverId"])
        })
    {
        return Err("mergeScopeInvalid");
    }
    // Older jobs kept checker snapshots only on the matched project task.
    // Partial job evidence never falls back: both fields must be absent.
    let source = if job.get("findings").is_none() && job.get("criterionResults").is_none() {
        task
    } else {
        job
    };
    if crate::daemon::validate_check(source, &job["criteria"]) != Ok(true)
        || crate::daemon::validate_check(task, &job["criteria"]) != Ok(true)
    {
        return Err("checksNotPassed");
    }
    let receipt = job
        .get("repoReceipt")
        .filter(|r| r.is_object())
        .ok_or("mergeReceiptMissing")?;
    if receipt["repoId"] != job["repoId"] || receipt["taskId"] != task["id"] {
        return Err("mergeScopeInvalid");
    }
    for side in ["before", "after"] {
        for key in ["devCommit", "mainCommit"] {
            if receipt[side][key]
                .as_str()
                .is_none_or(|s| s.len() != 40 || !s.bytes().all(|b| b.is_ascii_hexdigit()))
            {
                return Err("mergeReceiptInvalid");
            }
        }
    }
    if job["taskCommit"]
        .as_str()
        .is_none_or(|s| s.len() != 40 || !s.bytes().all(|b| b.is_ascii_hexdigit()))
        || receipt["taskCommit"] != job["taskCommit"]
        || receipt["devCommit"] != receipt["after"]["devCommit"]
        || receipt["mainCommit"] != receipt["after"]["mainCommit"]
        || job["mergedCommit"] != receipt["after"]["devCommit"]
        || receipt["before"]["mainCommit"] != receipt["after"]["mainCommit"]
    {
        return Err("mergeReceiptInvalid");
    }
    let bound = project["receipts"].as_array().is_some_and(|receipts| {
        receipts.iter().any(|r| {
            r["id"] == job["id"]
                && r["kind"] == "merge"
                && r["actor"] == "engine"
                && r["repoId"] == job["repoId"]
                && (r.get("taskId").is_none() || r["taskId"] == job["taskId"])
                && r["beforeRefs"] == receipt["before"]
                && r["afterRefs"] == receipt["after"]
                && r["before"] == receipt["before"]["devCommit"]
                && r["after"] == receipt["after"]["devCommit"]
        })
    });
    if !bound {
        return Err("mergeReceiptUnbound");
    }
    Ok(())
}
fn persist(conn: &Connection, w: &Value, jobs: &[Value]) -> Result<(), StoreError> {
    conn.execute("UPDATE workspace SET data=?1 WHERE id=1", [w.to_string()])?;
    conn.execute("DELETE FROM jobs", [])?;
    for j in jobs {
        conn.execute(
            "INSERT INTO jobs(id,data) VALUES(?1,?2)",
            params![
                j["id"].as_str().ok_or(StoreError("invalidJob"))?,
                j.to_string()
            ],
        )?;
    }
    Ok(())
}
fn event(
    conn: &Connection,
    kind: &str,
    action: &str,
    project: &str,
    task: &str,
    revision: u64,
) -> Result<(), StoreError> {
    conn.execute("INSERT INTO events(data) VALUES(?1)",[json!({"kind":kind,"action":action,"projectId":project,"taskId":task,"revision":revision,"at":now()}).to_string()])?;
    prune_events(conn)?;
    Ok(())
}
fn prune_events(conn: &Connection) -> Result<(), StoreError> {
    let cutoff: Option<i64> = conn
        .query_row(
            "SELECT seq FROM events ORDER BY seq DESC LIMIT 1 OFFSET ?1",
            [EVENT_RETENTION_LIMIT as i64],
            |r| r.get(0),
        )
        .optional()?;
    if let Some(cutoff) = cutoff {
        conn.execute(
            "UPDATE event_retention SET pruned_through=MAX(pruned_through,?1) WHERE id=1",
            [cutoff],
        )?;
        conn.execute("DELETE FROM events WHERE seq<=?1", [cutoff])?;
    }
    Ok(())
}
fn now() -> String {
    let elapsed = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default();
    let seconds = elapsed.as_secs();
    let z = (seconds / 86_400) as i64 + 719_468;
    let era = z / 146_097;
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let mut year = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let day = doy - (153 * mp + 2) / 5 + 1;
    let month = mp + if mp < 10 { 3 } else { -9 };
    if month <= 2 {
        year += 1;
    }
    let remainder = seconds % 86_400;
    format!(
        "{year:04}-{month:02}-{day:02}T{:02}:{:02}:{:02}.{:03}Z",
        remainder / 3600,
        (remainder % 3600) / 60,
        remainder % 60,
        elapsed.subsec_millis()
    )
}
fn new_id(kind: &str) -> String {
    format!("{kind}-{}", Uuid::new_v4())
}
fn result_json(accepted: bool, code: &str, id: &str, revision: u64) -> Value {
    json!({"accepted":accepted,"code":code,"projectId":id,"revision":revision,"replayed":false})
}
fn required_str<'a>(v: &'a Value, key: &str, code: &'static str) -> Result<&'a str, StoreError> {
    v[key]
        .as_str()
        .filter(|s| !s.trim().is_empty())
        .ok_or(StoreError(code))
}
fn project_revision(w: &Value, id: &str) -> u64 {
    w["projects"]
        .as_array()
        .and_then(|ps| ps.iter().find(|p| p["id"] == id))
        .and_then(|p| p["revision"].as_u64())
        .unwrap_or(0)
}
fn increment_project(p: &mut Value) {
    p["revision"] = json!(p["revision"].as_u64().unwrap_or(0) + 1);
    p["updatedAt"] = json!(now());
}
fn default_settings() -> Value {
    json!({"keepWorkingScreenOff":false,"mode":"single","maxLanes":1,"reviewLevel":"milestones","chargingOnly":false,"autoFix":true,"maxFixRounds":2,"budget":{"chosen":false,"unlimited":false,"daily":null,"total":null,"taskTokens":null}})
}
fn initial_workspace() -> Value {
    json!({"schemaVersion":1,"revision":0,"simulated":false,"projects":[],"servers":[{"id":"phone","name":"This phone","phone":true,"online":false,"laneCap":1,"chatWaiting":true,"memoryMb":null}],"roles":[{"id":"planner","name":"Planner","instructions":"Return a plan for approval. Do not change project files.","model":"","fallbackModel":"","readOnly":true},{"id":"worker","name":"Worker","instructions":"Implement the approved task and its acceptance criteria.","model":"","fallbackModel":"","readOnly":false},{"id":"checker","name":"Checker","instructions":"Check the task criteria without changing project files.","model":"","fallbackModel":"","readOnly":true}],"defaultSettings":default_settings()})
}
fn empty_spec() -> Value {
    json!({"contextFiles":[],"version":1,"goal":"","constraints":"","decisions":"","outOfScope":"","milestones":[],"approvedBy":"","approvedAt":""})
}
fn normalize_spec(spec: &Value) -> Result<Value, StoreError> {
    required_str(spec, "goal", "invalidSpec")?;
    if !spec.is_object() {
        return Err(StoreError("invalidSpec"));
    }
    let mut out = empty_spec();
    for key in [
        "contextFiles",
        "goal",
        "constraints",
        "decisions",
        "outOfScope",
        "milestones",
    ] {
        if let Some(v) = spec.get(key) {
            out[key] = v.clone();
        }
    }
    if !out["milestones"].is_array() || !out["contextFiles"].is_array() {
        return Err(StoreError("invalidSpec"));
    }
    Ok(out)
}
fn validate_settings(s: &Value) -> Result<(), StoreError> {
    if s["chargingOnly"] == true {
        return Err(StoreError("chargingUnsupported"));
    }
    if !matches!(s["mode"].as_str(), Some("single" | "parallel"))
        || !matches!(s["maxLanes"].as_u64(), Some(1..=32))
    {
        return Err(StoreError("chooseExecutionMode"));
    }
    let b = &s["budget"];
    if b["chosen"] != true
        || (b["unlimited"] != true && b["daily"].is_null() && b["total"].is_null())
    {
        return Err(StoreError("chooseBudget"));
    }
    for key in ["daily", "total"] {
        if !b[key].is_null()
            && b[key]
                .as_f64()
                .filter(|v| v.is_finite() && *v > 0.0)
                .is_none()
        {
            return Err(StoreError("chooseBudget"));
        }
    }
    if !b["taskTokens"].is_null() && b["taskTokens"].as_u64().filter(|v| *v > 0).is_none() {
        return Err(StoreError("chooseBudget"));
    }
    if !matches!(s["reviewLevel"].as_str(), Some("milestones" | "everyStep")) {
        return Err(StoreError("invalidReviewLevel"));
    }
    if !matches!(s["maxFixRounds"].as_u64(), Some(0..=3)) {
        return Err(StoreError("invalidFixRounds"));
    }
    Ok(())
}
fn normalize_phases(phases: &[Value]) -> Result<Vec<Value>, StoreError> {
    if phases.is_empty() || phases.len() > 1000 {
        return Err(StoreError("planPhaseInvalid"));
    }
    let mut ids = HashSet::new();
    phases.iter().map(|phase| {
        let id = required_str(phase, "id", "planPhaseInvalid")?;
        if !crate::config::valid_id(id) || !ids.insert(id) { return Err(StoreError("planPhaseInvalid")); }
        let title = required_str(phase, "title", "planPhaseInvalid")?;
        let milestone = phase.get("milestoneId").map(|v| v.as_str().ok_or(StoreError("planPhaseInvalid"))).transpose()?.unwrap_or("");
        let risky = phase.get("risky").map(|v| v.as_bool().ok_or(StoreError("planPhaseInvalid"))).transpose()?.unwrap_or(false);
        Ok(json!({"id":id,"title":title,"milestoneId":milestone,"risky":risky,"accepted":false}))
    }).collect()
}
fn normalize_task(t: &Value) -> Result<Value, StoreError> {
    required_str(t, "id", "invalidPlan")?;
    required_str(t, "title", "planTaskTitleRequired")?;
    for key in ["phaseId", "roleId", "repoId", "serverId"] {
        required_str(t, key, "invalidPlan")?;
    }
    if t["status"].as_str().unwrap_or("queued") != "queued"
        || t["steps"].as_u64().unwrap_or(0) != 0
        || t["tokens"].as_u64().unwrap_or(0) != 0
        || t["findings"].as_array().is_some_and(|v| !v.is_empty())
    {
        return Err(StoreError("invalidPlan"));
    }
    let mut out = json!({"id":"","title":"","phaseId":"","roleId":"worker","repoId":"","serverId":"phone","status":"queued","dependsOn":[],"criteria":[],"branch":"","reason":"","changedAt":now(),"steps":0,"tokens":0,"fixRounds":0,"affected":false,"findings":[],"messages":[],"diff":"","criterionResults":[],"usageReported":false});
    for key in [
        "id",
        "title",
        "phaseId",
        "roleId",
        "repoId",
        "serverId",
        "dependsOn",
        "criteria",
    ] {
        if let Some(v) = t.get(key) {
            out[key] = v.clone();
        }
    }
    if out["criteria"]
        .as_array()
        .filter(|a| {
            !a.is_empty()
                && a.iter()
                    .all(|v| v.as_str().is_some_and(|s| !s.trim().is_empty()))
        })
        .is_none()
        || out["dependsOn"]
            .as_array()
            .filter(|a| a.iter().all(Value::is_string))
            .is_none()
    {
        return Err(StoreError("invalidPlan"));
    }
    Ok(out)
}
fn job(w: &Value, p: &Value, task: Option<&Value>) -> Value {
    let role_id = task
        .map(|t| t["roleId"].as_str().unwrap_or("worker"))
        .unwrap_or("planner");
    let role = w["roles"]
        .as_array()
        .and_then(|rs| rs.iter().find(|r| r["id"] == role_id))
        .cloned()
        .unwrap_or(json!({}));
    let repo = task
        .and_then(|t| {
            p["repos"]
                .as_array()
                .and_then(|rs| rs.iter().find(|r| r["id"] == t["repoId"]))
        })
        .or_else(|| p["repos"].as_array().and_then(|rs| rs.first()))
        .cloned()
        .unwrap_or(json!({}));
    json!({"id":new_id("job"),"kind":if task.is_some(){"task"}else{"planner"},"projectId":p["id"],"taskId":task.map(|t|t["id"].clone()).unwrap_or(json!("")),"repoId":repo["id"],"serverId":repo["serverId"],"roleId":role_id,"model":role["model"].as_str().unwrap_or(""),"fallbackModel":role["fallbackModel"].as_str().unwrap_or(""),"instructions":role["instructions"].as_str().unwrap_or(""),"readOnly":task.is_none() || role["readOnly"]==true,"planningRoles":w["roles"].as_array().map(|roles| roles.iter().filter(|r| r["readOnly"] != true && r["id"] != "planner" && r["id"] != "checker").cloned().collect::<Vec<_>>()).unwrap_or_default(),"checkerRole":w["roles"].as_array().and_then(|roles| roles.iter().find(|role| role["id"]=="checker")).cloned().unwrap_or(json!({})),"title":task.map(|t|t["title"].clone()).unwrap_or(p["specDraft"]["goal"].clone()),"spec":p["specDraft"],"criteria":task.map(|t|t["criteria"].clone()).unwrap_or(json!([])),"dependsOn":task.map(|t|t["dependsOn"].clone()).unwrap_or(json!([])),"stage":"queued","directory":null,"sessionIds":{},"sessionUsage":{},"expectedDevCommit":repo["devCommit"].as_str().unwrap_or(""),"expectedMainCommit":repo["mainCommit"].as_str().unwrap_or(""),"usage":{"cost":null,"tokens":null},"createdAt":now(),"updatedAt":now()})
}
fn append_job_timeline(project: &mut Value, job: &Value) -> Result<(), StoreError> {
    let planner = job["kind"] == "planner";
    let subject = if planner { "Planning" } else { "Work" };
    let stage = job["stage"].as_str().unwrap_or("");
    let reason = job["reason"].as_str().unwrap_or("");
    // Only canned application-authored wording enters timeline copy. The typed
    // reason remains separate in planningState; no model, path or error text.
    let suffix = if stage == "stopped" {
        "is stopped."
    } else if stage == "paused" {
        "is paused."
    } else if stage == "interrupted"
        && !matches!(
            reason,
            "restartNeedsReconciliation" | "pauseNeedsReconciliation" | "serverRestarted"
        )
    {
        "was interrupted and needs review before resuming."
    } else {
        match reason {
            "chatBusy" => "is waiting for your chat reply to finish.",
            "chatStatusUnknown" | "chatStateUnknown" => "is waiting for chat status to be checked.",
            "executionUnavailable"
            | "boundaryUnverified"
            | "Protected execution is not verified" => {
                "is waiting for protected execution to be verified."
            }
            "protocolUnverified" | "serverOffline" => {
                "is waiting for the phone server to be checked."
            }
            "laneCap" => "is waiting for a free lane.",
            "dependencyPending" => "is waiting for another task to finish.",
            "checkerFindings" => "has checker findings that need review.",
            "restartNeedsReconciliation" => {
                "was interrupted when the app stopped. Resume to check its existing session."
            }
            "pauseNeedsReconciliation" => "is paused. Resume to check its existing session.",
            "serverRestarted" => {
                "was cut off when OpenCode restarted. Resume to send that step again."
            }
            "restartResend" => {
                "was cut off when OpenCode restarted. The team is sending that step again."
            }
            "" => match stage {
                "queued" => "is queued.",
                "starting" | "preparing" | "submitting" => "is starting.",
                "running" | "working" | "planning" => "is running.",
                "checking" => "is checking acceptance criteria.",
                "collecting" | "merging" => "is preparing the dev merge.",
                "completed" if planner => "finished. The plan is ready to review.",
                "completed" | "merged" => "finished.",
                "resuming" => "is checking its existing session before resuming.",
                "paused" => "is paused.",
                "stopped" => "is stopped.",
                "interrupted" => "was interrupted and needs review before resuming.",
                _ => "status changed.",
            },
            _ => "is waiting for an execution check.",
        }
    };
    if project["timeline"].is_null() {
        project["timeline"] = json!([]);
    }
    let timeline = project["timeline"]
        .as_array_mut()
        .ok_or(StoreError("storageCorrupt"))?;
    timeline.push(json!({"id":new_id("timeline"),"kind":if planner {"planner"} else {"job"},"text":format!("{subject} {suffix}"),"actor":"engine","at":job["updatedAt"],"taskId":job["taskId"].as_str().unwrap_or("")}));
    let truncated = timeline.len() > 500;
    if truncated {
        timeline.drain(..timeline.len() - 500);
        project["timelineTruncated"] = json!(true);
    }
    Ok(())
}
fn validate_plan(w: &Value, p: &Value) -> Result<(), StoreError> {
    let tasks = p["tasks"]
        .as_array()
        .filter(|t| !t.is_empty() && t.len() <= 1000)
        .ok_or(StoreError("invalidPlan"))?;
    let mut ids = HashSet::new();
    for t in tasks {
        let id = required_str(t, "id", "invalidPlan")?;
        if !ids.insert(id) {
            return Err(StoreError("invalidPlan"));
        }
        normalize_task(t)?;
        for (collection, key) in [
            (&p["repos"], "repoId"),
            (&w["roles"], "roleId"),
            (&w["servers"], "serverId"),
            (&p["phases"], "phaseId"),
        ] {
            if !collection
                .as_array()
                .is_some_and(|a| a.iter().any(|v| v["id"] == t[key]))
            {
                return Err(StoreError("invalidPlan"));
            }
        }
        let repo = p["repos"]
            .as_array()
            .unwrap()
            .iter()
            .find(|r| r["id"] == t["repoId"])
            .unwrap();
        if repo["serverId"] != t["serverId"] {
            return Err(StoreError("invalidPlacement"));
        }
    }
    fn visit<'a>(
        id: &'a str,
        tasks: &'a [Value],
        seen: &mut HashSet<&'a str>,
        visiting: &mut HashSet<&'a str>,
    ) -> Result<(), StoreError> {
        if seen.contains(id) {
            return Ok(());
        }
        if !visiting.insert(id) {
            return Err(StoreError("dependencyCycle"));
        }
        let t = tasks
            .iter()
            .find(|t| t["id"] == id)
            .ok_or(StoreError("missingDependency"))?;
        for dep in t["dependsOn"].as_array().ok_or(StoreError("invalidPlan"))? {
            visit(
                dep.as_str().ok_or(StoreError("invalidPlan"))?,
                tasks,
                seen,
                visiting,
            )?;
        }
        visiting.remove(id);
        seen.insert(id);
        Ok(())
    }
    let mut seen = HashSet::new();
    let mut visiting = HashSet::new();
    for id in ids {
        visit(id, tasks, &mut seen, &mut visiting)?;
    }
    Ok(())
}

fn apply_command(
    w: &mut Value,
    jobs: &mut Vec<Value>,
    c: &Value,
) -> Result<(String, u64), StoreError> {
    let action = required_str(c, "action", "invalidCommand")?;
    // No simulation, execution, merge, or promotion can be smuggled through the store.
    let supported = [
        "updateDefaults",
        "saveRole",
        "deleteRole",
        "updateServer",
        "createProject",
        "createQuickTask",
        "saveSpecDraft",
        "approveSpec",
        "approvePlan",
        "retryPlan",
        "acceptPhase",
        "acceptMilestone",
        "updateSettings",
        "pauseProject",
        "resumeProject",
        "stopProject",
        "pauseTask",
        "resumeTask",
        "stopTask",
        "acknowledgeDigest",
        "deleteProject",
    ];
    if !supported.contains(&action) {
        return Err(StoreError("unsupportedAction"));
    }
    let id = c["projectId"].as_str().unwrap_or("");
    let result: (String, u64);
    match action {
        "updateDefaults" => {
            validate_settings(&c["settings"])?;
            w["defaultSettings"] = c["settings"].clone();
            result = (String::new(), w["revision"].as_u64().unwrap_or(0) + 1);
        }
        "saveRole" => {
            let role = &c["role"];
            let rid = required_str(role, "id", "invalidRole")?;
            required_str(role, "name", "invalidRole")?;
            if matches!(rid, "checker" | "planner") && role["readOnly"] != true {
                return Err(StoreError("checkerMustBeReadOnly"));
            }
            if !role["instructions"].is_string()
                || !role["model"].is_string()
                || !role["fallbackModel"].is_string()
                || !role["readOnly"].is_boolean()
            {
                return Err(StoreError("invalidRole"));
            }
            let roles = w["roles"]
                .as_array_mut()
                .ok_or(StoreError("storageCorrupt"))?;
            roles.retain(|r| r["id"] != rid);
            roles.push(role.clone());
            result = (String::new(), w["revision"].as_u64().unwrap_or(0) + 1);
        }
        "deleteRole" => {
            let target = required_str(c, "targetId", "invalidRole")?;
            if matches!(target, "planner" | "checker")
                || jobs.iter().any(|j| j["roleId"] == target)
                || w["projects"].as_array().unwrap().iter().any(|p| {
                    p["tasks"]
                        .as_array()
                        .unwrap()
                        .iter()
                        .any(|t| t["roleId"] == target)
                })
            {
                return Err(StoreError("roleInUse"));
            }
            w["roles"]
                .as_array_mut()
                .unwrap()
                .retain(|r| r["id"] != target);
            result = (String::new(), w["revision"].as_u64().unwrap_or(0) + 1);
        }
        "updateServer" => {
            let server = &c["server"];
            let sid = required_str(server, "id", "invalidServer")?;
            if sid != "phone"
                || server["phone"] != true
                || !matches!(server["laneCap"].as_u64(), Some(1..=32))
            {
                return Err(StoreError("unsupportedServer"));
            }
            w["servers"] = json!([server]);
            result = (String::new(), w["revision"].as_u64().unwrap_or(0) + 1);
        }
        "createProject" | "createQuickTask" => {
            required_str(c, "name", "missingProjectDetails")?;
            validate_settings(&c["settings"])?;
            let spec = normalize_spec(&c["spec"])?;
            let repos = c["repos"]
                .as_array()
                .filter(|r| !r.is_empty() && r.len() <= 32)
                .ok_or(StoreError("missingProjectDetails"))?;
            let mut repo_ids = HashSet::new();
            for r in repos {
                let rid = required_str(r, "id", "invalidPlacement")?;
                required_str(r, "path", "invalidPlacement")?;
                if !repo_ids.insert(rid) || r["serverId"] != "phone" {
                    return Err(StoreError("invalidPlacement"));
                }
            }
            let pid = new_id("project");
            let quick = action == "createQuickTask";
            let mut p = json!({"id":pid,"name":c["name"],"status":"spec","revision":0,"settings":c["settings"],"repos":repos,"specDraft":spec,"specVersions":[],"phases":[],"tasks":[],"requests":[],"mergeQueue":[],"receipts":[],"timeline":[],"spent":0.0,"spentToday":0.0,"spendDay":"","digestReadAt":"","updatedAt":now(),"planApproved":false,"quickTask":quick,"usageReported":false,"simulated":false,"budgetWarning":false});
            if quick {
                if repos.len() != 1 {
                    return Err(StoreError("quickTaskNeedsOneRepo"));
                }
                let rid = c["roleId"]
                    .as_str()
                    .filter(|s| !s.is_empty())
                    .unwrap_or("worker");
                if !w["roles"]
                    .as_array()
                    .unwrap()
                    .iter()
                    .any(|r| r["id"] == rid)
                {
                    return Err(StoreError("roleNotFound"));
                }
                if c["serverId"]
                    .as_str()
                    .is_some_and(|s| !s.is_empty() && s != "phone")
                {
                    return Err(StoreError("unsupportedServer"));
                }
                let phase_id = new_id("phase");
                let task_id = new_id("task");
                let criteria = p["specDraft"]["milestones"]
                    .as_array()
                    .unwrap()
                    .iter()
                    .filter_map(|m| m["criteria"].as_array())
                    .flat_map(|a| a.iter().cloned())
                    .collect::<Vec<_>>();
                if criteria.is_empty() {
                    return Err(StoreError("missingCriteria"));
                }
                let task = normalize_task(
                    &json!({"id":task_id,"title":p["specDraft"]["goal"],"phaseId":phase_id,"roleId":rid,"repoId":repos[0]["id"],"serverId":"phone","criteria":criteria,"dependsOn":[]}),
                )?;
                p["phases"] = json!([{"id":phase_id,"milestoneId":"","title":p["specDraft"]["goal"],"risky":false,"accepted":false}]);
                p["tasks"] = json!([task]);
                p["specDraft"]["approvedBy"] = json!("person");
                p["specDraft"]["approvedAt"] = json!(now());
                p["specVersions"] = json!([p["specDraft"].clone()]);
                p["status"] = json!("needsPlanApproval");
                if c["confirmed"] == true {
                    validate_plan(w, &p)?;
                    p["planApproved"] = json!(true);
                    p["status"] = json!("running");
                    jobs.push(job(w, &p, Some(&p["tasks"][0])));
                }
            }
            w["projects"].as_array_mut().unwrap().push(p);
            result = (pid, 0);
        }
        _ => {
            let index = w["projects"]
                .as_array()
                .unwrap()
                .iter()
                .position(|p| p["id"] == id)
                .ok_or(StoreError("projectNotFound"))?;
            let mut p = w["projects"][index].clone();
            if c["expectedRevision"].as_u64() != p["revision"].as_u64() {
                return Err(StoreError("staleRevision"));
            }
            if action == "deleteProject" {
                if c["confirmed"] != true {
                    return Err(StoreError("confirmationRequired"));
                }
                jobs.retain(|j| j["projectId"] != id);
                w["projects"].as_array_mut().unwrap().remove(index);
                result = (id.to_owned(), p["revision"].as_u64().unwrap_or(0) + 1);
            } else {
                match action {
                    "saveSpecDraft" => {
                        if jobs.iter().any(|j| {
                            j["projectId"] == id
                                && crate::scheduler::active_stage(j["stage"].as_str().unwrap_or(""))
                        }) {
                            return Err(StoreError("projectBusy"));
                        }
                        let mut spec = normalize_spec(&c["spec"])?;
                        spec["version"] = json!(p["specVersions"].as_array().unwrap().len() + 1);
                        p["specDraft"] = spec;
                        p["planApproved"] = json!(false);
                        p["status"] = json!("spec");
                        for j in jobs.iter_mut().filter(|j| {
                            j["projectId"] == id
                                && !matches!(j["stage"].as_str(), Some("completed" | "merged"))
                        }) {
                            j["stage"] = json!("stopped");
                        }
                        for t in p["tasks"].as_array_mut().unwrap() {
                            t["affected"] = json!(t["status"] != "merged");
                        }
                    }
                    "approveSpec" => {
                        if jobs.iter().any(|j| {
                            j["projectId"] == id
                                && matches!(
                                    j["stage"].as_str(),
                                    Some(
                                        "queued"
                                            | "starting"
                                            | "planning"
                                            | "running"
                                            | "checking"
                                            | "interrupted"
                                    )
                                )
                        }) {
                            return Err(StoreError("projectBusy"));
                        }
                        let mut spec = normalize_spec(&p["specDraft"])?;
                        spec["version"] = json!(p["specVersions"].as_array().unwrap().len() + 1);
                        spec["approvedBy"] = json!("person");
                        spec["approvedAt"] = json!(now());
                        p["specDraft"] = spec.clone();
                        p["specVersions"].as_array_mut().unwrap().push(spec);
                        p["status"] = json!("planning");
                        p["planApproved"] = json!(false);
                        jobs.push(job(w, &p, None));
                    }
                    "retryPlan" => {
                        if p["status"] == "stopped" {
                            return Err(StoreError("projectStopped"));
                        }
                        if matches!(p["status"].as_str(), Some("paused" | "pausedBudget")) {
                            return Err(StoreError("projectPaused"));
                        }
                        if p["planApproved"] == true {
                            return Err(StoreError("planAlreadyApproved"));
                        }
                        if p["specVersions"]
                            .as_array()
                            .and_then(|versions| versions.last())
                            != Some(&p["specDraft"])
                            || p["specDraft"]["approvedBy"] != "person"
                            || p["specDraft"]["approvedAt"]
                                .as_str()
                                .is_none_or(str::is_empty)
                        {
                            return Err(StoreError("approveSpecFirst"));
                        }
                        let in_flight = |j: &Value| {
                            crate::scheduler::active_stage(j["stage"].as_str().unwrap_or(""))
                                || matches!(j["stage"].as_str(), Some("queued" | "mergeReady"))
                        };
                        if jobs
                            .iter()
                            .any(|j| j["projectId"] == id && j["kind"] == "planner" && in_flight(j))
                        {
                            return Err(StoreError("plannerBusy"));
                        }
                        if jobs
                            .iter()
                            .any(|j| j["projectId"] == id && j["kind"] == "task" && in_flight(j))
                        {
                            return Err(StoreError("tasksBusy"));
                        }
                        let latest = jobs
                            .iter()
                            .rposition(|j| j["projectId"] == id && j["kind"] == "planner")
                            .ok_or(StoreError("planningRetryUnavailable"))?;
                        if !matches!(
                            jobs[latest]["stage"].as_str(),
                            Some("failed" | "interrupted")
                        ) {
                            return Err(StoreError("planningRetryUnavailable"));
                        }
                        if matches!(
                            jobs[latest]["reason"].as_str(),
                            Some("restartNeedsReconciliation" | "pauseNeedsReconciliation")
                        ) {
                            return Err(StoreError("planningNeedsReconciliation"));
                        }
                        let prior = &jobs[latest];
                        let reason = prior["reason"].as_str().unwrap_or("");
                        let dispatch = &prior["promptDispatch"];
                        let no_dispatch = dispatch.is_null()
                            || dispatch.as_object().is_some_and(|d| d.is_empty());
                        let never_started = prior["sessionIds"]
                            .as_object()
                            .is_some_and(|ids| ids.is_empty())
                            && no_dispatch;
                        let completed_invalid_proposal = matches!(
                            reason,
                            "structuredOutputInvalid"
                                | "planInvalid"
                                | "planTaskTitleRequired"
                                | "invalidPlan"
                                | "planPhaseInvalid"
                                | "invalidPlacement"
                                | "dependencyCycle"
                                | "missingDependency"
                        ) && dispatch["planner"] == "dispatched";
                        let ambiguous = matches!(
                            reason,
                            "promptUncertain" | "transport_uncertain" | "sessionCreateUncertain"
                        ) || (!dispatch.is_null() && !dispatch.is_object())
                            || dispatch.as_object().is_some_and(|d| {
                                d.values()
                                    .any(|state| !matches!(state.as_str(), Some("dispatched")))
                            });
                        // OC2 reports this only after a matching terminal failed
                        // turn proves provider authentication was refused. A
                        // model error beside uncertain dispatch is not proof.
                        let terminal_model_failure = reason == "modelNotConfigured"
                            && dispatch["planner"] == "dispatched"
                            && prior["sessionIds"]["planner"]
                                .as_str()
                                .is_some_and(|id| !id.is_empty());
                        if reason != "sessionFailed"
                            && (ambiguous
                                || (!never_started
                                    && !completed_invalid_proposal
                                    && !terminal_model_failure))
                        {
                            return Err(StoreError("planningNeedsReconciliation"));
                        }
                        // This explicit request authorizes a new proposal, not
                        // replaying the old prompt or changing the approved spec.
                        p["tasks"] = json!([]);
                        p["phases"] = json!([]);
                        p["status"] = json!("planning");
                        let fresh = job(w, &p, None);
                        jobs[latest]["stage"] = json!("stopped");
                        jobs[latest]["retiredBy"] = fresh["id"].clone();
                        jobs[latest]["updatedAt"] = json!(now());
                        jobs.push(fresh);
                    }
                    "approvePlan" => {
                        if p["specVersions"].as_array().unwrap().is_empty() {
                            return Err(StoreError("approveSpecFirst"));
                        }
                        if jobs.iter().any(|j| {
                            j["projectId"] == id
                                && ((j["kind"] == "task"
                                    && !matches!(
                                        j["stage"].as_str(),
                                        Some("completed" | "merged" | "stopped")
                                    ))
                                    || (j["kind"] == "planner"
                                        && !matches!(
                                            j["stage"].as_str(),
                                            Some("completed" | "stopped")
                                        )))
                        }) {
                            return Err(StoreError("planAlreadyRunning"));
                        }
                        if let Some(tasks) = c.get("tasks").filter(|v| !v.is_null()) {
                            let mut normalized = Vec::new();
                            for t in tasks.as_array().ok_or(StoreError("invalidPlan"))? {
                                normalized.push(normalize_task(t)?);
                            }
                            p["tasks"] = json!(normalized);
                        }
                        if let Some(phases) = c.get("phases").filter(|v| !v.is_null()) {
                            if !phases.is_array() {
                                return Err(StoreError("invalidPlan"));
                            }
                            p["phases"] = json!(normalize_phases(
                                phases.as_array().ok_or(StoreError("planPhaseInvalid"))?
                            )?);
                        }
                        validate_plan(w, &p)?;
                        if jobs.iter().any(|j| {
                            j["projectId"] == id
                                && j["kind"] == "task"
                                && p["tasks"]
                                    .as_array()
                                    .unwrap()
                                    .iter()
                                    .any(|t| t["id"] == j["taskId"])
                        }) {
                            return Err(StoreError("taskIdAlreadyUsed"));
                        }
                        p["planApproved"] = json!(true);
                        p["status"] = json!("running");
                        for t in p["tasks"].as_array().unwrap() {
                            jobs.push(job(w, &p, Some(t)));
                        }
                    }
                    "acceptPhase" | "acceptMilestone" => {
                        if p["status"] == "stopped" {
                            return Err(StoreError("projectStopped"));
                        }
                        let target = required_str(
                            c,
                            "targetId",
                            if action == "acceptPhase" {
                                "phaseNotFound"
                            } else {
                                "milestoneNotFound"
                            },
                        )?;
                        if action == "acceptPhase" {
                            let index = p["phases"]
                                .as_array()
                                .ok_or(StoreError("storageCorrupt"))?
                                .iter()
                                .position(|phase| phase["id"] == target)
                                .ok_or(StoreError("phaseNotFound"))?;
                            let tasks: Vec<&Value> = p["tasks"]
                                .as_array()
                                .ok_or(StoreError("storageCorrupt"))?
                                .iter()
                                .filter(|task| task["phaseId"] == target)
                                .collect();
                            if p["planApproved"] != true
                                || tasks.is_empty()
                                || tasks.iter().any(|task| task["status"] != "merged")
                            {
                                return Err(StoreError("phaseNotReady"));
                            }
                            p["phases"][index]["accepted"] = json!(true);
                        } else {
                            let index = p["specDraft"]["milestones"]
                                .as_array()
                                .ok_or(StoreError("storageCorrupt"))?
                                .iter()
                                .position(|milestone| milestone["id"] == target)
                                .ok_or(StoreError("milestoneNotFound"))?;
                            let phases: Vec<&Value> = p["phases"]
                                .as_array()
                                .ok_or(StoreError("storageCorrupt"))?
                                .iter()
                                .filter(|phase| phase["milestoneId"] == target)
                                .collect();
                            let tasks: Vec<&Value> = p["tasks"]
                                .as_array()
                                .ok_or(StoreError("storageCorrupt"))?
                                .iter()
                                .filter(|task| {
                                    phases.iter().any(|phase| task["phaseId"] == phase["id"])
                                })
                                .collect();
                            if p["planApproved"] != true
                                || phases.is_empty()
                                || phases.iter().any(|phase| phase["accepted"] != true)
                                || tasks.is_empty()
                                || tasks.iter().any(|task| task["status"] != "merged")
                            {
                                return Err(StoreError("milestoneNotReady"));
                            }
                            p["specDraft"]["milestones"][index]["accepted"] = json!(true);
                        }
                        // Acceptance is the person's review decision only. It
                        // cannot promote main or fabricate project completion.
                    }
                    "updateSettings" => {
                        validate_settings(&c["settings"])?;
                        p["settings"] = c["settings"].clone();
                    }
                    "pauseProject" => {
                        if p["status"] == "stopped" {
                            return Err(StoreError("projectStopped"));
                        }
                        p["status"] = json!("paused");
                        for j in jobs.iter_mut().filter(|j| j["projectId"] == id) {
                            pause_job(j);
                        }
                        sync_task_stages(&mut p, jobs);
                    }
                    "resumeProject" => {
                        if p["status"] == "stopped" {
                            return Err(StoreError("projectStopped"));
                        }
                        for j in jobs.iter_mut().filter(|j| {
                            j["projectId"] == id
                                && matches!(j["stage"].as_str(), Some("paused" | "interrupted"))
                        }) {
                            resume_job_checkpoint(j)?;
                        }
                        p["status"] = json!(if p["planApproved"] == true {
                            "running"
                        } else if jobs.iter().any(|j| j["projectId"] == id
                            && j["kind"] == "planner"
                            && j["stage"] != "completed")
                        {
                            "planning"
                        } else {
                            "needsPlanApproval"
                        });
                        sync_task_stages(&mut p, jobs);
                    }
                    "stopProject" => {
                        if c["confirmed"] != true {
                            return Err(StoreError("confirmationRequired"));
                        }
                        p["status"] = json!("stopped");
                        for j in jobs.iter_mut().filter(|j| {
                            j["projectId"] == id
                                && !matches!(j["stage"].as_str(), Some("completed" | "merged"))
                        }) {
                            j["stage"] = json!("stopped");
                        }
                        sync_task_stages(&mut p, jobs);
                    }
                    "pauseTask" | "resumeTask" | "stopTask" => {
                        let target = required_str(c, "targetId", "taskNotFound")?;
                        let j = jobs
                            .iter_mut()
                            .find(|j| j["projectId"] == id && j["taskId"] == target)
                            .ok_or(StoreError("taskNotFound"))?;
                        match action {
                            "pauseTask" => pause_job(j),
                            "resumeTask" => {
                                resume_job_checkpoint(j)?;
                                if p["status"] == "interrupted" {
                                    p["status"] = json!(if p["planApproved"] == true {
                                        "running"
                                    } else {
                                        "planning"
                                    });
                                }
                            }
                            _ => {
                                if c["confirmed"] != true {
                                    return Err(StoreError("confirmationRequired"));
                                }
                                if matches!(j["stage"].as_str(), Some("completed" | "merged")) {
                                    return Err(StoreError("taskAlreadyDone"));
                                }
                                j["stage"] = json!("stopped");
                            }
                        }
                        sync_task_stages(&mut p, jobs);
                    }
                    "acknowledgeDigest" => p["digestReadAt"] = json!(now()),
                    _ => return Err(StoreError("unsupportedAction")),
                }
                increment_project(&mut p);
                result = (id.to_owned(), p["revision"].as_u64().unwrap_or(0));
                w["projects"][index] = p;
            }
        }
    }
    w["revision"] = json!(w["revision"].as_u64().unwrap_or(0) + 1);
    Ok(result)
}
fn pause_job(j: &mut Value) {
    if j["stage"] == "queued" {
        j["stage"] = json!("paused");
    } else if crate::scheduler::active_stage(j["stage"].as_str().unwrap_or("")) {
        let stage = j["stage"].as_str().unwrap_or("").to_owned();
        checkpoint_interruption(j, &stage);
        j["stage"] = json!("interrupted");
        j["reason"] = json!("pauseNeedsReconciliation");
    }
}
fn checkpoint_interruption(j: &mut Value, stage: &str) {
    // Repeated process deaths while reconciling must retain the original stage.
    if stage != "resuming" {
        j["resumeStage"] = json!(stage);
    }
}
fn resume_job_checkpoint(j: &mut Value) -> Result<(), StoreError> {
    if !matches!(j["stage"].as_str(), Some("paused" | "interrupted")) {
        return Err(StoreError("taskNotPaused"));
    }
    let sessions = j["sessionIds"]
        .as_object()
        .ok_or(StoreError("needsReconciliation"))?;
    let dispatches = j.get("promptDispatch").and_then(Value::as_object);
    if j.get("promptDispatch")
        .is_some_and(|v| !v.is_null() && !v.is_object())
    {
        return Err(StoreError("needsReconciliation"));
    }
    if sessions.is_empty() {
        // No recorded session is safe only before dispatch, and never after an
        // uncertain session creation (an unrecorded session may already exist).
        let safe_stage = j["stage"] == "paused"
            || matches!(
                j["resumeStage"].as_str(),
                Some("queued" | "starting" | "preparing")
            );
        if !safe_stage
            || dispatches.is_some_and(|d| !d.is_empty())
            || matches!(
                j["reason"].as_str(),
                Some("promptUncertain" | "sessionCreateUncertain")
            )
        {
            return Err(StoreError("needsReconciliation"));
        }
        j["stage"] = json!("queued");
    } else {
        let role = if sessions.contains_key("checker") {
            "checker"
        } else if j["kind"] == "planner" {
            "planner"
        } else {
            "worker"
        };
        if sessions
            .get(role)
            .and_then(Value::as_str)
            .filter(|s| !s.is_empty())
            .is_none()
            || j["directory"].as_str().filter(|s| !s.is_empty()).is_none()
            || !matches!(
                j["resumeStage"].as_str(),
                Some(
                    "starting"
                        | "planning"
                        | "running"
                        | "working"
                        | "checking"
                        | "mergeReady"
                        | "merging"
                        | "submitting"
                )
            )
            || !dispatches
                .and_then(|d| d.get(role))
                .is_some_and(|state| matches!(state.as_str(), Some("dispatching" | "dispatched")))
            || (role == "checker" && j["taskCommit"].as_str().filter(|s| !s.is_empty()).is_none())
        {
            return Err(StoreError("needsReconciliation"));
        }
        if j["reason"] == "serverRestarted" {
            // The engine saw this turn die under a newly proven OpenCode
            // process (a restart or crash relaunch), after its prompt was
            // accepted. The session is not running and did not finish, so
            // one new round may send the step again into the same session.
            // The round is consumed here, durably, before anything is sent.
            if dispatches.and_then(|d| d.get(role)) != Some(&json!("dispatched"))
                || j["restartDispatch"][role] == "dispatching"
            {
                return Err(StoreError("needsReconciliation"));
            }
            let rounds = j["restartResends"][role].as_u64().unwrap_or(0);
            if !j["restartResends"].is_object() {
                j["restartResends"] = json!({});
            }
            j["restartResends"][role] = json!(rounds + 1);
            if let Some(ledger) = j["restartDispatch"].as_object_mut() {
                ledger.remove(role);
            }
            j["restartResend"] = json!(role);
        }
        // The daemon observes this exact session; resuming never calls prompt
        // again, including when its acknowledgment was lost. The only resend
        // is the restart round above, on its own dispatch ledger.
        j["stage"] = json!("resuming");
    }
    j["reason"] = json!("");
    j["updatedAt"] = json!(now());
    Ok(())
}
/// How many times the engine sends a step again by itself after OpenCode
/// restarted under it. Past that, the person decides (Resume stays offered).
pub(crate) const AUTO_RESTART_RESENDS: u64 = 2;
/// The role whose turn a resume observes, as [resume_job_checkpoint] picks it.
fn checkpoint_role(j: &Value) -> &'static str {
    if j["sessionIds"].get("checker").is_some() {
        "checker"
    } else if j["kind"] == "planner" {
        "planner"
    } else {
        "worker"
    }
}
fn restart_cut_resumable(j: &Value) -> bool {
    j["reason"] == "serverRestarted"
        && j["restartResends"][checkpoint_role(j)]
            .as_u64()
            .unwrap_or(0)
            < AUTO_RESTART_RESENDS
}
fn sync_task_stages(p: &mut Value, jobs: &[Value]) {
    let id = p["id"].clone();
    for t in p["tasks"].as_array_mut().unwrap() {
        if let Some(j) = jobs
            .iter()
            .find(|j| j["projectId"] == id && j["taskId"] == t["id"])
        {
            t["status"] = json!(task_status(j["stage"].as_str().unwrap_or("")));
            t["reason"] = j["reason"].clone();
            t["changedAt"] = json!(now());
        }
    }
}
// A checker reports evidence, never the owner's finding resolution state.
// Preserve the complete authored finding except for that authority-bearing field.
fn open_checker_findings(value: &Value) -> Result<Value, StoreError> {
    let findings = value.as_array().ok_or(StoreError("invalidTaskPatch"))?;
    let mut published = Vec::with_capacity(findings.len());
    for finding in findings {
        let mut finding = finding
            .as_object()
            .ok_or(StoreError("invalidTaskPatch"))?
            .clone();
        finding.insert("status".into(), json!("open"));
        published.push(Value::Object(finding));
    }
    Ok(Value::Array(published))
}

fn task_status(stage: &str) -> &str {
    match stage {
        "starting" | "working" => "running",
        "checking" => "review",
        "mergeReady" => "checked",
        "completed" => "merged",
        _ => stage,
    }
}
fn valid_transition(from: &str, to: &str) -> bool {
    if from == to {
        return true;
    }
    if matches!(from, "completed" | "merged" | "stopped") {
        return false;
    }
    if matches!(to, "interrupted" | "failed" | "paused" | "stopped") {
        return true;
    }
    matches!(
        (from, to),
        ("queued", "starting" | "planning" | "preparing")
            | ("starting", "running" | "planning" | "working")
            | ("preparing", "submitting" | "running")
            | ("submitting", "running" | "working")
            | ("planning", "completed")
            | ("running" | "working", "checking" | "completed")
            | ("checking", "mergeReady" | "needsFix")
            | ("mergeReady", "merging")
            | ("merging", "completed")
            | ("interrupted", "resuming")
            | (
                "resuming",
                "running" | "checking" | "mergeReady" | "merging" | "completed"
            )
            | ("paused", "queued")
    )
}
fn update_usage(j: &mut Value, usage: &Value) -> Result<(), StoreError> {
    let fields = usage.as_object().ok_or(StoreError("invalidUsage"))?;
    for (key, value) in fields {
        match key.as_str() {
            "cost" | "dailyCost" => {
                if value.is_null() {
                    if key == "dailyCost" {
                        j["usage"][key] = Value::Null;
                    }
                    continue;
                }
                let cost = value
                    .as_f64()
                    .filter(|n| n.is_finite() && *n >= 0.0)
                    .ok_or(StoreError("invalidUsage"))?;
                if key == "cost" && j["usage"][key].as_f64().is_some_and(|old| old > cost) {
                    return Err(StoreError("usageRegression"));
                }
                j["usage"][key] = json!(cost);
            }
            "tokens" => {
                if value.is_null() {
                    continue;
                }
                let tokens = value.as_u64().ok_or(StoreError("invalidUsage"))?;
                if j["usage"][key].as_u64().is_some_and(|old| old > tokens) {
                    return Err(StoreError("usageRegression"));
                }
                j["usage"][key] = json!(tokens);
            }
            "day" => {
                let day = value
                    .as_str()
                    .filter(|d| {
                        d.len() == 10
                            && d.bytes().enumerate().all(|(i, b)| {
                                if i == 4 || i == 7 {
                                    b == b'-'
                                } else {
                                    b.is_ascii_digit()
                                }
                            })
                    })
                    .ok_or(StoreError("invalidUsage"))?;
                j["usage"][key] = json!(day);
            }
            _ => return Err(StoreError("invalidUsage")),
        }
    }
    Ok(())
}
fn aggregate_usage(w: &mut Value, jobs: &[Value]) {
    for p in w["projects"].as_array_mut().unwrap() {
        let relevant: Vec<_> = jobs.iter().filter(|j| j["projectId"] == p["id"]).collect();
        let total: Option<f64> = relevant.iter().map(|j| j["usage"]["cost"].as_f64()).sum();
        p["usageReported"] = json!(!relevant.is_empty() && total.is_some());
        if let Some(total) = total.filter(|v| v.is_finite()) {
            p["spent"] = json!(total);
        }
        let day = relevant
            .iter()
            .filter_map(|j| j["usage"]["day"].as_str())
            .max();
        if let Some(day) = day {
            let daily: Option<f64> = relevant
                .iter()
                .map(|j| {
                    if j["usage"]["day"] == day {
                        j["usage"]["dailyCost"].as_f64()
                    } else {
                        None
                    }
                })
                .sum();
            if let Some(daily) = daily.filter(|v| v.is_finite()) {
                p["spentToday"] = json!(daily);
                p["spendDay"] = json!(day);
            }
        }
    }
}

fn apply_repo_receipt(
    p: &mut Value,
    binding: &Value,
    receipt: &Value,
    kind: &str,
) -> Result<(), StoreError> {
    let repo_id = binding["repoId"]
        .as_str()
        .ok_or(StoreError("invalidRepositoryReceipt"))?;
    if receipt["repoId"] != repo_id {
        return Err(StoreError("invalidRepositoryReceipt"));
    }
    if kind == "merge" && receipt["taskId"] != binding["taskId"] {
        return Err(StoreError("invalidRepositoryReceipt"));
    }
    for side in ["before", "after"] {
        for key in ["devCommit", "mainCommit"] {
            required_str(&receipt[side], key, "invalidRepositoryReceipt")?;
        }
    }
    let repo = p["repos"]
        .as_array_mut()
        .ok_or(StoreError("storageCorrupt"))?
        .iter_mut()
        .find(|r| r["id"] == repo_id)
        .ok_or(StoreError("repoNotFound"))?;
    if repo["devCommit"] != receipt["before"]["devCommit"]
        || repo["mainCommit"] != receipt["before"]["mainCommit"]
    {
        return Err(StoreError("staleRepositoryRefs"));
    }
    if kind == "merge" && receipt["before"]["mainCommit"] != receipt["after"]["mainCommit"] {
        return Err(StoreError("invalidRepositoryReceipt"));
    }
    repo["devCommit"] = receipt["after"]["devCommit"].clone();
    repo["mainCommit"] = receipt["after"]["mainCommit"].clone();
    let ref_key = if kind == "promote" {
        "mainCommit"
    } else {
        "devCommit"
    };
    p["receipts"].as_array_mut().ok_or(StoreError("storageCorrupt"))?.push(json!({"id":binding["id"],"kind":kind,"repoId":repo_id,"taskId":binding["taskId"].as_str().unwrap_or(""),"before":receipt["before"][ref_key],"after":receipt["after"][ref_key],"at":now(),"actor":"engine","beforeRefs":receipt["before"],"afterRefs":receipt["after"]}));
    Ok(())
}

#[cfg(test)]
mod checker_publication_tests {
    use super::*;
    use std::path::PathBuf;

    fn storage() -> tempfile::TempDir {
        let root = std::env::var_os("OC_ENGINE_TEST_ROOT")
            .map(PathBuf::from)
            .unwrap_or_else(|| PathBuf::from("/home/eslam/Storage/tmp/oc-phone-engine-tests"));
        fs::create_dir_all(&root).unwrap();
        tempfile::Builder::new()
            .prefix("checker-publication-")
            .tempdir_in(root)
            .unwrap()
    }

    fn checking(store: &Store) -> String {
        let create = json!({"requestId":"create","action":"createQuickTask","confirmed":true,
            "name":"Finding publication","settings":{"mode":"single","maxLanes":1,"reviewLevel":"milestones","maxFixRounds":0,
                "chargingOnly":false,"budget":{"chosen":true,"unlimited":true}},
            "spec":{"goal":"Preserve findings","milestones":[{"id":"milestone","title":"Checked","criteria":["Behavior is verified"]}]},
            "repos":[{"id":"repo","serverId":"phone","path":"/workspace/source","devCommit":"seed","mainCommit":"seed"}]});
        assert_eq!(store.execute(&create).unwrap()["accepted"], true);
        let id = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
        for (before, after) in [
            ("queued", "starting"),
            ("starting", "running"),
            ("running", "checking"),
        ] {
            store
                .update_job(&id, before, &json!({"stage":after}))
                .unwrap();
        }
        id
    }

    #[test]
    fn authored_finding_statuses_publish_open_on_job_and_task_after_restart() {
        for status in ["met", "fixed", "ignored", "closed", "open"] {
            for nested in [false, true] {
                let root = storage();
                let store = Store::open(root.path(), "p").unwrap();
                let id = checking(&store);
                let authored = json!({"id":"finding","status":status,"severity":"minor","criterion":"Behavior is verified",
                    "location":"README.md:1","text":"Explanatory finding remains reviewable","extra":{"evidence":"Retain this"}});
                let mut expected = authored.clone();
                expected["status"] = json!("open");
                let criteria = store.jobs().unwrap()[0]["criteria"].clone();
                let results = json!([{"criterion":"Behavior is verified","status":"met","note":"Authored result"}]);
                let mut patch = json!({"stage":"needsFix","criterionResults":results});
                if nested {
                    patch["task"] = json!({"findings":[authored]});
                } else {
                    patch["findings"] = json!([authored]);
                }
                let updated = store.update_job(&id, "checking", &patch).unwrap();
                assert_eq!(updated["stage"], "needsFix");
                assert_eq!(updated["findings"], json!([expected.clone()]));
                assert_eq!(updated["reason"], "checkerFindings");
                drop(store);
                let store = Store::open(root.path(), "p").unwrap();
                let job = store.jobs().unwrap()[0].clone();
                let project = store.workspace().unwrap()["projects"][0].clone();
                let task = &project["tasks"][0];
                assert_eq!(job["stage"], "needsFix");
                assert_eq!(task["status"], "needsFix");
                assert_eq!(job["findings"], json!([expected.clone()]));
                assert_eq!(task["findings"], json!([expected]));
                assert_eq!(job["reason"], "checkerFindings");
                assert_eq!(task["reason"], "checkerFindings");
                assert_eq!(job["criteria"], criteria);
                assert_eq!(task["criteria"], criteria);
                assert_eq!(job["criterionResults"], results);
                assert_eq!(task["criterionResults"], results);
                assert_eq!(crate::daemon::validate_check(&job, &criteria), Ok(false));
                assert!(project["timeline"]
                    .as_array()
                    .unwrap()
                    .iter()
                    .any(|event| event["text"] == "Work has checker findings that need review."));
            }
        }
    }

    #[test]
    fn empty_successful_check_preserves_results_and_merge_ready_stage() {
        let root = storage();
        let store = Store::open(root.path(), "p").unwrap();
        let id = checking(&store);
        let results = json!([{"criterion":"Behavior is verified","status":"met"}]);
        let updated = store
            .update_job(
                &id,
                "checking",
                &json!({"stage":"mergeReady","findings":[],"criterionResults":results}),
            )
            .unwrap();
        assert_eq!(updated["stage"], "mergeReady");
        assert_eq!(updated["findings"], json!([]));
        assert_eq!(updated["criterionResults"], results);
        assert_ne!(updated["reason"], "checkerFindings");
        assert_eq!(
            crate::daemon::validate_check(&updated, &updated["criteria"]),
            Ok(true)
        );
        drop(store);
        let store = Store::open(root.path(), "p").unwrap();
        let task = store.workspace().unwrap()["projects"][0]["tasks"][0].clone();
        assert_eq!(task["status"], "checked");
        assert_eq!(task["findings"], json!([]));
        assert_eq!(task["criterionResults"], results);
        assert_ne!(task["reason"], "checkerFindings");
    }

    #[test]
    fn needs_fix_keeps_explicit_reason_and_defaults_absent_or_empty_reason() {
        for reason in [None, Some(""), Some("acceptanceUnmet")] {
            let root = storage();
            let store = Store::open(root.path(), "p").unwrap();
            let id = checking(&store);
            let mut patch = json!({"stage":"needsFix","findings":[],"criterionResults":[{"criterion":"Behavior is verified","status":"unmet"}]});
            if let Some(reason) = reason {
                patch["reason"] = json!(reason);
            }
            let updated = store.update_job(&id, "checking", &patch).unwrap();
            let expected = reason
                .filter(|reason| !reason.is_empty())
                .unwrap_or("checkerFindings");
            assert_eq!(updated["reason"], expected);
            assert_eq!(
                store.workspace().unwrap()["projects"][0]["tasks"][0]["reason"],
                expected
            );
        }
    }

    fn seed_reason_checkpoint(store: &Store, stage: &str, reason: &str) -> String {
        let id = checking(store);
        let mut conn = store.lock().unwrap();
        let tx = conn.transaction().unwrap();
        let mut workspace = load_workspace(&tx).unwrap();
        let mut jobs = load_jobs(&tx).unwrap();
        jobs[0]["stage"] = json!(stage);
        jobs[0]["reason"] = json!(reason);
        workspace["projects"][0]["tasks"][0]["status"] = json!(task_status(stage));
        workspace["projects"][0]["tasks"][0]["reason"] = json!(reason);
        persist(&tx, &workspace, &jobs).unwrap();
        tx.commit().unwrap();
        id
    }

    #[test]
    fn admission_reason_clear_reaches_task_without_clearing_blocked_or_usage_state() {
        for (blocked_stage, admitted_stage) in [("queued", "starting"), ("starting", "running")] {
            let root = storage();
            let store = Store::open(root.path(), "p").unwrap();
            let id = seed_reason_checkpoint(&store, blocked_stage, "chatStatusUnknown");
            let before = store.jobs().unwrap()[0].clone();
            let revision = store.workspace().unwrap()["projects"][0]["revision"].clone();
            let usage = store
                .update_job(
                    &id,
                    blocked_stage,
                    &json!({"usage":{"cost":0.0,"tokens":3}}),
                )
                .unwrap();
            assert_eq!(usage["reason"], "chatStatusUnknown");
            assert_eq!(
                store.workspace().unwrap()["projects"][0]["tasks"][0]["reason"],
                "chatStatusUnknown"
            );
            assert_eq!(
                store.workspace().unwrap()["projects"][0]["revision"],
                revision
            );
            let blocked = store
                .update_job(&id, blocked_stage, &json!({"stage":blocked_stage}))
                .unwrap();
            assert_eq!(blocked["reason"], "chatStatusUnknown");
            let admitted = store
                .update_job(&id, blocked_stage, &json!({"stage":admitted_stage}))
                .unwrap();
            assert_eq!(admitted["reason"], "");
            let conn = store.lock().unwrap();
            let raw = load_workspace(&conn).unwrap();
            assert_eq!(raw["projects"][0]["tasks"][0]["reason"], "");
            drop(conn);
            for key in [
                "sessionIds",
                "promptDispatch",
                "criteria",
                "findings",
                "criterionResults",
            ] {
                assert_eq!(admitted.get(key), before.get(key));
            }
            let interrupted = store
                .update_job(
                    &id,
                    admitted_stage,
                    &json!({"stage":"interrupted","reason":"sessionUnknown"}),
                )
                .unwrap();
            assert_eq!(interrupted["reason"], "sessionUnknown");
            assert_eq!(
                store.workspace().unwrap()["projects"][0]["tasks"][0]["reason"],
                "sessionUnknown"
            );
        }
        let root = storage();
        let store = Store::open(root.path(), "p").unwrap();
        let id = seed_reason_checkpoint(&store, "queued", "chatStatusUnknown");
        let updated = store
            .update_job(
                &id,
                "queued",
                &json!({"stage":"starting","task":{"reason":"needsOwnerReview"}}),
            )
            .unwrap();
        assert_eq!(updated["reason"], "needsOwnerReview");
        assert_eq!(
            store.workspace().unwrap()["projects"][0]["tasks"][0]["reason"],
            "needsOwnerReview"
        );
    }

    #[test]
    fn workspace_projects_latest_scoped_job_reason_without_writing_legacy_checkpoint() {
        for reason in ["", "chatBusy"] {
            let root = storage();
            let store = Store::open(root.path(), "p").unwrap();
            let id = seed_reason_checkpoint(&store, "running", "chatStatusUnknown");
            let mut conn = store.lock().unwrap();
            let tx = conn.transaction().unwrap();
            let raw = load_workspace(&tx).unwrap();
            let mut jobs = load_jobs(&tx).unwrap();
            // Multiple rows use the same task scope. Latest inserted matching
            // job wins; later unrelated rows must not affect the task.
            let mut current = jobs[0].clone();
            current["id"] = json!(format!("{id}-latest"));
            current["reason"] = json!(reason);
            jobs.push(current.clone());
            for other_scope in ["projectId", "taskId"] {
                let mut unrelated = current.clone();
                unrelated["id"] = json!(format!("{id}-{other_scope}"));
                unrelated[other_scope] = json!("unrelated");
                unrelated["reason"] = json!("wrongScope");
                jobs.push(unrelated);
            }
            persist(&tx, &raw, &jobs).unwrap();
            tx.commit().unwrap();
            let stored_jobs = load_jobs(&conn).unwrap();
            drop(conn);
            for _ in 0..3 {
                let projected = store.workspace().unwrap();
                assert_eq!(projected["projects"][0]["tasks"][0]["reason"], reason);
                assert_eq!(
                    projected["projects"][0]["tasks"][0]["status"],
                    raw["projects"][0]["tasks"][0]["status"]
                );
                assert_eq!(projected["revision"], raw["revision"]);
                assert_eq!(
                    projected["projects"][0]["revision"],
                    raw["projects"][0]["revision"]
                );
            }
            let conn = store.lock().unwrap();
            assert_eq!(load_workspace(&conn).unwrap(), raw);
            assert_eq!(load_jobs(&conn).unwrap(), stored_jobs);
        }
    }
}
