use oc_phone_engine::store::Store;
use serde_json::{json, Value};
use std::{fs, path::PathBuf};
use tempfile::TempDir;

fn storage() -> TempDir {
    let root = std::env::var_os("OC_ENGINE_TEST_ROOT")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/home/eslam/Storage/tmp/oc-phone-engine-tests"));
    fs::create_dir_all(&root).unwrap();
    tempfile::Builder::new()
        .prefix("store-")
        .tempdir_in(root)
        .unwrap()
}
fn settings() -> Value {
    json!({"mode":"single","maxLanes":1,"reviewLevel":"milestones","maxFixRounds":2,"chargingOnly":false,"budget":{"chosen":true,"unlimited":true,"daily":null,"total":null,"taskTokens":null}})
}
fn create(request: &str) -> Value {
    json!({"requestId":request,"action":"createProject","name":"Durable authored project","settings":settings(),"spec":{"goal":"Implement the approved change","milestones":[{"id":"m1","title":"Works","criteria":["Behavior is verified"]}]},"repos":[{"id":"repo","serverId":"phone","path":"/workspace/source","devCommit":"abc","mainCommit":"abc"}]})
}
#[test]
fn semantic_preflight_rejects_before_side_effects_and_does_not_consume_request() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let before = store.workspace().unwrap();
    let mut c = create("preflight");
    c["settings"]["budget"]["chosen"] = json!(false);
    assert_eq!(
        store.validate_command(&c).unwrap_err().code(),
        "chooseBudget"
    );
    assert_eq!(store.workspace().unwrap(), before);
    assert!(store.jobs().unwrap().is_empty());
    assert!(store.events(0, 100).unwrap().is_empty());
    assert!(store.command_result(&c).unwrap().is_none());

    c["settings"] = settings();
    store.validate_command(&c).unwrap();
    assert_eq!(store.workspace().unwrap(), before);
    assert!(store.command_result(&c).unwrap().is_none());
    let accepted = store.execute(&c).unwrap();
    assert_eq!(accepted["accepted"], true);
    assert_eq!(
        store.workspace().unwrap()["projects"]
            .as_array()
            .unwrap()
            .len(),
        1
    );
    assert_eq!(store.events(0, 100).unwrap().len(), 1);
}

#[test]
fn semantic_preflight_rechecks_current_revision_without_persisting_cloned_jobs() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let created = store.execute(&create("create")).unwrap();
    let project = created["projectId"].as_str().unwrap();
    let c = command("approve", "approveSpec", project, 0);
    let before = store.workspace().unwrap();
    let events = store.events(0, 100).unwrap();
    store.validate_command(&c).unwrap();
    assert_eq!(store.workspace().unwrap(), before);
    assert!(store.jobs().unwrap().is_empty());
    assert_eq!(store.events(0, 100).unwrap(), events);
    store.execute(&c).unwrap();
    assert_eq!(
        store.validate_command(&c).unwrap_err().code(),
        "staleRevision"
    );
}

#[test]
fn charging_only_is_rejected_at_creation_defaults_and_settings_update() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let mut unsupported = settings();
    unsupported["chargingOnly"] = json!(true);
    let mut c = create("charging-create");
    c["settings"] = unsupported.clone();
    assert_eq!(
        store.validate_command(&c).unwrap_err().code(),
        "chargingUnsupported"
    );
    assert_eq!(store.execute(&c).unwrap()["code"], "chargingUnsupported");
    assert!(store.workspace().unwrap()["projects"]
        .as_array()
        .unwrap()
        .is_empty());
    let defaults =
        json!({"requestId":"charging-defaults","action":"updateDefaults","settings":unsupported});
    assert_eq!(
        store.execute(&defaults).unwrap()["code"],
        "chargingUnsupported"
    );

    let created = store.execute(&create("supported")).unwrap();
    let before = store.workspace().unwrap();
    let mut update = command(
        "charging-update",
        "updateSettings",
        created["projectId"].as_str().unwrap(),
        0,
    );
    update["settings"] = defaults["settings"].clone();
    assert_eq!(
        store.execute(&update).unwrap()["code"],
        "chargingUnsupported"
    );
    assert_eq!(store.workspace().unwrap(), before);
}

