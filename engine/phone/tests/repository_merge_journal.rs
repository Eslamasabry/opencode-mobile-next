//! Crash fixtures operate on private native state, never agent-authored metadata.
use git2::{Oid, Repository, Signature};
use hmac::{Hmac, Mac};
use oc_phone_engine::repository::RepositoryAuthority;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha2::Sha256;
use std::{fs, path::Path};

const FINGERPRINT: &str = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Journal {
    schema_version: u32,
    repo_id: String,
    task_id: String,
    evidence_id: String,
    evidence_fingerprint: String,
    expected_dev: String,
    expected_task: String,
    expected_main: String,
    next_dev: String,
    state: String,
}
struct Fixture {
    _temp: tempfile::TempDir,
    private: std::path::PathBuf,
    workers: std::path::PathBuf,
    authority: RepositoryAuthority,
    before: String,
    checked: String,
}
fn commit(repo: &Repository, text: &str) -> Oid {
    fs::write(repo.workdir().unwrap().join("result.txt"), text).unwrap();
    let mut index = repo.index().unwrap();
    index.add_path(Path::new("result.txt")).unwrap();
    index.write().unwrap();
    let tree_id = index.write_tree().unwrap();
    let tree = repo.find_tree(tree_id).unwrap();
    let signature = Signature::now("Fixture", "fixture@example.invalid").unwrap();
    let parent = repo.head().ok().map(|head| head.peel_to_commit().unwrap());
    let parents: Vec<_> = parent.iter().collect();
    repo.commit(Some("HEAD"), &signature, &signature, text, &tree, &parents)
        .unwrap()
}
impl Fixture {
    fn new() -> Self {
        let scratch = std::env::var_os("OC_ENGINE_TEST_ROOT")
            .map(std::path::PathBuf::from)
            .unwrap_or_else(|| "/home/eslam/Storage/tmp/aiteam-phone-engine-tests".into());
        assert!(scratch.is_absolute() && !scratch.starts_with("/tmp"));
        fs::create_dir_all(&scratch).unwrap();
        let temp = tempfile::tempdir_in(scratch).unwrap();
        let source = temp.path().join("source");
        fs::create_dir(&source).unwrap();
        let source_repo = Repository::init(&source).unwrap();
        source_repo.set_head("refs/heads/main").unwrap();
        let before = commit(&source_repo, "before").to_string();
        let private = temp.path().join("private");
        let workers = temp.path().join("workers");
        let authority = RepositoryAuthority::new(private.clone(), workers.clone()).unwrap();
        authority.import_repo("repo", &source).unwrap();
        let worker = authority.prepare_worker("repo", "task").unwrap();
        let worker = Repository::open(worker["workerPath"].as_str().unwrap()).unwrap();
        let checked = commit(&worker, "after").to_string();
        authority.collect_worker("repo", "task", &before).unwrap();
        Self {
            _temp: temp,
            private,
            workers,
            authority,
            before,
            checked,
        }
    }
    fn merge(&self) -> Value {
        self.authority
            .merge_dev_for_evidence(
                "repo",
                "task",
                &self.before,
                &self.checked,
                "job",
                FINGERPRINT,
            )
            .unwrap()
    }
    fn reopen(&self) -> RepositoryAuthority {
        RepositoryAuthority::new(self.private.clone(), self.workers.clone()).unwrap()
    }
    fn journal_path(&self) -> std::path::PathBuf {
        fs::read_dir(self.private.join("merges/repo"))
            .unwrap()
            .map(|entry| entry.unwrap().path())
            .find(|p| p.extension().is_some_and(|e| e == "json"))
            .unwrap()
    }
    // Reconstitute the exact signed prepared write that precedes the ref update.
    // The fixture acts as the native process, holding its private signing key.
    fn simulate_crash(&self, before_ref: bool) {
        let path = self.journal_path();
        let signed: Value = serde_json::from_slice(&fs::read(&path).unwrap()).unwrap();
        let mut journal: Journal = serde_json::from_value(signed["journal"].clone()).unwrap();
        journal.state = "prepared".into();
        let key = fs::read(self.private.join("merges/authentication.key")).unwrap();
        let mut mac = Hmac::<Sha256>::new_from_slice(&key).unwrap();
        mac.update(&serde_json::to_vec(&journal).unwrap());
        let authentication: String = mac
            .finalize()
            .into_bytes()
            .iter()
            .map(|b| format!("{b:02x}"))
            .collect();
        fs::write(
            path,
            serde_json::to_vec(
                &serde_json::json!({"journal":journal,"authentication":authentication}),
            )
            .unwrap(),
        )
        .unwrap();
        if before_ref {
            let canonical = Repository::open_bare(self.private.join("repos/repo.git")).unwrap();
            canonical
                .reference(
                    "refs/heads/dev",
                    Oid::from_str(&self.before).unwrap(),
                    true,
                    "simulate pre-ref death",
                )
                .unwrap();
        }
    }
    fn recover(&self, authority: &RepositoryAuthority) -> Value {
        authority
            .recover_merge_dev("repo", "task", &self.checked, "job", FINGERPRINT)
            .unwrap()
            .unwrap()
    }
}

