//! Deferred component E2E: durable task → isolated clone → independent check
//! → canonical dev merge → expected-SHA promotion authority receipt.
//! Real server admission and on-device proot proofs are separate prerequisites.
use git2::{Repository, Signature};
use oc_phone_engine::{
    daemon::{validate_check, validate_plan},
    repository::RepositoryAuthority,
    store::Store,
};
use serde_json::{json, Value};

fn commit(repo: &Repository, parent: Option<git2::Oid>, text: &str) -> git2::Oid {
    std::fs::write(repo.workdir().unwrap().join("result.txt"), text).unwrap();
    let mut index = repo.index().unwrap();
    index.add_path(std::path::Path::new("result.txt")).unwrap();
    index.write().unwrap();
    let tree_id = index.write_tree().unwrap();
    let tree = repo.find_tree(tree_id).unwrap();
    let sig = Signature::now("Fixture", "fixture@example.invalid").unwrap();
    let parents: Vec<git2::Commit> = parent
        .map(|p| vec![repo.find_commit(p).unwrap()])
        .unwrap_or_default();
    let refs: Vec<&git2::Commit> = parents.iter().collect();
    repo.commit(Some("HEAD"), &sig, &sig, text, &tree, &refs)
        .unwrap()
}
fn project(store: &Store, id: &str) -> Value {
    store.workspace().unwrap()["projects"]
        .as_array()
        .unwrap()
        .iter()
        .find(|p| p["id"] == id)
        .unwrap()
        .clone()
}
fn command(store: &Store, id: &str, action: &str, extra: Value) -> Value {
    let mut c = json!({"requestId":format!("{action}-{}",project(store,id)["revision"]),"action":action,"projectId":id,"expectedRevision":project(store,id)["revision"]});
    for (k, v) in extra.as_object().unwrap() {
        c[k] = v.clone();
    }
    let out = store.execute(&c).unwrap();
    assert_eq!(out["accepted"], true, "{out}");
    out
}
#[test]
fn checked_clone_merges_only_to_dev_then_expected_promotion_receipt_survives_restart() {
    let scratch = std::env::var_os("OC_ENGINE_TEST_ROOT")
        .map(std::path::PathBuf::from)
        .unwrap_or_else(|| "/home/eslam/Storage/tmp/aiteam-phone-engine-tests".into());
    assert!(scratch.is_absolute() && !scratch.starts_with("/tmp"));
    std::fs::create_dir_all(&scratch).unwrap();
    let temporary = tempfile::tempdir_in(scratch).unwrap();
    let source = temporary.path().join("source");
    std::fs::create_dir(&source).unwrap();
    let repo = Repository::init(&source).unwrap();
    repo.set_head("refs/heads/main").unwrap();
    let base = commit(&repo, None, "before");
    repo.reference("refs/heads/dev", base, false, "fixture")
        .unwrap();
    let private = temporary.path().join("private");
    let workers = temporary.path().join("workers");
    let authority = RepositoryAuthority::new(private.clone(), workers.clone()).unwrap();
    let imported = authority.import_repo("repo", &source).unwrap();
    let store = Store::open(&temporary.path().join("state"), "profile").unwrap();
    let settings = json!({"mode":"single","maxLanes":1,"reviewLevel":"milestones","maxFixRounds":2,"budget":{"chosen":true,"unlimited":true}});
    let create = json!({"requestId":"create","action":"createProject","name":"Feature","settings":settings,
        "repos":[{"id":"repo","name":"Repo","serverId":"phone","path":"/root/projects/repo","devCommit":imported["devCommit"],"mainCommit":imported["mainCommit"]}],
        "spec":{"goal":"Implement feature","milestones":[{"id":"m","title":"Feature","criteria":["result is after"]}]}});
    let created = store.execute(&create).unwrap();
    assert_eq!(created["accepted"], true, "{created}");
    let pid = created["projectId"].as_str().unwrap();
    command(&store, pid, "approveSpec", json!({"confirmed":true}));
    let planner = store
        .jobs()
        .unwrap()
        .into_iter()
        .find(|j| j["kind"] == "planner")
        .unwrap();
    let planner_id = planner["id"].as_str().unwrap();
    store
        .update_job(planner_id, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store
        .update_job(
            planner_id,
            "starting",
            &json!({"stage":"running","sessionIds":{"planner":"planner-session"}}),
        )
        .unwrap();
    let plan = json!({"spec":project(&store,pid)["specDraft"],"phases":[{"id":"phase","milestoneId":"m","title":"Build"}],
        "tasks":[{"id":"task","title":"Feature","roleId":"worker","repoId":"repo","serverId":"phone","phaseId":"phase","dependsOn":[],"criteria":["result is after"]}]});
    validate_plan(&plan, "repo").unwrap();
    store
        .update_job(
            planner_id,
            "running",
            &json!({"stage":"completed","plan":plan}),
        )
        .unwrap();
    command(
        &store,
        pid,
        "approvePlan",
        json!({"confirmed":true,"tasks":plan["tasks"],"phases":plan["phases"]}),
    );
    let job = store
        .jobs()
        .unwrap()
        .into_iter()
        .find(|j| j["kind"] == "task")
        .unwrap();
    let job_id = job["id"].as_str().unwrap();
    let work = authority.prepare_worker("repo", "task").unwrap();
    store
        .update_job(job_id, "queued", &json!({"stage":"starting"}))
        .unwrap();
    store.update_job(job_id,"starting",&json!({"stage":"running","directory":"/root/aiteam/work/profile/repo/task","sessionIds":{"worker":"worker-session"}})).unwrap();
    let worker = Repository::open(work["workerPath"].as_str().unwrap()).unwrap();
    assert!(worker.find_reference("refs/heads/main").is_err());
    assert!(worker.find_remote("origin").is_err());
    commit(&worker, Some(base), "after");
    let collected = authority
        .collect_worker("repo", "task", base.to_string().as_str())
        .unwrap();
    store.update_job(job_id,"running",&json!({"stage":"checking","sessionIds":{"worker":"worker-session","checker":"checker-session"}})).unwrap();
    let verification =
        json!({"findings":[],"criterionResults":[{"criterion":"result is after","status":"met"}]});
    assert!(validate_check(&verification, &plan["tasks"][0]["criteria"]).unwrap());
    store.update_job(job_id,"checking",&json!({"stage":"mergeReady","findings":verification["findings"],"criterionResults":verification["criterionResults"]})).unwrap();
    store
        .update_job(job_id, "mergeReady", &json!({"stage":"merging"}))
        .unwrap();
    let merged = authority
        .merge_dev(
            "repo",
            "task",
            base.to_string().as_str(),
            collected["taskCommit"].as_str().unwrap(),
        )
        .unwrap();
    assert_eq!(merged["mainCommit"], base.to_string());
    store
        .update_job(
            job_id,
            "merging",
            &json!({"stage":"completed","mergedCommit":merged["devCommit"],"repoReceipt":merged}),
        )
        .unwrap();
    assert!(authority
        .promote(
            "repo",
            merged["devCommit"].as_str().unwrap(),
            base.to_string().as_str(),
            false,
            "promote"
        )
        .is_err());
    let promote = json!({"requestId":"promote","action":"promote","targetId":"repo","projectId":pid,"expectedRevision":project(&store,pid)["revision"],"expectedDevCommit":merged["devCommit"],"expectedMainCommit":base.to_string(),"confirmed":true});
    let receipt = authority
        .promote(
            "repo",
            merged["devCommit"].as_str().unwrap(),
            base.to_string().as_str(),
            true,
            "promote",
        )
        .unwrap();
    let result = store.record_promotion(&promote, &receipt).unwrap();
    assert_eq!(result["accepted"], true);
    drop(store);
    drop(authority);
    let reopened = Store::open(&temporary.path().join("state"), "profile").unwrap();
    assert_eq!(
        project(&reopened, pid)["repos"][0]["mainCommit"],
        merged["devCommit"]
    );
    assert_eq!(
        reopened.record_promotion(&promote, &receipt).unwrap()["replayed"],
        true
    );
    reopened.delete_profile().unwrap();
    assert!(reopened.workspace().is_err());
}
#[test]
fn incomplete_or_unmet_verification_cannot_authorize_merge() {
    let criteria = json!(["A", "B"]);
    assert!(validate_check(
        &json!({"findings":[],"criterionResults":[{"criterion":"A","status":"met"}]}),
        &criteria
    )
    .is_err());
    assert!(!validate_check(&json!({"findings":[],"criterionResults":[{"criterion":"A","status":"met"},{"criterion":"B","status":"unmet"}]}),&criteria).unwrap());
    assert!(validate_check(&json!({"findings":[],"criterionResults":[{"criterion":"A","status":"met"},{"criterion":"A","status":"met"}]}),&criteria).is_err());
}

#[test]
fn planner_contract_requires_title_before_approval() {
    let job = json!({"repoId":"repo","planningRoles":[{"id":"worker","instructions":"Implement approved changes"}]});
    let prompt = oc_phone_engine::daemon::planner_request(&job);
    assert!(prompt.contains("nonempty title"));
    assert!(prompt.contains("\"roleId\":{\"enum\":[\"worker\"]}"));
    assert!(prompt.contains("\"required\":[\"id\",\"title\",\"phaseId\""));
    let mut plan = json!({"spec":{},"phases":[{"id":"phase"}],"tasks":[{"id":"task","roleId":"worker","repoId":"repo","criteria":["Works"],"dependsOn":[]}]});
    assert_eq!(validate_plan(&plan, "repo"), Err("planTaskTitleRequired"));
    plan["tasks"][0]["title"] = json!(" ");
    assert_eq!(validate_plan(&plan, "repo"), Err("planTaskTitleRequired"));
    plan["tasks"][0]["title"] = json!("Build the feature");
    assert!(validate_plan(&plan, "repo").is_ok());
}

#[test]
fn single_fenced_verdict_with_explanation_is_parsed_without_guessing() {
    use oc_phone_engine::daemon::structured_output;
    let verdict = json!({"findings":[],"criterionResults":[{"criterion":"Works","status":"met"}]});
    let text =
        format!("```json\n{verdict}\n```\nVerification basis: inspected the committed task file.");
    let parsed = structured_output(&json!({"text":text})).unwrap();
    assert_eq!(parsed, verdict);
    assert!(validate_check(&parsed, &json!(["Works"])).unwrap());
    assert!(validate_check(&parsed, &json!(["Different criterion"])).is_err());
    for text in [
        format!("```json\n{verdict}\n```\n```json\n{verdict}\n```"),
        format!("```json\n{verdict}\n```\n{{\"otherVerdict\":true}}"),
        "Done! No JSON verdict.".to_owned(),
        "```json\n{invalid}\n```".to_owned(),
        format!("```javascript\n{verdict}\n```"),
    ] {
        assert!(structured_output(&json!({"text":text})).is_err());
    }
}

#[test]
fn conflicting_duplicate_verdict_keys_are_refused_at_every_depth() {
    use oc_phone_engine::daemon::structured_output;
    for text in [
        r#"{"findings":[{"severity":"major"}],"findings":[],"criterionResults":[]}"#,
        r#"{"findings":[],"criterionResults":[{"criterion":"Works","status":"unmet","status":"met"}]}"#,
        r#"{"status":"unmet","\u0073tatus":"met"}"#,
    ] {
        assert!(structured_output(&json!({"text":text})).is_err());
        assert!(structured_output(
            &json!({"text":format!("```json\n{text}\n```\nVerification basis: done.")})
        )
        .is_err());
    }
}