fn seed_events(root: &std::path::Path, count: usize) {
    let mut conn = rusqlite::Connection::open(root.join("oc.teamEngine.p/state.sqlite3")).unwrap();
    let tx = conn.transaction().unwrap();
    {
        let mut insert = tx.prepare("INSERT INTO events(data) VALUES(?1)").unwrap();
        for _ in 0..count {
            insert
                .execute([r#"{"kind":"historical","action":"seed"}"#])
                .unwrap();
        }
    }
    tx.commit().unwrap();
}

#[test]
fn event_retention_prunes_on_append_and_persists_explicit_cursor_gap() {
    use oc_phone_engine::store::EVENT_RETENTION_LIMIT;
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    seed_events(root.path(), EVENT_RETENTION_LIMIT);
    store.execute(&create("create")).unwrap();
    let window = store.event_window().unwrap();
    assert_eq!(window["prunedThroughSeq"], 1);
    assert_eq!(window["earliestAvailableSeq"], 2);
    assert_eq!(window["latestSeq"], EVENT_RETENTION_LIMIT + 1);
    assert_eq!(store.events(0, 100).unwrap_err().code(), "cursorExpired");
    let retained = store.events(1, 1000).unwrap();
    assert_eq!(retained[0]["seq"], 2);
    let conn =
        rusqlite::Connection::open(root.path().join("oc.teamEngine.p/state.sqlite3")).unwrap();
    let count: i64 = conn
        .query_row("SELECT COUNT(*) FROM events", [], |r| r.get(0))
        .unwrap();
    assert_eq!(count, EVENT_RETENTION_LIMIT as i64);
    drop(conn);
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    assert_eq!(store.event_window().unwrap(), window);
    assert_eq!(store.events(0, 100).unwrap_err().code(), "cursorExpired");
    assert_eq!(store.events(1, 1000).unwrap(), retained);
    assert_eq!(
        store.command_result(&create("create")).unwrap().unwrap()["accepted"],
        true
    );
}

#[test]
fn event_retention_migrates_existing_large_logs_without_resetting_sequence() {
    use oc_phone_engine::store::EVENT_RETENTION_LIMIT;
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    seed_events(root.path(), EVENT_RETENTION_LIMIT + 11);
    drop(store);
    // Simulate a database from before bounded event retention was introduced.
    let conn =
        rusqlite::Connection::open(root.path().join("oc.teamEngine.p/state.sqlite3")).unwrap();
    conn.execute_batch("DROP TABLE event_retention").unwrap();
    drop(conn);
    let store = Store::open(root.path(), "p").unwrap();
    let window = store.event_window().unwrap();
    assert_eq!(window["prunedThroughSeq"], 11);
    assert_eq!(window["earliestAvailableSeq"], 12);
    assert_eq!(window["latestSeq"], EVENT_RETENTION_LIMIT + 11);
    assert_eq!(store.events(10, 100).unwrap_err().code(), "cursorExpired");
    assert_eq!(store.events(11, 100).unwrap()[0]["seq"], 12);
    store.execute(&create("next")).unwrap();
    assert_eq!(
        store.event_window().unwrap()["latestSeq"],
        EVENT_RETENTION_LIMIT + 12
    );
    assert_eq!(store.event_window().unwrap()["prunedThroughSeq"], 12);
}
fn command(request: &str, action: &str, id: &str, revision: u64) -> Value {
    json!({"requestId":request,"action":action,"projectId":id,"expectedRevision":revision})
}
fn current_command(store: &Store, request: &str, action: &str, id: &str) -> Value {
    let w = store.workspace().unwrap();
    let p = w["projects"]
        .as_array()
        .unwrap()
        .iter()
        .find(|p| p["id"] == id)
        .unwrap();
    command(request, action, id, p["revision"].as_u64().unwrap())
}
fn prepared_task(store: &Store) -> (String, String) {
    let (project, _) = prepared(store);
    assert_eq!(
        store
            .execute(&current_command(store, "plan", "approvePlan", &project))
            .unwrap()["accepted"],
        true
    );
    let task = store
        .jobs()
        .unwrap()
        .into_iter()
        .find(|j| j["kind"] == "task")
        .unwrap();
    (project, task["id"].as_str().unwrap().to_owned())
}
fn resume_task(store: &Store, request: &str, project: &str) -> Value {
    let mut c = current_command(store, request, "resumeTask", project);
    c["targetId"] = json!("task");
    store.execute(&c).unwrap()
}
fn prepared(store: &Store) -> (String, String) {
    let created = store.execute(&create("create")).unwrap();
    let id = created["projectId"].as_str().unwrap().to_owned();
    assert_eq!(
        store
            .execute(&command("spec", "approveSpec", &id, 0))
            .unwrap()["accepted"],
        true
    );
    let planner = store.jobs().unwrap().remove(0)["id"]
        .as_str()
        .unwrap()
        .to_owned();
    store
        .update_job(&planner, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store
        .update_job(
            &planner,
            "starting",
            &json!({"stage":"running","sessionIds":{"planner":"session-plan"}}),
        )
        .unwrap();
    store.update_job(&planner,"running",&json!({"stage":"completed","plan":{"phases":[{"id":"phase","title":"Build"}],"tasks":[{"id":"task","title":"Implementation","phaseId":"phase","roleId":"worker","repoId":"repo","serverId":"phone","criteria":["Behavior is verified"],"dependsOn":[],"status":"queued"}]}})).unwrap();
    (id, planner)
}

#[test]
fn command_replay_survives_reopen_and_does_not_duplicate_events() {
    let root = storage();
    let store = Store::open(root.path(), "profile").unwrap();
    let first = store.execute(&create("create")).unwrap();
    assert_eq!(first["accepted"], true);
    let events = store.events(0, 100).unwrap();
    drop(store);
    let store = Store::open(root.path(), "profile").unwrap();
    let replay = store.execute(&create("create")).unwrap();
    assert_eq!(replay["replayed"], true);
    assert_eq!(replay["projectId"], first["projectId"]);
    assert_eq!(
        store.workspace().unwrap()["projects"]
            .as_array()
            .unwrap()
            .len(),
        1
    );
    assert_eq!(store.events(0, 100).unwrap(), events);
}
#[test]
fn request_reuse_is_payload_bound_and_rejections_are_durable() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let c = create("create");
    store.execute(&c).unwrap();
    let mut changed = c.clone();
    changed["name"] = json!("Different");
    assert_eq!(store.execute(&changed).unwrap()["code"], "requestIdReuse");
    let bad = command(
        "stale",
        "pauseProject",
        store.workspace().unwrap()["projects"][0]["id"]
            .as_str()
            .unwrap(),
        99,
    );
    assert_eq!(store.execute(&bad).unwrap()["code"], "staleRevision");
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    let result = store.execute(&bad).unwrap();
    assert_eq!(result["replayed"], true);
    assert_eq!(result["code"], "staleRevision");
}
#[test]
fn semantic_failure_rolls_back_all_proposed_plan_and_jobs() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let (id, _) = prepared(&store);
    let before = store.workspace().unwrap();
    let jobs = store.jobs().unwrap();
    let revision = before["projects"][0]["revision"].as_u64().unwrap();
    let mut c = command("plan", "approvePlan", &id, revision);
    c["tasks"] = json!([{"id":"bad","title":"Bad","phaseId":"phase","roleId":"worker","repoId":"repo","serverId":"phone","criteria":["Check"],"dependsOn":["absent"]}]);
    assert_eq!(store.execute(&c).unwrap()["code"], "missingDependency");
    assert_eq!(store.workspace().unwrap(), before);
    assert_eq!(store.jobs().unwrap(), jobs);
}
#[test]
fn invalid_planner_proposal_rolls_back_before_publishing_approval() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let created = store.execute(&create("create")).unwrap();
    let id = created["projectId"].as_str().unwrap();
    store
        .execute(&command("spec", "approveSpec", id, 0))
        .unwrap();
    let job = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    store
        .update_job(&job, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store
        .update_job(&job, "starting", &json!({"stage":"running"}))
        .unwrap();
    let before = store.workspace().unwrap();
    let jobs = store.jobs().unwrap();
    let mut plan = json!({"phases":[{"id":"phase","title":"Build"}],"tasks":[{"id":"task","phaseId":"phase","roleId":"worker","repoId":"repo","serverId":"phone","criteria":["Behavior is verified"],"dependsOn":[]}]});
    assert_eq!(
        store
            .update_job(&job, "running", &json!({"stage":"completed","plan":plan}))
            .unwrap_err()
            .code(),
        "planTaskTitleRequired"
    );
    assert_eq!(store.workspace().unwrap(), before);
    assert_eq!(store.jobs().unwrap(), jobs);
    plan["tasks"][0]["title"] = json!("Implementation");
    for invalid in [
        json!([{"id":"phase","title":42}]),
        json!([{"id":"phase","title":"Build","risky":"true"}]),
        json!([{"id":"phase","title":"Build"},42]),
        json!([{"id":"phase","title":"Build"},{"id":"phase","title":"Duplicate"}]),
    ] {
        plan["phases"] = invalid;
        assert_eq!(
            store
                .update_job(&job, "running", &json!({"stage":"completed","plan":plan}))
                .unwrap_err()
                .code(),
            "planPhaseInvalid"
        );
        assert_eq!(store.workspace().unwrap(), before);
        assert_eq!(store.jobs().unwrap(), jobs);
    }
    plan["phases"] =
        json!([{"id":"phase","title":"Build","accepted":true,"extra":"model runtime"}]);

    store
        .update_job(&job, "running", &json!({"stage":"completed","plan":plan}))
        .unwrap();
    let w = store.workspace().unwrap();
    assert_eq!(w["projects"][0]["tasks"][0]["status"], "queued");
    assert_eq!(w["projects"][0]["tasks"][0]["findings"], json!([]));
    assert_eq!(w["projects"][0]["phases"][0]["accepted"], false);
    assert!(w["projects"][0]["phases"][0].get("extra").is_none());
    assert_eq!(
        store
            .execute(&current_command(&store, "plan", "approvePlan", id))
            .unwrap()["accepted"],
        true
    );
}

#[test]
fn planner_proposal_requires_approval_before_task_queue_is_created() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let (id, _) = prepared(&store);
    let w = store.workspace().unwrap();
    assert_eq!(w["projects"][0]["status"], "needsPlanApproval");
    assert_eq!(store.jobs().unwrap().len(), 1);
    let result = store
        .execute(&command(
            "plan",
            "approvePlan",
            &id,
            w["projects"][0]["revision"].as_u64().unwrap(),
        ))
        .unwrap();
    assert_eq!(result["accepted"], true);
    let jobs = store.jobs().unwrap();
    assert_eq!(jobs.len(), 2);
    assert_eq!(jobs[1]["stage"], "queued");
    assert_eq!(jobs[1]["usage"]["cost"], Value::Null);
    assert_eq!(
        store.workspace().unwrap()["projects"][0]["usageReported"],
        false
    );
}
#[test]
fn restart_interrupts_uncertain_submission_and_preserves_session_and_usage() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let created = store.execute(&create("create")).unwrap();
    let id = created["projectId"].as_str().unwrap();
    store
        .execute(&command("spec", "approveSpec", id, 0))
        .unwrap();
    let job = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    store
        .update_job(
            &job,
            "queued",
            &json!({"stage":"starting","sessionIds":{"planner":"existing"},"usage":{"tokens":12}}),
        )
        .unwrap();
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    store.recover().unwrap();
    let jobs = store.jobs().unwrap();
    assert_eq!(jobs[0]["stage"], "interrupted");
    assert_eq!(jobs[0]["sessionIds"]["planner"], "existing");
    assert_eq!(jobs[0]["usage"]["cost"], Value::Null);
    assert_eq!(jobs[0]["usage"]["tokens"], 12);
    let events = store.events(0, 100).unwrap();
    store.recover().unwrap();
    assert_eq!(store.events(0, 100).unwrap(), events);
}
#[test]
fn cas_rejects_late_updates_and_usage_regression_atomically() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let created = store.execute(&create("create")).unwrap();
    store
        .execute(&command(
            "spec",
            "approveSpec",
            created["projectId"].as_str().unwrap(),
            0,
        ))
        .unwrap();
    let id = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    store
        .update_job(
            &id,
            "queued",
            &json!({"stage":"starting","usage":{"cost":0.25,"tokens":100}}),
        )
        .unwrap();
    let before = store.jobs().unwrap();
    assert_eq!(
        store
            .update_job(&id, "queued", &json!({"stage":"starting"}))
            .unwrap_err()
            .code(),
        "staleJobStage"
    );
    assert_eq!(
        store
            .update_job(
                &id,
                "starting",
                &json!({"stage":"running","usage":{"cost":0.10}})
            )
            .unwrap_err()
            .code(),
        "usageRegression"
    );
    assert_eq!(store.jobs().unwrap(), before);
    assert_eq!(
        store
            .update_job(&id, "starting", &json!({"sessionIds":{"planner":"one"}}))
            .unwrap()["sessionIds"]["planner"],
        "one"
    );
    assert_eq!(
        store
            .update_job(&id, "starting", &json!({"sessionIds":{"planner":"two"}}))
            .unwrap_err()
            .code(),
        "sessionAlreadyRecorded"
    );
}
#[test]
fn deletion_tombstone_blocks_old_handles_reopen_and_late_commands() {
    let root = storage();
    let first = Store::open(root.path(), "p").unwrap();
    let late = Store::open(root.path(), "p").unwrap();
    first.execute(&create("create")).unwrap();
    first.delete_profile().unwrap();
    assert_eq!(
        late.execute(&create("late")).unwrap_err().code(),
        "profileDeleted"
    );
    assert_eq!(late.workspace().unwrap_err().code(), "profileDeleted");
    assert_eq!(
        Store::open(root.path(), "p").err().unwrap().code(),
        "profileDeleted"
    );
    first.delete_profile().unwrap();
    assert!(Store::open(root.path(), "other")
        .unwrap()
        .workspace()
        .unwrap()["projects"]
        .as_array()
        .unwrap()
        .is_empty());
}
#[test]
fn event_stream_contains_metadata_and_never_authored_content() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let mut c = create("create");
    c["spec"]["goal"] = json!("Private authored text with sk-redaction-probe");
    store.execute(&c).unwrap();
    let events = store.events(0, 100).unwrap();
    assert_eq!(events.len(), 1);
    assert!(!serde_json::to_string(&events)
        .unwrap()
        .contains("sk-redaction-probe"));
    assert!(store
        .events(events[0]["seq"].as_i64().unwrap(), 100)
        .unwrap()
        .is_empty());
    assert_eq!(store.events(-1, 1).unwrap_err().code(), "invalidCursor");
}
#[test]
fn profile_names_cannot_escape_namespace_and_symlink_is_rejected() {
    let root = storage();
    assert_eq!(
        Store::open(root.path(), "../other").err().unwrap().code(),
        "invalidProfile"
    );
    #[cfg(unix)]
    {
        std::os::unix::fs::symlink(root.path(), root.path().join("oc.teamEngine.link")).unwrap();
        assert_eq!(
            Store::open(root.path(), "link").err().unwrap().code(),
            "unsafeStoragePath"
        );
    }
}
#[test]
fn unsupported_simulation_and_merge_actions_never_modify_state() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let before = store.workspace().unwrap();
    for action in [
        "advance",
        "simulateConflict",
        "promote",
        "processMergeQueue",
        "undoMerge",
    ] {
        let result = store
            .execute(&json!({"requestId":action,"action":action}))
            .unwrap();
        assert_eq!(result["accepted"], false);
        assert_eq!(result["code"], "unsupportedAction");
    }
    assert_eq!(store.workspace().unwrap(), before);
}

