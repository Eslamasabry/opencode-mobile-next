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
        .prefix("planner-projection-")
        .tempdir_in(root)
        .unwrap()
}
fn approved(store: &Store) -> (String, String) {
    let c = json!({"requestId":"create","action":"createProject","name":"Project","settings":{"mode":"single","maxLanes":1,"reviewLevel":"milestones","maxFixRounds":0,"chargingOnly":false,"budget":{"chosen":true,"unlimited":true}},"spec":{"goal":"Ship an approved change","milestones":[{"id":"m1","title":"Works","criteria":["Verified"]}]},"repos":[{"id":"repo","serverId":"phone","path":"/workspace/source","devCommit":"abc","mainCommit":"abc"}]});
    let id = store.execute(&c).unwrap()["projectId"]
        .as_str()
        .unwrap()
        .to_owned();
    assert!(store.workspace().unwrap()["projects"][0]["planningState"].is_null());
    assert_eq!(store.execute(&json!({"requestId":"spec","action":"approveSpec","projectId":id,"expectedRevision":0})).unwrap()["accepted"], true);
    let job = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    (id, job)
}
fn project(store: &Store) -> Value {
    store.workspace().unwrap()["projects"][0].clone()
}

#[test]
fn queued_planner_reason_survives_reopen_and_workspace_reads_do_not_mutate() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let (_, job) = approved(&store);
    assert_eq!(project(&store)["status"], "planning");
    assert_eq!(project(&store)["planningState"]["jobId"], job);
    assert_eq!(project(&store)["planningState"]["stage"], "queued");
    assert_eq!(
        project(&store)["timeline"][0]["text"],
        "Planning is queued."
    );
    store
        .update_job(&job, "queued", &json!({"reason":"chatStatusUnknown"}))
        .unwrap();
    let before = project(&store);
    assert_eq!(before["planningState"]["reason"], "chatStatusUnknown");
    assert_eq!(
        before["timeline"][1]["text"],
        "Planning is waiting for chat status to be checked."
    );
    let events = store.events(0, 100).unwrap();
    for _ in 0..3 {
        assert_eq!(project(&store), before);
    }
    assert_eq!(store.events(0, 100).unwrap(), events);
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    assert_eq!(project(&store), before);
    store
        .update_job(&job, "queued", &json!({"reason":"chatStatusUnknown"}))
        .unwrap();
    assert_eq!(project(&store)["timeline"].as_array().unwrap().len(), 2);
    store
        .update_job(&job, "queued", &json!({"stage":"starting"}))
        .unwrap();
    assert_eq!(project(&store)["planningState"]["reason"], "");
    assert_eq!(
        project(&store)["timeline"]
            .as_array()
            .unwrap()
            .last()
            .unwrap()["text"],
        "Planning is starting."
    );
}

#[test]
fn newest_planner_checkpoint_is_project_scoped_and_usage_creates_no_timeline_noise() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let (id, first) = approved(&store);
    store
        .update_job(&first, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store
        .update_job(
            &first,
            "starting",
            &json!({"stage":"running","sessionIds":{"planner":"sesPlan"}}),
        )
        .unwrap();
    let before = project(&store);
    store
        .update_job(
            &first,
            "running",
            &json!({"sessionUsage":{"planner":{"tokens":1,"cost":0.01}}}),
        )
        .unwrap();
    assert_eq!(project(&store)["timeline"], before["timeline"]);
    assert_eq!(project(&store)["revision"], before["revision"]);
    store.update_job(&first, "running", &json!({"stage":"completed","plan":{"phases":[{"id":"phase","title":"Build"}],"tasks":[{"id":"task","title":"Implement","phaseId":"phase","roleId":"worker","repoId":"repo","serverId":"phone","criteria":["Verified"],"dependsOn":[]}]}})).unwrap();
    let revision = project(&store)["revision"].clone();
    assert_eq!(store.execute(&json!({"requestId":"spec-again","action":"approveSpec","projectId":id,"expectedRevision":revision})).unwrap()["accepted"], true);
    let newest = store.jobs().unwrap().last().unwrap()["id"].clone();
    assert_ne!(newest, first);
    assert_eq!(project(&store)["planningState"]["jobId"], newest);
    assert_eq!(project(&store)["planningState"]["stage"], "queued");
    assert_eq!(project(&store)["planningState"]["reason"], "");
    assert_eq!(project(&store)["status"], "planning");
}