#[test]
fn prepared_merge_before_ref_death_recovers_exact_receipt_once() {
    let f = Fixture::new();
    let receipt = f.merge();
    f.simulate_crash(true);
    let reopened = f.reopen();
    assert_eq!(f.recover(&reopened), receipt);
    assert_eq!(f.recover(&reopened), receipt);
    assert_eq!(
        reopened
            .merge_dev_for_evidence("repo", "task", &f.before, &f.checked, "job", FINGERPRINT)
            .unwrap(),
        receipt
    );
    assert_eq!(receipt["before"]["devCommit"], f.before);
    assert_eq!(receipt["after"]["devCommit"], f.checked);
    assert_eq!(reopened.refs("repo").unwrap()["mainCommit"], f.before);
}

#[test]
fn prepared_merge_after_ref_death_recovers_original_before_not_current_dev() {
    let f = Fixture::new();
    let receipt = f.merge();
    f.simulate_crash(false);
    let reopened = f.reopen();
    assert_eq!(f.recover(&reopened), receipt);
    assert_eq!(receipt["before"]["devCommit"], f.before);
    let data: Value = serde_json::from_slice(&fs::read(f.journal_path()).unwrap()).unwrap();
    assert_eq!(data["journal"]["state"], "applied");
}

#[test]
fn journal_tampering_or_changed_checker_evidence_never_recovers_authority() {
    let f = Fixture::new();
    f.merge();
    let different = "1123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assert_eq!(
        f.authority
            .recover_merge_dev("repo", "task", &f.checked, "job", different)
            .unwrap_err()
            .code(),
        "merge_evidence_conflict"
    );
    assert_eq!(
        f.authority
            .recover_merge_dev("repo", "task", &f.before, "job", FINGERPRINT)
            .unwrap_err()
            .code(),
        "merge_evidence_conflict"
    );
    assert_eq!(
        f.authority
            .recover_merge_dev("repo", "other", &f.checked, "job", FINGERPRINT)
            .unwrap(),
        None
    );
    let path = f.journal_path();
    let mut signed: Value = serde_json::from_slice(&fs::read(&path).unwrap()).unwrap();
    signed["journal"]["nextDev"] = Value::String(f.before.clone());
    fs::write(path, serde_json::to_vec(&signed).unwrap()).unwrap();
    assert_eq!(
        f.authority
            .recover_merge_dev("repo", "task", &f.checked, "job", FINGERPRINT)
            .unwrap_err()
            .code(),
        "invalid_merge_journal"
    );
}

#[test]
fn applied_journal_cannot_publish_over_rewritten_dev_or_changed_main() {
    let f = Fixture::new();
    f.merge();
    let canonical = Repository::open_bare(f.private.join("repos/repo.git")).unwrap();
    canonical
        .reference(
            "refs/heads/dev",
            Oid::from_str(&f.before).unwrap(),
            true,
            "simulate unexpected rewrite",
        )
        .unwrap();
    assert_eq!(
        f.authority
            .recover_merge_dev("repo", "task", &f.checked, "job", FINGERPRINT)
            .unwrap_err()
            .code(),
        "merge_recovery_refs_changed"
    );
    canonical
        .reference(
            "refs/heads/dev",
            Oid::from_str(&f.checked).unwrap(),
            true,
            "restore dev",
        )
        .unwrap();
    canonical
        .reference(
            "refs/heads/main",
            Oid::from_str(&f.checked).unwrap(),
            true,
            "fixture native main change",
        )
        .unwrap();
    assert_eq!(
        f.authority
            .recover_merge_dev("repo", "task", &f.checked, "job", FINGERPRINT)
            .unwrap_err()
            .code(),
        "merge_recovery_refs_changed"
    );
}

#[test]
fn malformed_journal_is_an_explicit_refusal_not_a_missing_receipt() {
    let f = Fixture::new();
    f.merge();
    fs::write(f.journal_path(), b"{truncated").unwrap();
    assert_eq!(
        f.authority
            .recover_merge_dev("repo", "task", &f.checked, "job", FINGERPRINT)
            .unwrap_err()
            .code(),
        "invalid_merge_journal"
    );
    assert_eq!(f.authority.refs("repo").unwrap()["devCommit"], f.checked);
}

#[test]
fn repo_collection_sweeps_its_journals_and_keeps_only_private_authentication_key() {
    let f = Fixture::new();
    let receipt = f.merge();
    f.authority
        .collect_unreferenced_repositories(&["repo".into()])
        .unwrap();
    assert_eq!(f.recover(&f.authority), receipt);
    f.authority.collect_unreferenced_repositories(&[]).unwrap();
    assert!(!f.private.join("merges/repo").exists());
    assert!(f.private.join("merges/authentication.key").is_file());
    assert_eq!(
        f.authority
            .recover_merge_dev("repo", "task", &f.checked, "job", FINGERPRINT)
            .unwrap(),
        None
    );
}