#[test]
fn imported_actual_refs_preserve_the_authored_replay_binding() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let c = create("create");
    assert!(store.command_result(&c).unwrap().is_none());
    let mut imported = c["repos"].clone();
    imported[0]["devCommit"] = json!("actual-dev");
    imported[0]["mainCommit"] = json!("actual-main");
    store.execute_with_repositories(&c, &imported).unwrap();
    assert_eq!(
        store.workspace().unwrap()["projects"][0]["repos"][0]["devCommit"],
        "actual-dev"
    );
    imported[0]["devCommit"] = json!("later-dev");
    assert_eq!(
        store.execute_with_repositories(&c, &imported).unwrap()["replayed"],
        true
    );
    assert_eq!(
        store.workspace().unwrap()["projects"][0]["repos"][0]["devCommit"],
        "actual-dev"
    );
    let mut changed = c.clone();
    changed["name"] = json!("Changed");
    assert_eq!(
        store.command_result(&changed).unwrap_err().code(),
        "requestIdReuse"
    );
}

#[test]
fn promotion_requires_bound_physical_evidence_and_replays_after_revision_changes() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let id = store.execute(&create("create")).unwrap()["projectId"]
        .as_str()
        .unwrap()
        .to_owned();
    let mut c = command("promote", "promote", &id, 0);
    c["targetId"] = json!("repo");
    c["confirmed"] = json!(true);
    c["expectedDevCommit"] = json!("abc");
    c["expectedMainCommit"] = json!("abc");
    let receipt = json!({"repoId":"repo","receiptId":"promote","before":{"devCommit":"abc","mainCommit":"abc"},"after":{"devCommit":"abc","mainCommit":"abc"}});
    let mut bad = receipt.clone();
    bad["after"]["mainCommit"] = json!("different");
    let before = store.workspace().unwrap();
    assert_eq!(
        store.record_promotion(&c, &bad).unwrap_err().code(),
        "invalidRepositoryReceipt"
    );
    assert_eq!(store.workspace().unwrap(), before);
    let result = store.record_promotion(&c, &receipt).unwrap();
    assert_eq!(result["accepted"], true);
    assert_eq!(
        store.workspace().unwrap()["projects"][0]["receipts"]
            .as_array()
            .unwrap()
            .len(),
        1
    );
    assert_eq!(
        store.record_promotion(&c, &receipt).unwrap()["replayed"],
        true
    );
    c["expectedRevision"] = json!(1);
    assert_eq!(
        store.record_promotion(&c, &receipt).unwrap_err().code(),
        "requestIdReuse"
    );
}

