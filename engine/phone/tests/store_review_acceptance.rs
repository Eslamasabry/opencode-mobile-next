use oc_phone_engine::store::Store;
use serde_json::{json, Value};
use std::{fs, path::PathBuf};

fn fixture() -> (tempfile::TempDir, Store, String) {
    let parent = std::env::var_os("OC_ENGINE_TEST_ROOT")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/home/eslam/Storage/tmp/oc-phone-engine-tests"));
    fs::create_dir_all(&parent).unwrap();
    let root = tempfile::Builder::new()
        .prefix("review-acceptance-")
        .tempdir_in(parent)
        .unwrap();
    let store = Store::open(root.path(), "p").unwrap();
    let created = store.execute(&json!({"requestId":"create","action":"createProject","name":"Reviewed phases",
        "settings":{"mode":"parallel","maxLanes":2,"reviewLevel":"milestones","maxFixRounds":0,"chargingOnly":false,"budget":{"chosen":true,"unlimited":true}},
        "spec":{"goal":"Accept scoped work","milestones":[{"id":"m1","title":"First","criteria":["One"]},{"id":"m2","title":"Second","criteria":["Two"]}]},
        "repos":[{"id":"repo","serverId":"phone","path":"/root/work/review","devCommit":"seed","mainCommit":"seed"}]})).unwrap();
    let id = created["projectId"].as_str().unwrap().to_owned();
    assert_eq!(store.execute(&json!({"requestId":"spec","action":"approveSpec","projectId":id,"expectedRevision":0})).unwrap()["accepted"],true);
    let planner = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    store
        .update_job(&planner, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store
        .update_job(&planner, "starting", &json!({"stage":"running"}))
        .unwrap();
    store.update_job(&planner,"running",&json!({"stage":"completed","plan":{"phases":[{"id":"p1","title":"First","milestoneId":"m1"},{"id":"p2","title":"Second","milestoneId":"m2"}],
        "tasks":[{"id":"t1","title":"First","phaseId":"p1","roleId":"worker","repoId":"repo","serverId":"phone","criteria":["One"],"dependsOn":[]},
            {"id":"t2","title":"Second","phaseId":"p2","roleId":"worker","repoId":"repo","serverId":"phone","criteria":["Two"],"dependsOn":[]}]}})).unwrap();
    assert_eq!(
        store
            .execute(&command(&store, &id, "plan", "approvePlan", ""))
            .unwrap()["accepted"],
        true
    );
    (root, store, id)
}
fn project(store: &Store) -> Value {
    store.workspace().unwrap()["projects"][0].clone()
}
fn command(store: &Store, id: &str, request: &str, action: &str, target: &str) -> Value {
    json!({"requestId":request,"action":action,"projectId":id,"expectedRevision":project(store)["revision"],"targetId":target})
}
fn merge(store: &Store, task: &str, next: &str) {
    let id = store
        .jobs()
        .unwrap()
        .into_iter()
        .find(|j| j["taskId"] == task)
        .unwrap()["id"]
        .as_str()
        .unwrap()
        .to_owned();
    store
        .update_job(&id, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store
        .update_job(&id, "starting", &json!({"stage":"running"}))
        .unwrap();
    store
        .update_job(&id, "running", &json!({"stage":"checking"}))
        .unwrap();
    store.update_job(&id,"checking",&json!({"stage":"mergeReady","findings":[],"criterionResults":[{"criterion":if task=="t1" {"One"} else {"Two"},"status":"met"}]})).unwrap();
    store
        .update_job(&id, "mergeReady", &json!({"stage":"merging"}))
        .unwrap();
    let p = project(store);
    let before = p["repos"][0]["devCommit"].clone();
    store.update_job(&id,"merging",&json!({"stage":"completed","mergedCommit":next,"repoReceipt":{"repoId":"repo","taskId":task,"taskCommit":next,"devCommit":next,"mainCommit":"seed",
        "before":{"devCommit":before,"mainCommit":"seed"},"after":{"devCommit":next,"mainCommit":"seed"}}})).unwrap();
}
#[test]
fn phase_requires_merged_scoped_tasks_and_acceptance_survives_reopen() {
    let (root, store, id) = fixture();
    let before = project(&store);
    assert_eq!(
        store
            .execute(&command(&store, &id, "early", "acceptPhase", "p1"))
            .unwrap()["code"],
        "phaseNotReady"
    );
    assert_eq!(project(&store), before);
    merge(&store, "t1", "first");
    let repos = project(&store)["repos"].clone();
    assert_eq!(
        store
            .execute(&command(&store, &id, "phase", "acceptPhase", "p1"))
            .unwrap()["accepted"],
        true
    );
    assert_eq!(project(&store)["phases"][0]["accepted"], true);
    assert_eq!(project(&store)["phases"][1]["accepted"], false);
    assert_eq!(project(&store)["tasks"][1]["status"], "queued");
    assert_eq!(project(&store)["repos"], repos);
    assert_eq!(project(&store)["status"], "running");
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    assert_eq!(project(&store)["phases"][0]["accepted"], true);
}
#[test]
fn milestone_requires_phase_review_and_only_accepts_its_own_scope() {
    let (_, store, id) = fixture();
    merge(&store, "t1", "first");
    assert_eq!(
        store
            .execute(&command(&store, &id, "early", "acceptMilestone", "m1"))
            .unwrap()["code"],
        "milestoneNotReady"
    );
    assert_eq!(
        store
            .execute(&command(&store, &id, "phase", "acceptPhase", "p1"))
            .unwrap()["accepted"],
        true
    );
    assert_eq!(
        store
            .execute(&command(&store, &id, "milestone", "acceptMilestone", "m1"))
            .unwrap()["accepted"],
        true
    );
    assert_eq!(
        project(&store)["specDraft"]["milestones"][0]["accepted"],
        true
    );
    assert_eq!(
        project(&store)["specDraft"]["milestones"][1]["accepted"],
        Value::Null
    );
    assert_eq!(project(&store)["repos"][0]["mainCommit"], "seed");
    assert_eq!(project(&store)["status"], "running");
    assert_eq!(
        store
            .execute(&command(&store, &id, "other", "acceptMilestone", "m2"))
            .unwrap()["code"],
        "milestoneNotReady"
    );
}
#[test]
fn review_commands_reject_stale_revision_and_invalid_target_without_mutation() {
    let (_, store, id) = fixture();
    merge(&store, "t1", "first");
    let stale = command(&store, &id, "stale", "acceptPhase", "p1");
    merge(&store, "t2", "second");
    let before = project(&store);
    assert_eq!(store.execute(&stale).unwrap()["code"], "staleRevision");
    for (action, reason) in [
        ("acceptPhase", "phaseNotFound"),
        ("acceptMilestone", "milestoneNotFound"),
    ] {
        assert_eq!(
            store
                .execute(&command(&store, &id, action, action, "missing"))
                .unwrap()["code"],
            reason
        );
    }
    assert_eq!(project(&store), before);
}
#[test]
fn accepting_every_phase_and_milestone_does_not_promote_or_mark_done() {
    let (_, store, id) = fixture();
    merge(&store, "t1", "first");
    merge(&store, "t2", "second");
    for (phase, milestone) in [("p1", "m1"), ("p2", "m2")] {
        assert_eq!(
            store
                .execute(&command(&store, &id, phase, "acceptPhase", phase))
                .unwrap()["accepted"],
            true
        );
        assert_eq!(
            store
                .execute(&command(
                    &store,
                    &id,
                    milestone,
                    "acceptMilestone",
                    milestone
                ))
                .unwrap()["accepted"],
            true
        );
    }
    assert_eq!(project(&store)["status"], "running");
    assert_eq!(project(&store)["repos"][0]["mainCommit"], "seed");
    assert_eq!(project(&store)["receipts"].as_array().unwrap().len(), 2);
    assert!(project(&store)["receipts"]
        .as_array()
        .unwrap()
        .iter()
        .all(|receipt| receipt["kind"] == "merge"));
}
