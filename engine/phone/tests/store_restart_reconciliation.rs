use oc_phone_engine::store::Store;
use serde_json::{json, Value};
use std::{fs, path::PathBuf};

fn fixture() -> (tempfile::TempDir, Store, String, String) {
    let parent = std::env::var_os("OC_ENGINE_TEST_ROOT")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/home/eslam/Storage/tmp/oc-phone-engine-tests"));
    fs::create_dir_all(&parent).unwrap();
    let root = tempfile::Builder::new()
        .prefix("restart-")
        .tempdir_in(parent)
        .unwrap();
    let store = Store::open(root.path(), "p").unwrap();
    let created = store.execute(&json!({"requestId":"create","action":"createProject","name":"Recovery",
        "settings":{"mode":"parallel","maxLanes":2,"reviewLevel":"milestones","maxFixRounds":0,"chargingOnly":false,"budget":{"chosen":true,"unlimited":true}},
        "spec":{"goal":"Reconcile safely","milestones":[{"id":"m","title":"Safe","criteria":["No prompt replay"]}]},
        "repos":[{"id":"repo","serverId":"phone","path":"/root/projects/recovery","devCommit":"seed","mainCommit":"seed"}]})).unwrap();
    let project = created["projectId"].as_str().unwrap().to_owned();
    assert_eq!(store.execute(&json!({"requestId":"approve","action":"approveSpec","projectId":project,"expectedRevision":0})).unwrap()["accepted"], true);
    let job = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    (root, store, project, job)
}
fn project(store: &Store) -> Value {
    store.workspace().unwrap()["projects"][0].clone()
}
fn recorded_turn(store: &Store, job: &str) {
    store
        .update_job(job, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store.update_job(job,"starting",&json!({"stage":"running","directory":"/root/work/plan","sessionIds":{"planner":"original"},"promptDispatch":{"planner":"dispatching"}})).unwrap();
}
#[test]
fn ready_attachment_reconciles_restart_once_and_keeps_original_turn() {
    let (root, store, _, job) = fixture();
    recorded_turn(&store, &job);
    store.recover().unwrap();
    let checkpoint = store.jobs().unwrap()[0].clone();
    store.reconcile_restart_jobs().unwrap();
    let resumed = store.jobs().unwrap()[0].clone();
    assert_eq!(resumed["stage"], "resuming");
    assert_eq!(resumed["reason"], "");
    assert_eq!(resumed["sessionIds"], checkpoint["sessionIds"]);
    assert_eq!(resumed["promptDispatch"], checkpoint["promptDispatch"]);
    assert_eq!(resumed["resumeStage"], "running");
    assert_eq!(project(&store)["status"], "planning");
    let before = project(&store);
    let events = store.events(0, 100).unwrap();
    store.reconcile_restart_jobs().unwrap();
    assert_eq!(project(&store), before);
    assert_eq!(store.events(0, 100).unwrap(), events);
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    assert_eq!(
        store.jobs().unwrap()[0]["sessionIds"]["planner"],
        "original"
    );
}
#[test]
fn uncertain_checkpoint_becomes_reviewable_instead_of_endless_restart_wait() {
    let (_, store, _, job) = fixture();
    store
        .update_job(&job, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store.update_job(&job,"starting",&json!({"stage":"running","directory":"/root/work/plan","sessionIds":{"planner":"original"}})).unwrap();
    store.recover().unwrap();
    store.reconcile_restart_jobs().unwrap();
    assert_eq!(store.jobs().unwrap()[0]["stage"], "interrupted");
    assert_eq!(store.jobs().unwrap()[0]["reason"], "recoveryNeedsReview");
    assert_eq!(
        project(&store)["planningState"]["reason"],
        "recoveryNeedsReview"
    );
    let before = project(&store);
    store.reconcile_restart_jobs().unwrap();
    assert_eq!(project(&store), before);
}
#[test]
fn safe_never_dispatched_restart_requeues_without_existing_session() {
    let (_, store, _, job) = fixture();
    store
        .update_job(&job, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store.recover().unwrap();
    store.reconcile_restart_jobs().unwrap();
    assert_eq!(store.jobs().unwrap()[0]["stage"], "queued");
    assert_eq!(store.jobs().unwrap()[0]["sessionIds"], json!({}));
    assert_eq!(project(&store)["status"], "planning");
}
#[test]
fn user_paused_stopped_and_existing_review_reasons_never_auto_resume() {
    for action in ["pauseProject", "stopProject", "review"] {
        let (_, store, id, job) = fixture();
        recorded_turn(&store, &job);
        if action == "review" {
            store
                .update_job(
                    &job,
                    "running",
                    &json!({"stage":"interrupted","reason":"sessionUnknown"}),
                )
                .unwrap();
        } else {
            store.execute(&json!({"requestId":action,"action":action,"projectId":id,"expectedRevision":project(&store)["revision"],"confirmed":true})).unwrap();
        }
        store.recover().unwrap();
        let before = store.workspace().unwrap();
        let jobs = store.jobs().unwrap();
        store.reconcile_restart_jobs().unwrap();
        assert_eq!(store.workspace().unwrap(), before);
        assert_eq!(store.jobs().unwrap(), jobs);
    }
}
#[test]
fn fresh_session_provenance_is_atomic_and_cannot_be_forged_after_dispatch() {
    let (_, store, _, job) = fixture();
    store
        .update_job(&job, "queued", &json!({"stage":"starting"}))
        .unwrap();
    assert_eq!(
        store
            .update_job(
                &job,
                "starting",
                &json!({"freshSessionIds":{"planner":"not-recorded"}})
            )
            .unwrap_err()
            .code(),
        "invalidJobPatch"
    );
    store.update_job(&job,"starting",&json!({"stage":"running","sessionIds":{"planner":"new"},"freshSessionIds":{"planner":"new"}})).unwrap();
    assert_eq!(
        store.jobs().unwrap()[0]["freshSessionIds"]["planner"],
        "new"
    );
    store
        .update_job(
            &job,
            "running",
            &json!({"promptDispatch":{"planner":"dispatching"}}),
        )
        .unwrap();
    assert!(store.jobs().unwrap()[0]["sessionDispatchDays"]["planner"].is_string());
    assert_eq!(
        store
            .update_job(
                &job,
                "running",
                &json!({"sessionIds":{"planner":"new"},"freshSessionIds":{"planner":"new"}})
            )
            .unwrap_err()
            .code(),
        "invalidJobPatch"
    );
}
#[test]
fn session_daily_cost_is_aggregated_without_command_revision_changes() {
    let (_, store, _, job) = fixture();
    recorded_turn(&store, &job);
    let revision = project(&store)["revision"].clone();
    store.update_job(&job,"running",&json!({"sessionUsage":{"planner":{"cost":0.5,"tokens":12,"day":"2026-10-01","dailyCost":0.5}}})).unwrap();
    assert_eq!(project(&store)["spentToday"], 0.5);
    assert_eq!(project(&store)["spendDay"], "2026-10-01");
    assert_eq!(project(&store)["revision"], revision);
    store.update_job(&job,"running",&json!({"sessionUsage":{"planner":{"cost":0.7,"tokens":14,"day":"2026-10-02","dailyCost":null}}})).unwrap();
    assert!(store.jobs().unwrap()[0]["usage"]["dailyCost"].is_null());
}