#[test]
fn per_session_usage_is_cumulative_and_remains_unknown_without_cost_evidence() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let id = store.execute(&create("create")).unwrap()["projectId"]
        .as_str()
        .unwrap()
        .to_owned();
    store
        .execute(&command("spec", "approveSpec", &id, 0))
        .unwrap();
    let job = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    let j=store.update_job(&job,"queued",&json!({"stage":"starting","sessionIds":{"planner":"session"},"sessionUsage":{"planner":{"cost":null,"tokens":5}},"usage":{"cost":null,"tokens":5}})).unwrap();
    assert_eq!(j["sessionUsage"]["planner"]["tokens"], 5);
    assert_eq!(j["sessionUsage"]["planner"]["cost"], Value::Null);
    assert_eq!(
        store.workspace().unwrap()["projects"][0]["usageReported"],
        false
    );
    assert_eq!(
        store
            .update_job(
                &job,
                "starting",
                &json!({"sessionUsage":{"planner":{"tokens":4}}})
            )
            .unwrap_err()
            .code(),
        "usageRegression"
    );
    store.recover().unwrap();
    assert_eq!(
        store
            .update_job(&job, "interrupted", &json!({"stage":"starting"}))
            .unwrap_err()
            .code(),
        "invalidJobTransition"
    );
    assert_eq!(
        store
            .update_job(&job, "interrupted", &json!({"stage":"resuming"}))
            .unwrap()["sessionIds"]["planner"],
        "session"
    );
}

