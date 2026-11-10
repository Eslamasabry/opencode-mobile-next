use oc_phone_engine::store::Store;
use serde_json::{json, Value};
use std::{fs, path::PathBuf};
fn fixture(approved: bool) -> (tempfile::TempDir, Store, String) {
    let parent = std::env::var_os("OC_ENGINE_TEST_ROOT")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/home/eslam/Storage/tmp/oc-phone-engine-tests"));
    fs::create_dir_all(&parent).unwrap();
    let root = tempfile::Builder::new()
        .prefix("retry-plan-")
        .tempdir_in(parent)
        .unwrap();
    let store = Store::open(root.path(), "p").unwrap();
    let created=store.execute(&json!({"requestId":"create","action":"createProject","name":"Retry planning",
        "settings":{"mode":"single","maxLanes":1,"reviewLevel":"milestones","maxFixRounds":0,"chargingOnly":false,"budget":{"chosen":true,"unlimited":true}},
        "spec":{"goal":"Produce a proposal","milestones":[{"id":"m","title":"Safe","criteria":["Verified"]}]},
        "repos":[{"id":"repo","serverId":"phone","path":"/root/work/retry","devCommit":"seed","mainCommit":"seed"}]})).unwrap();
    let id = created["projectId"].as_str().unwrap().to_owned();
    if approved {
        assert_eq!(
            store
                .execute(&command(&store, &id, "spec", "approveSpec"))
                .unwrap()["accepted"],
            true
        );
    }
    (root, store, id)
}
fn project(store: &Store) -> Value {
    store.workspace().unwrap()["projects"][0].clone()
}
fn command(store: &Store, id: &str, request: &str, action: &str) -> Value {
    json!({"requestId":request,"action":action,"projectId":id,"expectedRevision":project(store)["revision"]})
}
fn fail(store: &Store, reason: &str) -> String {
    let id = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    store
        .update_job(&id, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store.update_job(&id,"starting",&json!({"stage":"running","directory":"/root/work/retry","sessionIds":{"planner":"original-session"},"promptDispatch":{"planner":"dispatched"},"sessionUsage":{"planner":{"cost":0.3,"tokens":11}}})).unwrap();
    store
        .update_job(
            &id,
            "running",
            &json!({"stage":"interrupted","reason":reason}),
        )
        .unwrap();
    id
}
#[test]
fn explicit_retry_uses_current_roles_without_reapproving_spec_or_replaying_turn() {
    let (root, store, id) = fixture(true);
    let old = fail(&store, "sessionFailed");
    let original = store.jobs().unwrap()[0].clone();
    let before = project(&store);
    let planner = json!({"id":"planner","name":"Planner","instructions":"Current authored instructions","model":"zai-coding-plan/glm-5.3","fallbackModel":"","readOnly":true});
    assert_eq!(
        store
            .execute(&json!({"requestId":"role","action":"saveRole","role":planner}))
            .unwrap()["accepted"],
        true
    );
    let retry = command(&store, &id, "retry", "retryPlan");
    let result = store.execute(&retry).unwrap();
    assert_eq!(result["accepted"], true);
    assert_eq!(store.execute(&retry).unwrap()["replayed"], true);
    let jobs = store.jobs().unwrap();
    assert_eq!(jobs.len(), 2);
    assert_eq!(jobs[0]["id"], old);
    assert_eq!(jobs[0]["stage"], "stopped");
    for key in [
        "sessionIds",
        "sessionUsage",
        "usage",
        "promptDispatch",
        "reason",
        "directory",
    ] {
        assert_eq!(jobs[0][key], original[key]);
    }
    assert_eq!(jobs[0]["retiredBy"], jobs[1]["id"]);
    assert_eq!(jobs[1]["stage"], "queued");
    assert_eq!(jobs[1]["readOnly"], true);
    assert_eq!(jobs[1]["model"], "zai-coding-plan/glm-5.3");
    assert_eq!(jobs[1]["instructions"], "Current authored instructions");
    assert_eq!(jobs[1]["sessionIds"], json!({}));
    assert!(jobs[1]["promptDispatch"].is_null());
    assert_eq!(project(&store)["specVersions"], before["specVersions"]);
    assert_eq!(project(&store)["specDraft"], before["specDraft"]);
    assert_eq!(project(&store)["planningState"]["jobId"], jobs[1]["id"]);
    assert_eq!(project(&store)["status"], "planning");
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    assert_eq!(store.jobs().unwrap(), jobs);
}
#[test]
fn retry_refuses_active_planner_and_stale_revision_without_creating_jobs() {
    let (_, store, id) = fixture(true);
    assert_eq!(
        store
            .execute(&command(&store, &id, "active", "retryPlan"))
            .unwrap()["code"],
        "plannerBusy"
    );
    let stale = command(&store, &id, "stale", "retryPlan");
    fail(&store, "sessionFailed");
    let before = store.jobs().unwrap();
    assert_eq!(store.execute(&stale).unwrap()["code"], "staleRevision");
    assert_eq!(store.jobs().unwrap(), before);
}
#[test]
fn retry_never_replaces_restart_or_pause_reconciliation_or_operator_stop() {
    for reason in ["restartNeedsReconciliation", "pauseNeedsReconciliation"] {
        let (_, store, id) = fixture(true);
        fail(&store, reason);
        let before = store.jobs().unwrap();
        assert_eq!(
            store
                .execute(&command(&store, &id, "retry", "retryPlan"))
                .unwrap()["code"],
            "planningNeedsReconciliation"
        );
        assert_eq!(store.jobs().unwrap(), before);
    }
    for (action, reason) in [
        ("pauseProject", "projectPaused"),
        ("stopProject", "projectStopped"),
    ] {
        let (_, store, id) = fixture(true);
        fail(&store, "sessionFailed");
        let mut control = command(&store, &id, "control", action);
        control["confirmed"] = json!(true);
        assert_eq!(store.execute(&control).unwrap()["accepted"], true);
        assert_eq!(
            store
                .execute(&command(&store, &id, "retry", "retryPlan"))
                .unwrap()["code"],
            reason
        );
    }
}
#[test]
fn retry_requires_current_approved_spec_and_failed_latest_planner() {
    let (_, store, id) = fixture(false);
    assert_eq!(
        store
            .execute(&command(&store, &id, "retry", "retryPlan"))
            .unwrap()["code"],
        "approveSpecFirst"
    );
    let (_, store, id) = fixture(true);
    fail(&store, "sessionFailed");
    let mut draft = command(&store, &id, "draft", "saveSpecDraft");
    draft["spec"] = project(&store)["specDraft"].clone();
    draft["spec"]["goal"] = json!("Changed after approval");
    assert_eq!(store.execute(&draft).unwrap()["accepted"], true);
    assert_eq!(
        store
            .execute(&command(&store, &id, "retry", "retryPlan"))
            .unwrap()["code"],
        "approveSpecFirst"
    );
}
#[test]
fn retry_refuses_approved_plan_and_cannot_restart_its_tasks() {
    let (_, store, id) = fixture(true);
    let planner = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    store
        .update_job(&planner, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store
        .update_job(&planner, "starting", &json!({"stage":"running"}))
        .unwrap();
    store.update_job(&planner,"running",&json!({"stage":"completed","plan":{"phases":[{"id":"phase","title":"Build","milestoneId":"m"}],"tasks":[{"id":"task","title":"Build","phaseId":"phase","roleId":"worker","repoId":"repo","serverId":"phone","criteria":["Verified"],"dependsOn":[]}]}})).unwrap();
    assert_eq!(
        store
            .execute(&command(&store, &id, "before-approval", "retryPlan"))
            .unwrap()["code"],
        "planningRetryUnavailable"
    );
    assert_eq!(
        store
            .execute(&command(&store, &id, "approve-plan", "approvePlan"))
            .unwrap()["accepted"],
        true
    );
    let before = store.jobs().unwrap();
    assert_eq!(
        store
            .execute(&command(&store, &id, "after-approval", "retryPlan"))
            .unwrap()["code"],
        "planAlreadyApproved"
    );
    assert_eq!(store.jobs().unwrap(), before);
}

#[test]
fn retry_refuses_ambiguous_dispatch_and_session_observation_failures() {
    for reason in [
        "promptUncertain",
        "transport_uncertain",
        "sessionCreateUncertain",
        "sessionUnknown",
        "needsAnswer",
    ] {
        let (_, store, id) = fixture(true);
        fail(&store, reason);
        let before = store.jobs().unwrap();
        assert_eq!(
            store
                .execute(&command(&store, &id, "retry", "retryPlan"))
                .unwrap()["code"],
            "planningNeedsReconciliation"
        );
        assert_eq!(store.jobs().unwrap(), before);
    }
    let (_, store, id) = fixture(true);
    let planner = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    store
        .update_job(&planner, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store.update_job(&planner,"starting",&json!({"stage":"running","directory":"/root/work/retry","sessionIds":{"planner":"possibly-live"},"promptDispatch":{"planner":"dispatching"}})).unwrap();
    store
        .update_job(
            &planner,
            "running",
            &json!({"stage":"interrupted","reason":"planInvalid"}),
        )
        .unwrap();
    let before = store.jobs().unwrap();
    assert_eq!(
        store
            .execute(&command(&store, &id, "retry", "retryPlan"))
            .unwrap()["code"],
        "planningNeedsReconciliation"
    );
    assert_eq!(store.jobs().unwrap(), before);
}
#[test]
fn retry_allows_proven_pre_dispatch_failure_and_completed_invalid_proposal() {
    let (_, store, id) = fixture(true);
    let planner = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    store
        .update_job(
            &planner,
            "queued",
            &json!({"stage":"interrupted","reason":"invalid_model"}),
        )
        .unwrap();
    assert_eq!(
        store
            .execute(&command(&store, &id, "retry", "retryPlan"))
            .unwrap()["accepted"],
        true
    );
    let (_, store, id) = fixture(true);
    fail(&store, "planInvalid");
    assert_eq!(
        store
            .execute(&command(&store, &id, "retry", "retryPlan"))
            .unwrap()["accepted"],
        true
    );
}

#[test]
fn retry_after_terminal_model_auth_failure_uses_new_explicit_role_model() {
    let (_, store, id) = fixture(true);
    let old = fail(&store, "modelNotConfigured");
    let planner = json!({"id":"planner","name":"Planner","instructions":"Plan only",
        "model":"zai-coding-plan/glm-5.3-flash","fallbackModel":"","readOnly":true});
    assert_eq!(
        store
            .execute(&json!({"requestId":"role","action":"saveRole","role":planner}))
            .unwrap()["accepted"],
        true
    );
    assert_eq!(
        store
            .execute(&command(&store, &id, "retry", "retryPlan"))
            .unwrap()["accepted"],
        true
    );
    let jobs = store.jobs().unwrap();
    assert_eq!(jobs.len(), 2);
    assert_eq!(jobs[0]["id"], old);
    assert_eq!(jobs[0]["reason"], "modelNotConfigured");
    assert_eq!(jobs[0]["stage"], "stopped");
    assert_eq!(jobs[1]["model"], "zai-coding-plan/glm-5.3-flash");
    assert_eq!(jobs[1]["sessionIds"], json!({}));
    assert!(jobs[1]["promptDispatch"].is_null());
}

#[test]
fn model_failure_does_not_authorize_retry_of_uncertain_dispatch() {
    let (_, store, id) = fixture(true);
    let planner = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    store
        .update_job(&planner, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store
        .update_job(
            &planner,
            "starting",
            &json!({"stage":"running",
        "directory":"/root/work/retry","sessionIds":{"planner":"possibly-live"},
        "promptDispatch":{"planner":"dispatching"}}),
        )
        .unwrap();
    store
        .update_job(
            &planner,
            "running",
            &json!({"stage":"interrupted","reason":"modelNotConfigured"}),
        )
        .unwrap();
    let before = store.jobs().unwrap();
    assert_eq!(
        store
            .execute(&command(&store, &id, "retry", "retryPlan"))
            .unwrap()["code"],
        "planningNeedsReconciliation"
    );
    assert_eq!(store.jobs().unwrap(), before);
}
