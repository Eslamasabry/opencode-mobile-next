use oc_phone_engine::store::Store;
use serde_json::{json, Value};
use std::{
    fs,
    path::{Path, PathBuf},
};
use tempfile::TempDir;

const SEED: &str = "1111111111111111111111111111111111111111";
const TASK: &str = "2222222222222222222222222222222222222222";

fn storage() -> TempDir {
    let root = std::env::var_os("OC_ENGINE_TEST_ROOT")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/home/eslam/Storage/tmp/oc-phone-engine-tests"));
    fs::create_dir_all(&root).unwrap();
    tempfile::Builder::new()
        .prefix("merge-projection-")
        .tempdir_in(root)
        .unwrap()
}
fn completed(store: &Store, receipt: bool) -> String {
    let c = json!({"requestId":"create","action":"createQuickTask","confirmed":true,"name":"Checked merge","settings":{"mode":"single","maxLanes":1,"reviewLevel":"milestones","maxFixRounds":0,"chargingOnly":false,"budget":{"chosen":true,"unlimited":true}},"spec":{"goal":"Implement verified change","milestones":[{"id":"milestone","title":"Works","criteria":["Verified change"]}]},"repos":[{"id":"repo","serverId":"phone","path":"/workspace/source","devCommit":SEED,"mainCommit":SEED}]});
    assert_eq!(store.execute(&c).unwrap()["accepted"], true);
    let job = store.jobs().unwrap().remove(0);
    let id = job["id"].as_str().unwrap();
    store
        .update_job(id, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store
        .update_job(
            id,
            "starting",
            &json!({"stage":"running","sessionIds":{"worker":"ses_worker"}}),
        )
        .unwrap();
    store
        .update_job(
            id,
            "running",
            &json!({"stage":"checking","taskCommit":TASK,"sessionIds":{"checker":"ses_checker"}}),
        )
        .unwrap();
    store.update_job(id, "checking", &json!({"stage":"mergeReady","findings":[],"criterionResults":[{"criterion":"Verified change","status":"met"}]})).unwrap();
    store
        .update_job(id, "mergeReady", &json!({"stage":"merging"}))
        .unwrap();
    let mut finished = json!({"stage":"completed","mergedCommit":TASK});
    if receipt {
        finished["repoReceipt"] = json!({"repoId":"repo","taskId":job["taskId"],"taskCommit":TASK,"devCommit":TASK,"mainCommit":SEED,"before":{"devCommit":SEED,"mainCommit":SEED},"after":{"devCommit":TASK,"mainCommit":SEED}});
    }
    store.update_job(id, "merging", &finished).unwrap();
    id.to_owned()
}
fn db(root: &Path) -> PathBuf {
    root.join("oc.teamEngine.p/state.sqlite3")
}
fn raw_workspace(root: &Path) -> Value {
    let conn = rusqlite::Connection::open(db(root)).unwrap();
    serde_json::from_str(
        &conn
            .query_row("SELECT data FROM workspace WHERE id=1", [], |r| {
                r.get::<_, String>(0)
            })
            .unwrap(),
    )
    .unwrap()
}
fn persist_fixture(root: &Path, workspace: &Value, job: &Value) {
    let mut conn = rusqlite::Connection::open(db(root)).unwrap();
    let tx = conn.transaction().unwrap();
    tx.execute(
        "UPDATE workspace SET data=?1 WHERE id=1",
        [workspace.to_string()],
    )
    .unwrap();
    tx.execute(
        "UPDATE jobs SET data=?1 WHERE id=?2",
        rusqlite::params![job.to_string(), job["id"].as_str().unwrap()],
    )
    .unwrap();
    tx.commit().unwrap();
}
fn queue(store: &Store) -> Value {
    store.workspace().unwrap()["projects"][0]["mergeQueue"].clone()
}
fn assert_blocked(store: &Store) {
    let q = queue(store);
    assert_eq!(q.as_array().unwrap().len(), 1);
    assert_eq!(q[0]["status"], "blocked");
    assert_eq!(q[0]["checksPassed"], false);
    assert!(q[0]["reason"].as_str().unwrap().len() > 0);
}

#[test]
fn checked_scoped_receipt_projects_stable_merged_queue_without_read_mutation() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    let id = completed(&store, true);
    let job = store.jobs().unwrap().remove(0);
    let before = raw_workspace(root.path());
    let events = store.events(0, 100).unwrap();
    let expected = json!([{"id":format!("merge-{id}"),"taskId":job["taskId"],"repoId":"repo","status":"merged","reason":"","checksPassed":true}]);
    for _ in 0..3 {
        assert_eq!(queue(&store), expected);
    }
    assert_eq!(raw_workspace(root.path()), before);
    assert_eq!(store.events(0, 100).unwrap(), events);
    assert_eq!(
        store.workspace().unwrap()["projects"][0]["revision"],
        before["projects"][0]["revision"]
    );
    assert_eq!(job["criterionResults"][0]["status"], "met");
    assert_eq!(job["findings"], json!([]));
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    assert_eq!(queue(&store), expected);
    assert_eq!(raw_workspace(root.path()), before);
}