#[test]
fn session_snapshots_sum_once_and_a_new_session_keeps_unknown_usage_unknown() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let id = store.execute(&create("create")).unwrap()["projectId"]
        .as_str()
        .unwrap()
        .to_owned();
    store
        .execute(&command("spec", "approveSpec", &id, 0))
        .unwrap();
    let job = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    let snapshot = json!({"sessionIds":{"planner":"planner"},"sessionUsage":{"planner":{"cost":0.25,"tokens":10}}});
    let first = store.update_job(&job, "queued", &snapshot).unwrap();
    let second = store.update_job(&job, "queued", &snapshot).unwrap();
    assert_eq!(first["usage"], second["usage"]);
    assert_eq!(second["usage"]["cost"], 0.25);
    assert_eq!(second["usage"]["tokens"], 10);
    let unknown = store
        .update_job(&job, "queued", &json!({"sessionIds":{"checker":"checker"}}))
        .unwrap();
    assert_eq!(unknown["usage"]["cost"], Value::Null);
    let known = store
        .update_job(
            &job,
            "queued",
            &json!({"sessionUsage":{"checker":{"cost":0.50,"tokens":20}}}),
        )
        .unwrap();
    assert_eq!(known["usage"]["cost"], 0.75);
    assert_eq!(known["usage"]["tokens"], 30);
}