#[test]
fn recovery_timeline_is_durable_nonrepeating_and_project_deletion_sweeps_it() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let (id, job) = approved(&store);
    store
        .update_job(&job, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store.recover().unwrap();
    let before = project(&store);
    assert_eq!(before["planningState"]["stage"], "interrupted");
    assert_eq!(
        before["planningState"]["reason"],
        "restartNeedsReconciliation"
    );
    let last = before["timeline"].as_array().unwrap().last().unwrap();
    assert_eq!(last["actor"], "engine");
    assert_eq!(last["kind"], "planner");
    assert_eq!(
        last["text"],
        "Planning was interrupted when the app stopped. Resume to check its existing session."
    );
    store.recover().unwrap();
    assert_eq!(project(&store), before);
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    assert_eq!(project(&store), before);
    assert_eq!(store.execute(&json!({"requestId":"delete","action":"deleteProject","projectId":id,"expectedRevision":before["revision"],"confirmed":true})).unwrap()["accepted"], true);
    assert!(store.jobs().unwrap().is_empty());
    assert!(store.workspace().unwrap()["projects"]
        .as_array()
        .unwrap()
        .is_empty());
}

#[test]
fn timeline_is_bounded_and_never_echoes_unknown_reason_payload() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let (_, job) = approved(&store);
    for n in 0..505 {
        let reason = if n % 2 == 0 {
            "chatBusy"
        } else {
            "chatStatusUnknown"
        };
        store
            .update_job(&job, "queued", &json!({"reason":reason}))
            .unwrap();
    }
    assert_eq!(project(&store)["timeline"].as_array().unwrap().len(), 500);
    assert_eq!(project(&store)["timelineTruncated"], true);
    store
        .update_job(
            &job,
            "queued",
            &json!({"reason":"provider-key-or-private-path"}),
        )
        .unwrap();
    let p = project(&store);
    assert_eq!(
        p["timeline"].as_array().unwrap().last().unwrap()["text"],
        "Planning is waiting for an execution check."
    );
    assert!(!p["timeline"]
        .to_string()
        .contains("provider-key-or-private-path"));
}

#[test]
fn legacy_interrupted_planner_gets_a_stable_read_only_current_state_timeline() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let (_, job) = approved(&store);
    store
        .update_job(&job, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store
        .update_job(
            &job,
            "starting",
            &json!({"stage":"interrupted","reason":"promptUncertain"}),
        )
        .unwrap();
    let conn =
        rusqlite::Connection::open(root.path().join("oc.teamEngine.p/state.sqlite3")).unwrap();
    let mut legacy: Value = serde_json::from_str(
        &conn
            .query_row("SELECT data FROM workspace WHERE id=1", [], |r| {
                r.get::<_, String>(0)
            })
            .unwrap(),
    )
    .unwrap();
    legacy["projects"][0]["timeline"] = json!([]);
    conn.execute(
        "UPDATE workspace SET data=?1 WHERE id=1",
        [legacy.to_string()],
    )
    .unwrap();
    let events = store.events(0, 100).unwrap();
    let before = project(&store);
    assert_eq!(before["planningState"]["reason"], "promptUncertain");
    let row = &before["timeline"][0];
    assert_eq!(row["id"], format!("planner-checkpoint-{job}"));
    assert_eq!(
        row["text"],
        "Planning was interrupted and needs review before resuming."
    );
    assert_eq!(row["at"], before["planningState"]["updatedAt"]);
    for _ in 0..3 {
        assert_eq!(project(&store), before);
    }
    store.recover().unwrap();
    assert_eq!(project(&store), before);
    assert_eq!(store.events(0, 100).unwrap(), events);
    let unchanged: Value = serde_json::from_str(
        &conn
            .query_row("SELECT data FROM workspace WHERE id=1", [], |r| {
                r.get::<_, String>(0)
            })
            .unwrap(),
    )
    .unwrap();
    assert_eq!(unchanged, legacy);
    drop(conn);
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    assert_eq!(project(&store), before);
    // An actual new progress row replaces the read projection, not a duplicate
    // or a fabricated historical interruption persisted by a GET request.
    store
        .update_job(&job, "interrupted", &json!({"reason":"sessionUnknown"}))
        .unwrap();
    let current = project(&store);
    assert_eq!(current["timeline"].as_array().unwrap().len(), 1);
    assert_ne!(current["timeline"][0]["id"], row["id"]);
}