#[test]
fn completed_stage_without_canonical_merge_receipt_never_grants_passed_authority() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    completed(&store, false);
    assert_blocked(&store);
    assert_eq!(queue(&store)[0]["reason"], "mergeReceiptMissing");
}

#[test]
fn malformed_scopes_refs_and_failed_checks_block_completed_merge_projection() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    completed(&store, true);
    let original_workspace = raw_workspace(root.path());
    let original_job = store.jobs().unwrap().remove(0);
    type Mutator = fn(&mut Value, &mut Value);
    let mutants: &[Mutator] = &[
        |_, j| {
            j["repoReceipt"]["repoId"] = json!("other-repo");
        },
        |_, j| {
            j["repoReceipt"]["taskId"] = json!("other-task");
        },
        |w, _| {
            w["projects"][0]["receipts"][0]["id"] = json!("other-job");
        },
        |w, _| {
            w["projects"][0]["receipts"][0]["kind"] = json!("promote");
        },
        |w, _| {
            w["projects"][0]["receipts"][0]["taskId"] = json!("other-task");
        },
        |w, _| {
            w["projects"][0]["receipts"][0]["afterRefs"]["devCommit"] = json!(SEED);
        },
        |_, j| {
            j["repoReceipt"]["taskCommit"] = json!(SEED);
        },
        |_, j| {
            j["criterionResults"][0]["status"] = json!("unmet");
        },
        |_, j| {
            j["criterionResults"][0]["status"] = json!("notApplicable");
        },
        |_, j| {
            j["findings"] = json!([{"severity":"major","criterion":"Verified change","text":"Unresolved","status":"open"}]);
        },
        |w, _| {
            w["projects"][0]["tasks"][0]["findings"] = json!([{"severity":"minor","criterion":"Verified change","text":"Unresolved","status":"open"}]);
        },
        |w, _| {
            w["projects"][0]["tasks"][0]["criteria"] = json!(["Changed acceptance criteria"]);
        },
        |w, _| {
            w["projects"][0]["tasks"][0]["status"] = json!("checked");
        },
        |_, j| {
            j["projectId"] = json!("other-project");
        },
    ];
    for (n, mutate) in mutants.iter().enumerate() {
        let mut w = original_workspace.clone();
        let mut j = original_job.clone();
        mutate(&mut w, &mut j);
        persist_fixture(root.path(), &w, &j);
        if n == mutants.len() - 1 {
            assert!(queue(&store).as_array().unwrap().is_empty());
        } else {
            assert_blocked(&store);
        }
    }
}

#[test]
fn legacy_task_only_checker_snapshot_reopens_with_exact_scope_and_fails_closed() {
    let root = storage();
    let store = Store::open(root.path(), "p").unwrap();
    completed(&store, true);
    let mut w = raw_workspace(root.path());
    let mut j = store.jobs().unwrap().remove(0);
    j.as_object_mut().unwrap().remove("findings");
    j.as_object_mut().unwrap().remove("criterionResults");
    w["projects"][0]["receipts"][0]
        .as_object_mut()
        .unwrap()
        .remove("taskId");
    persist_fixture(root.path(), &w, &j);
    let projected = queue(&store);
    assert_eq!(projected[0]["checksPassed"], true);
    assert_eq!(projected[0]["status"], "merged");
    drop(store);
    let store = Store::open(root.path(), "p").unwrap();
    assert_eq!(queue(&store), projected);
    assert_eq!(raw_workspace(root.path()), w);
    w["projects"][0]["tasks"][0]["criterionResults"][0]["status"] = json!("unmet");
    persist_fixture(root.path(), &w, &j);
    assert_blocked(&store);
    // A partial new job snapshot cannot silently use task-only legacy fallback.
    j["findings"] = json!([]);
    w["projects"][0]["tasks"][0]["criterionResults"][0]["status"] = json!("met");
    persist_fixture(root.path(), &w, &j);
    assert_blocked(&store);
}