#[test]
fn restarted_worker_resumes_only_its_recorded_uncertain_turn_and_keeps_checkpoint() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let (project, job) = prepared_task(&store);
    store
        .update_job(&job, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store.update_job(&job, "starting", &json!({"stage":"running","directory":"/root/work/task","sessionIds":{"worker":"original-worker"}})).unwrap();
    store
        .update_job(
            &job,
            "running",
            &json!({"promptDispatch":{"worker":"dispatching"}}),
        )
        .unwrap();
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    store.recover().unwrap();
    assert_eq!(store.jobs().unwrap()[1]["resumeStage"], "running");
    // Merely reopening never grants admission: a user command is required.
    assert_eq!(store.jobs().unwrap()[1]["stage"], "interrupted");
    assert_eq!(resume_task(&store, "resume", &project)["accepted"], true);
    let resumed = store.jobs().unwrap()[1].clone();
    assert_eq!(resumed["stage"], "resuming");
    assert_eq!(resumed["sessionIds"]["worker"], "original-worker");
    assert_eq!(resumed["promptDispatch"]["worker"], "dispatching");
    assert_eq!(resumed["directory"], "/root/work/task");
    assert_eq!(
        store.workspace().unwrap()["projects"][0]["status"],
        "running"
    );
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    store.recover().unwrap();
    assert_eq!(store.jobs().unwrap()[1]["resumeStage"], "running");
    assert_eq!(
        resume_task(&store, "resume-again", &project)["accepted"],
        true
    );
    assert_eq!(
        store.jobs().unwrap()[1]["sessionIds"],
        resumed["sessionIds"]
    );
}

