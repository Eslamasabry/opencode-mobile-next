use oc_phone_engine::store::Store;
use serde_json::{json, Value};
use std::{fs, path::PathBuf};
fn fixture() -> (tempfile::TempDir, Store, String) {
    let parent = std::env::var_os("OC_ENGINE_TEST_ROOT")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/home/eslam/Storage/tmp/oc-phone-engine-tests"));
    fs::create_dir_all(&parent).unwrap();
    let root = tempfile::Builder::new()
        .prefix("resolved-models-")
        .tempdir_in(parent)
        .unwrap();
    let store = Store::open(root.path(), "p").unwrap();
    let created=store.execute(&json!({"requestId":"create","action":"createProject","name":"Durable model",
        "settings":{"mode":"single","maxLanes":1,"reviewLevel":"milestones","maxFixRounds":0,"chargingOnly":false,"budget":{"chosen":true,"unlimited":true}},
        "spec":{"goal":"Pin effective model","milestones":[{"id":"m","title":"Safe","criteria":["Durable"]}]},
        "repos":[{"id":"repo","serverId":"phone","path":"/root/work/models","devCommit":"seed","mainCommit":"seed"}]})).unwrap();
    assert_eq!(store.execute(&json!({"requestId":"approve","action":"approveSpec","projectId":created["projectId"],"expectedRevision":0})).unwrap()["accepted"],true);
    let id = store.jobs().unwrap()[0]["id"].as_str().unwrap().to_owned();
    (root, store, id)
}
#[test]
fn resolved_models_merge_persist_and_do_not_replace_authored_choices() {
    let (root, store, id) = fixture();
    let before = store.jobs().unwrap()[0].clone();
    store
        .update_job(
            &id,
            "queued",
            &json!({"resolvedModels":{"planner":"zai-coding-plan/glm-5.3"}}),
        )
        .unwrap();
    let updated=store.update_job(&id,"queued",&json!({"resolvedModels":{"worker":"provider/worker-model","checker":"provider/checker-model"}})).unwrap();
    assert_eq!(
        updated["resolvedModels"],
        json!({"planner":"zai-coding-plan/glm-5.3","worker":"provider/worker-model","checker":"provider/checker-model"})
    );
    assert_eq!(updated["model"], before["model"]);
    assert_eq!(updated["checkerRole"], before["checkerRole"]);
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    assert_eq!(store.jobs().unwrap()[0], updated);
}
#[test]
fn invalid_resolved_model_objects_refuse_atomically() {
    let (_, store, id) = fixture();
    let before = store.jobs().unwrap();
    for value in [
        Value::Null,
        json!([]),
        json!({}),
        json!({"unknown":"provider/model"}),
        json!({"planner":""}),
        json!({"planner":"model"}),
        json!({"planner":"/model"}),
        json!({"planner":"provider/"}),
        json!({"planner":"provider/model with spaces"}),
        json!({"planner":123}),
        json!({"planner":"provider/valid","checker":"invalid"}),
    ] {
        assert_eq!(
            store
                .update_job(&id, "queued", &json!({"resolvedModels":value}))
                .unwrap_err()
                .code(),
            "invalidResolvedModel"
        );
        assert_eq!(store.jobs().unwrap(), before);
    }
}
#[test]
fn resolved_role_cannot_change_and_exact_replay_survives_session_dispatch() {
    let (_, store, id) = fixture();
    store
        .update_job(
            &id,
            "queued",
            &json!({"resolvedModels":{"planner":"provider/model"}}),
        )
        .unwrap();
    assert_eq!(
        store
            .update_job(
                &id,
                "queued",
                &json!({"resolvedModels":{"planner":"provider/other"}})
            )
            .unwrap_err()
            .code(),
        "resolvedModelAlreadyRecorded"
    );
    store
        .update_job(&id, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store.update_job(&id,"starting",&json!({"stage":"running","sessionIds":{"planner":"bound-session"},"promptDispatch":{"planner":"dispatched"}})).unwrap();
    let before = store.jobs().unwrap()[0].clone();
    let updated = store
        .update_job(
            &id,
            "running",
            &json!({"resolvedModels":{"planner":"provider/model"}}),
        )
        .unwrap();
    assert_eq!(updated["resolvedModels"], before["resolvedModels"]);
    assert_eq!(updated["sessionIds"], before["sessionIds"]);
    assert_eq!(updated["promptDispatch"], before["promptDispatch"]);
    assert_eq!(
        store
            .update_job(
                &id,
                "running",
                &json!({"resolvedModels":{"planner":"provider/other"}})
            )
            .unwrap_err()
            .code(),
        "resolvedModelAlreadyRecorded"
    );
    store.recover().unwrap();
    assert_eq!(
        store.jobs().unwrap()[0]["resolvedModels"]["planner"],
        "provider/model"
    );
}
#[test]
fn first_resolution_after_session_or_in_same_creation_patch_is_refused() {
    let (_, store, id) = fixture();
    store
        .update_job(&id, "queued", &json!({"stage":"starting"}))
        .unwrap();
    let before = store.jobs().unwrap();
    assert_eq!(store.update_job(&id,"starting",&json!({"stage":"running","sessionIds":{"planner":"new"},"resolvedModels":{"planner":"provider/model"}})).unwrap_err().code(),"modelResolutionTooLate");
    assert_eq!(store.jobs().unwrap(), before);
    store
        .update_job(
            &id,
            "starting",
            &json!({"stage":"running","sessionIds":{"planner":"existing"}}),
        )
        .unwrap();
    assert_eq!(
        store
            .update_job(
                &id,
                "running",
                &json!({"resolvedModels":{"planner":"provider/model"}})
            )
            .unwrap_err()
            .code(),
        "modelResolutionTooLate"
    );
    store
        .update_job(
            &id,
            "running",
            &json!({"promptDispatch":{"planner":"dispatching"}}),
        )
        .unwrap();
    assert_eq!(
        store
            .update_job(
                &id,
                "running",
                &json!({"resolvedModels":{"planner":"provider/model"}})
            )
            .unwrap_err()
            .code(),
        "modelResolutionTooLate"
    );
}
#[test]
fn role_checkpoint_uses_stage_cas_and_does_not_partially_merge_on_conflict() {
    let (_, store, id) = fixture();
    store
        .update_job(
            &id,
            "queued",
            &json!({"resolvedModels":{"planner":"provider/model"}}),
        )
        .unwrap();
    store
        .update_job(&id, "queued", &json!({"stage":"starting"}))
        .unwrap();
    let before = store.jobs().unwrap();
    assert_eq!(
        store
            .update_job(
                &id,
                "queued",
                &json!({"resolvedModels":{"checker":"provider/checker"}})
            )
            .unwrap_err()
            .code(),
        "staleJobStage"
    );
    assert_eq!(
        store
            .update_job(
                &id,
                "starting",
                &json!({"resolvedModels":{"checker":"provider/checker","planner":"provider/other"}})
            )
            .unwrap_err()
            .code(),
        "resolvedModelAlreadyRecorded"
    );
    assert_eq!(store.jobs().unwrap(), before);
}