#[test]
fn explicit_project_resume_reconciles_checker_without_replacing_its_turn() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let (project, job) = prepared_task(&store);
    store
        .update_job(&job, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store.update_job(&job, "starting", &json!({"stage":"running","directory":"/root/work/task","sessionIds":{"worker":"worker"}})).unwrap();
    store
        .update_job(
            &job,
            "running",
            &json!({"promptDispatch":{"worker":"dispatched"}}),
        )
        .unwrap();
    store.update_job(&job, "running", &json!({"stage":"checking","sessionIds":{"checker":"checker"},"taskCommit":"checked-commit"})).unwrap();
    store
        .update_job(
            &job,
            "checking",
            &json!({"promptDispatch":{"checker":"dispatched"}}),
        )
        .unwrap();
    assert_eq!(
        store
            .execute(&current_command(&store, "pause", "pauseProject", &project))
            .unwrap()["accepted"],
        true
    );
    let paused = store.jobs().unwrap()[1].clone();
    assert_eq!(paused["resumeStage"], "checking");
    assert_eq!(
        store
            .execute(&current_command(
                &store,
                "resume-project",
                "resumeProject",
                &project
            ))
            .unwrap()["accepted"],
        true
    );
    let resumed = store.jobs().unwrap()[1].clone();
    assert_eq!(resumed["stage"], "resuming");
    assert_eq!(resumed["taskCommit"], "checked-commit");
    assert_eq!(resumed["sessionIds"], paused["sessionIds"]);
    assert_eq!(resumed["promptDispatch"], paused["promptDispatch"]);
}

#[test]
fn undispatched_restart_can_requeue_but_uncertain_session_or_prompt_cannot() {
    for reason in [
        None,
        Some("sessionCreateUncertain"),
        Some("promptUncertain"),
    ] {
        let root = storage();
        let store = Store::open(root.path(), "p").unwrap();
        let (project, job) = prepared_task(&store);
        store
            .update_job(&job, "queued", &json!({"stage":"starting"}))
            .unwrap();
        if let Some(reason) = reason {
            store
                .update_job(
                    &job,
                    "starting",
                    &json!({"stage":"interrupted","reason":reason}),
                )
                .unwrap();
        } else {
            store.recover().unwrap();
        }
        let before = store.jobs().unwrap();
        let result = resume_task(&store, "resume", &project);
        if reason.is_some() {
            assert_eq!(result["code"], "needsReconciliation");
            assert_eq!(store.jobs().unwrap(), before);
        } else {
            assert_eq!(result["accepted"], true);
            let resumed = store.jobs().unwrap()[1].clone();
            assert_eq!(resumed["stage"], "queued");
            assert_eq!(resumed["sessionIds"], json!({}));
            assert!(resumed["promptDispatch"].is_null());
        }
    }
}

#[test]
fn missing_dispatch_checkpoint_and_missing_checker_commit_fail_closed() {
    for checker in [false, true] {
        let root = storage();
        let store = Store::open(root.path(), "p").unwrap();
        let (project, job) = prepared_task(&store);
        store
            .update_job(&job, "queued", &json!({"stage":"starting"}))
            .unwrap();
        store.update_job(&job, "starting", &json!({"stage":"running","directory":"/root/work/task","sessionIds":{"worker":"worker"}})).unwrap();
        if checker {
            store
                .update_job(
                    &job,
                    "running",
                    &json!({"stage":"checking","sessionIds":{"checker":"checker"}}),
                )
                .unwrap();
            store
                .update_job(
                    &job,
                    "checking",
                    &json!({"promptDispatch":{"checker":"dispatched"}}),
                )
                .unwrap();
        }
        store.recover().unwrap();
        let before = store.workspace().unwrap();
        let jobs = store.jobs().unwrap();
        assert_eq!(
            resume_task(&store, "resume", &project)["code"],
            "needsReconciliation"
        );
        assert_eq!(store.workspace().unwrap(), before);
        assert_eq!(store.jobs().unwrap(), jobs);
    }
}

#[test]
fn prompt_acknowledgment_is_monotonic_and_durable() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let (_, job) = prepared_task(&store);
    store
        .update_job(
            &job,
            "queued",
            &json!({"stage":"starting","sessionIds":{"worker":"worker"}}),
        )
        .unwrap();
    store
        .update_job(
            &job,
            "starting",
            &json!({"promptDispatch":{"worker":"dispatching"}}),
        )
        .unwrap();
    store
        .update_job(
            &job,
            "starting",
            &json!({"promptDispatch":{"worker":"dispatched"}}),
        )
        .unwrap();
    assert_eq!(
        store
            .update_job(
                &job,
                "starting",
                &json!({"promptDispatch":{"worker":"dispatching"}})
            )
            .unwrap_err()
            .code(),
        "promptAlreadyDispatched"
    );
    drop(store);
    assert_eq!(
        Store::open(root.path(), "p").unwrap().jobs().unwrap()[1]["promptDispatch"]["worker"],
        "dispatched"
    );
}

#[test]
fn usage_changes_workspace_snapshot_without_invalidating_user_revision() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let (project, job) = prepared_task(&store);
    store
        .update_job(
            &job,
            "queued",
            &json!({"stage":"starting","sessionIds":{"worker":"worker"}}),
        )
        .unwrap();
    let before = store.workspace().unwrap();
    let pause = current_command(&store, "pause", "pauseProject", &project);
    let old_events = store.events(0, 100).unwrap();
    for tokens in 1..=3 {
        store
            .update_job(
                &job,
                "starting",
                &json!({"sessionUsage":{"worker":{"tokens":tokens,"cost":null}}}),
            )
            .unwrap();
    }
    let after = store.workspace().unwrap();
    assert_eq!(
        after["projects"][0]["revision"],
        before["projects"][0]["revision"]
    );
    assert!(after["revision"].as_u64().unwrap() > before["revision"].as_u64().unwrap());
    assert_eq!(after["projects"][0]["usageRevision"], 3);
    assert_eq!(after["projects"][0]["usageReported"], false);
    assert_eq!(after["projects"][0]["tasks"][0]["tokens"], 3);
    assert_eq!(store.jobs().unwrap()[1]["usage"]["cost"], Value::Null);
    let ticks = store
        .events(old_events.last().unwrap()["seq"].as_i64().unwrap(), 100)
        .unwrap();
    assert_eq!(ticks.len(), 3);
    assert!(ticks.iter().all(|e| e["kind"] == "usage"
        && e["action"] == "sessionUsage"
        && e["revision"] == before["projects"][0]["revision"]));
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    assert_eq!(
        store.workspace().unwrap()["projects"][0]["usageRevision"],
        3
    );
    // The exact user revision obtained before three ticks is still valid.
    assert_eq!(store.execute(&pause).unwrap()["accepted"], true);
    assert!(
        store.workspace().unwrap()["projects"][0]["revision"]
            .as_u64()
            .unwrap()
            > before["projects"][0]["revision"].as_u64().unwrap()
    );
}

#[test]
fn explicit_task_resume_after_checker_parse_failure_preserves_the_completed_turn() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let (project, job) = prepared_task(&store);
    store
        .update_job(&job, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store
        .update_job(
            &job,
            "starting",
            &json!({
                "stage":"running", "directory":"/root/work/task",
                "sessionIds":{"worker":"original-worker"},
                "promptDispatch":{"worker":"dispatched"}
            }),
        )
        .unwrap();
    store
        .update_job(
            &job,
            "running",
            &json!({
                "resolvedModels":{"checker":"zai-coding-plan/glm-5.3-flash"}
            }),
        )
        .unwrap();
    store
        .update_job(
            &job,
            "running",
            &json!({
                "stage":"checking", "sessionIds":{"checker":"completed-checker"},
                "promptDispatch":{"checker":"dispatched"}, "taskCommit":"checked-commit"
            }),
        )
        .unwrap();
    store
        .update_job(
            &job,
            "checking",
            &json!({
                "stage":"interrupted", "reason":"structuredOutputInvalid"
            }),
        )
        .unwrap();
    let interrupted = store.jobs().unwrap()[1].clone();
    assert_eq!(interrupted["resumeStage"], "checking");
    assert_eq!(
        store.workspace().unwrap()["projects"][0]["tasks"][0]["status"],
        "interrupted"
    );
    // A parse error is resumable without pretending a process restart occurred.
    // Reopening storage must retain the original completed session checkpoint.
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    let mut resume = current_command(&store, "resume-checker", "resumeTask", &project);
    resume["targetId"] = json!("task");
    assert_eq!(store.execute(&resume).unwrap()["accepted"], true);
    assert_eq!(store.execute(&resume).unwrap()["replayed"], true);
    let jobs = store.jobs().unwrap();
    assert_eq!(jobs.len(), 2);
    let resumed = &jobs[1];
    assert_eq!(resumed["stage"], "resuming");
    assert_eq!(resumed["reason"], "");
    for key in [
        "id",
        "sessionIds",
        "promptDispatch",
        "taskCommit",
        "directory",
        "resolvedModels",
        "resumeStage",
    ] {
        assert_eq!(resumed[key], interrupted[key], "changed {key}");
    }
}
