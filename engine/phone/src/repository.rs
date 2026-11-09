//! Native-only canonical Git authority. Worker metadata is always untrusted.
//! This module must live outside the workers' filesystem/process boundary.
use git2::{build::RepoBuilder, Oid, Repository, Signature};
use hmac::{Hmac, Mac};
use rand::RngCore;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::collections::HashSet;
use std::ffi::{CStr, CString, OsStr};
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd};
use std::os::unix::ffi::OsStrExt;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::{Component, Path, PathBuf};

const MAIN: &str = "refs/heads/main";
const DEV: &str = "refs/heads/dev";
const MAX_SNAPSHOT_BYTES: u64 = 2 * 1024 * 1024 * 1024;
const MAX_SNAPSHOT_FILES: usize = 200_000;
const MAX_DECODED_OBJECT_BYTES: usize = 64 * 1024 * 1024;

#[derive(Debug)]
pub struct RepoError(&'static str);
impl RepoError {
    pub fn code(&self) -> &'static str {
        self.0
    }
}
impl std::fmt::Display for RepoError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.0)
    }
}
impl std::error::Error for RepoError {}
impl From<std::io::Error> for RepoError {
    fn from(_: std::io::Error) -> Self {
        Self("repository_io")
    }
}
impl From<git2::Error> for RepoError {
    fn from(_: git2::Error) -> Self {
        Self("repository_git")
    }
}
type Result<T> = std::result::Result<T, RepoError>;

/// Trusted native deletion after all users of the tree have been stopped. The
/// caller supplies the app-owned root; descendants never become trusted paths.
/// Missing roots succeed and symlinks are unlinked without following them.
pub fn erase_tree_no_links(path: &Path) -> Result<()> {
    if !path.is_absolute()
        || path
            .components()
            .any(|c| matches!(c, Component::CurDir | Component::ParentDir))
    {
        return Err(RepoError("invalid_path"));
    }
    let parent = path.parent().ok_or(RepoError("invalid_path"))?;
    let name = path.file_name().ok_or(RepoError("invalid_path"))?;
    check_ancestors(parent)?;
    let parent = open_directory(parent)?;
    let mut count = 0;
    remove_collected_child(&parent, name, 0, &mut count, true)?;
    sync_fd(&parent)
}

pub struct RepositoryAuthority {
    private_root: PathBuf,
    worker_root: PathBuf,
}
struct Lock(File);
impl Drop for Lock {
    fn drop(&mut self) {
        unsafe {
            libc::flock(self.0.as_raw_fd(), libc::LOCK_UN);
        }
    }
}
struct Snapshot(PathBuf);
impl Drop for Snapshot {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.0);
    }
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct WorkerRecord {
    repo_id: String,
    task_id: String,
    dev_commit: String,
}
#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct PromotionReceipt {
    schema_version: u32,
    repo_id: String,
    request_id: String,
    expected_dev: String,
    expected_main: String,
    confirmed: bool,
    state: String,
    before: Value,
    after: Value,
}

/// Daemon-authored durable integration intent; never read from the worker tree.
#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct MergeJournal {
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
#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct SignedMergeJournal {
    journal: MergeJournal,
    authentication: String,
}

impl RepositoryAuthority {
    pub fn new(private_root: PathBuf, worker_root: PathBuf) -> Result<Self> {
        // Reject overlap before creating either root (including ancestor symlinks).
        let private_root = absolute(&private_root)?;
        let worker_root = absolute(&worker_root)?;
        check_ancestors(&private_root)?;
        check_ancestors(&worker_root)?;
        if private_root.starts_with(&worker_root) || worker_root.starts_with(&private_root) {
            return Err(RepoError("overlapping_roots"));
        }
        secure_create_dir(&private_root)?;
        secure_create_dir(&worker_root)?;
        for name in [
            "repos",
            "workers",
            "receipts",
            "merges",
            "receipt-scopes",
            "retired",
            "staging",
            "imports",
        ] {
            secure_create_dir(&private_root.join(name))?;
        }
        Ok(Self {
            private_root,
            worker_root,
        })
    }

    pub fn import_repo(&self, repo_id: &str, source: &Path) -> Result<Value> {
        self.import_bound(repo_id, source, None)
    }

    /// Recover import completed before the command's SQLite commit. Bind the
    /// authored request, not just the source path or caller-selected repo id.
    pub fn import_repo_for_request(
        &self,
        repo_id: &str,
        source: &Path,
        request: &str,
    ) -> Result<Value> {
        if request.is_empty() || request.len() > 128 {
            return Err(RepoError("invalid_request"));
        }
        self.import_bound(repo_id, source, Some(request))
    }

    fn import_bound(&self, repo_id: &str, source: &Path, request: Option<&str>) -> Result<Value> {
        valid_id(repo_id)?;
        let _lock = self.lock()?;
        let source = absolute(source)?;
        if source.starts_with(&self.private_root)
            || self.private_root.starts_with(&source)
            || source.starts_with(&self.worker_root)
            || self.worker_root.starts_with(&source)
        {
            return Err(RepoError("unsafe_source"));
        }
        let target = self.repo_path(repo_id);
        let binding_path = self
            .private_root
            .join("imports")
            .join(format!("{repo_id}.json"));
        let binding = json!({"repoId":repo_id,"source":source,"requestId":request});
        if self
            .private_root
            .join("retired")
            .join(format!("{repo_id}.json"))
            .exists()
        {
            return Err(RepoError("repository_retired"));
        }
        if target.exists() {
            if request.is_some() && read_json::<Value>(&binding_path)? == binding {
                return self.refs_unlocked(repo_id);
            }
            return Err(RepoError("repository_exists"));
        }
        if request.is_some() {
            if binding_path.exists() && read_json::<Value>(&binding_path)? != binding {
                return Err(RepoError("import_binding_mismatch"));
            }
            atomic_json(&binding_path, &binding)?;
        }
        let snapshot = self.snapshot(&source, true)?;
        let staged = self
            .private_root
            .join("staging")
            .join(uuid::Uuid::new_v4().to_string());
        let cleanup = Snapshot(staged.clone());
        let repo = RepoBuilder::new()
            .bare(true)
            .clone_local(git2::build::CloneLocal::None)
            .clone(path_string(&snapshot.0)?, &staged)?;
        if repo.is_empty()? {
            return Err(RepoError("repository_empty"));
        }
        let seed = repo
            .find_reference(MAIN)
            .and_then(|r| r.peel_to_commit())
            .or_else(|_| repo.head().and_then(|r| r.peel_to_commit()))?
            .id();
        // Import creates the engine-owned branch policy; worker/source branch names are not authority.
        // Bare clone may materialize non-HEAD source branches as remote
        // refs. Resolve the intended source dev from the sanitized snapshot,
        // then validate its fetched object before establishing private refs.
        let imported_source = Repository::open(&snapshot.0)?;
        let initial_dev = imported_source
            .find_reference(DEV)
            .and_then(|r| r.peel_to_commit())
            .map(|c| c.id())
            .unwrap_or(seed);
        if repo.find_reference(MAIN).is_err() {
            repo.reference(MAIN, seed, false, "initial main")?;
        }
        if repo.find_reference(DEV).is_err() {
            repo.reference(DEV, initial_dev, false, "initial dev")?;
        }
        direct_commit_ref(&repo, MAIN)?;
        direct_commit_ref(&repo, DEV)?;
        if !descends(&repo, initial_dev, seed)? {
            return Err(RepoError("divergent_import"));
        }
        validate_commit_tree(&repo, seed)?;
        validate_commit_tree(&repo, initial_dev)?;
        remove_remotes(&repo)?;
        repo.set_head(DEV)?;
        install_hooks(&repo)?;
        sync_all(&staged)?;
        drop(repo);
        fs::rename(&staged, &target)?;
        sync_dir(target.parent().unwrap())?;
        drop(cleanup);
        let mut result = self.refs_unlocked(repo_id)?;
        result["before"] = json!({"devCommit":null,"mainCommit":null});
        result["after"] =
            json!({"devCommit":result["devCommit"],"mainCommit":result["mainCommit"]});
        Ok(result)
    }

    pub fn prepare_worker(&self, repo_id: &str, task_id: &str) -> Result<Value> {
        valid_id(repo_id)?;
        valid_id(task_id)?;
        let _lock = self.lock()?;
        self.prepare_worker_unlocked(repo_id, task_id)
    }

    /// Discard a clone abandoned before any session could have been dispatched.
    /// Only the trusted backend may call this after checking durable stage/session/
    /// prompt-dispatch/session-create-uncertainty evidence. Ordinary prepare never resets.
    pub fn prepare_fresh_worker(&self, repo_id: &str, task_id: &str) -> Result<Value> {
        valid_id(repo_id)?;
        valid_id(task_id)?;
        let _lock = self.lock()?;
        let canonical = self.open_repo(repo_id)?;
        commit_ref(&canonical, DEV)?;
        commit_ref(&canonical, MAIN)?;
        // A collected task contradicts the required undispatched caller proof.
        if canonical.find_reference(&incoming_ref(task_id)).is_ok() {
            return Err(RepoError("worker_reset_refused"));
        }
        let records = open_directory(&self.private_root.join("workers"))?;
        let record_name = format!("{repo_id}.{task_id}.json");
        if let Some(metadata) = child_stat(&records, OsStr::new(&record_name))? {
            if metadata.st_mode & libc::S_IFMT != libc::S_IFREG || metadata.st_nlink != 1 {
                return Err(RepoError("unsafe_repository_metadata"));
            }
            let record: WorkerRecord = read_json(&self.worker_record(repo_id, task_id))?;
            if record.repo_id != repo_id || record.task_id != task_id {
                return Err(RepoError("worker_binding_mismatch"));
            }
        }
        let root = open_directory(&self.worker_root)?;
        let parent = mkdir_child(&root, OsStr::new(repo_id), false)?;
        let mut removed = 0;
        remove_worker_child(&parent, OsStr::new(task_id), 0, &mut removed)?;
        sync_fd(&parent)?;
        unlink_child(&records, OsStr::new(&record_name), false)?;
        sync_fd(&records)?;
        self.prepare_worker_unlocked(repo_id, task_id)
    }

    fn prepare_worker_unlocked(&self, repo_id: &str, task_id: &str) -> Result<Value> {
        let canonical = self.open_repo(repo_id)?;
        let dev = commit_ref(&canonical, DEV)?;
        let main = commit_ref(&canonical, MAIN)?;
        validate_commit_tree(&canonical, dev)?;
        let path = self.worker_path(repo_id, task_id);
        if fs::symlink_metadata(&path).is_ok() {
            return Err(RepoError("worker_exists"));
        }
        // Build privately first, then export through directory descriptors. Agent-controlled
        // path replacement must never redirect a privileged libgit2 checkout into private state.
        let staged = self
            .private_root
            .join("staging")
            .join(uuid::Uuid::new_v4().to_string());
        let cleanup = Snapshot(staged.clone());
        let worker = Repository::init(&staged)?;
        // Anonymous transport copies objects: no linked .git, alternates, hardlinks, origin or main.
        let mut fetch_options = git2::FetchOptions::new();
        fetch_options.update_fetchhead(false);
        fetch_options.download_tags(git2::AutotagOption::None);
        worker
            .remote_anonymous(path_string(&self.repo_path(repo_id))?)?
            .fetch(
                &["refs/heads/dev:refs/heads/dev"],
                Some(&mut fetch_options),
                None,
            )?;
        // libgit2 may create FETCH_HEAD even with update_fetchhead(false).
        // Transport metadata must never expose the private canonical path.
        let fetch_head = worker.path().join("FETCH_HEAD");
        if fetch_head.exists() {
            fs::remove_file(fetch_head)?;
        }
        let branch = task_branch(task_id);
        worker.reference(
            &format!("refs/heads/{branch}"),
            dev,
            false,
            "seed worker from dev",
        )?;
        worker.set_head(&format!("refs/heads/{branch}"))?;
        worker.checkout_head(None)?;
        worker.find_reference(DEV)?.delete()?;
        install_hooks(&worker)?;
        drop(worker);
        let root_fd = open_directory(&self.worker_root)?;
        let repo_fd = mkdir_child(&root_fd, OsStr::new(repo_id), false)?;
        let task_fd = mkdir_child(&repo_fd, OsStr::new(task_id), true)?;
        export_directory(&staged, &task_fd)?;
        drop(cleanup);
        let record = WorkerRecord {
            repo_id: repo_id.into(),
            task_id: task_id.into(),
            dev_commit: dev.to_string(),
        };
        atomic_json(&self.worker_record(repo_id, task_id), &record)?;
        Ok(
            json!({"repoId":repo_id,"taskId":task_id,"workerPath":path,"branch":branch,
            "devCommit":dev.to_string(),"taskCommit":dev.to_string(),
            "before":{"devCommit":dev.to_string(),"mainCommit":main.to_string()},
            "after":{"devCommit":dev.to_string(),"mainCommit":main.to_string()}}),
        )
    }

    pub fn collect_worker(
        &self,
        repo_id: &str,
        task_id: &str,
        expected_dev: &str,
    ) -> Result<Value> {
        valid_id(repo_id)?;
        valid_id(task_id)?;
        let expected = parse_oid(expected_dev)?;
        let _lock = self.lock()?;
        let canonical = self.open_repo(repo_id)?;
        let main = commit_ref(&canonical, MAIN)?;
        if commit_ref(&canonical, DEV)? != expected {
            return Err(RepoError("stale_dev"));
        }
        let record: WorkerRecord = read_json(&self.worker_record(repo_id, task_id))?;
        if record.repo_id != repo_id || record.task_id != task_id {
            return Err(RepoError("worker_binding_mismatch"));
        }
        let base = parse_oid(&record.dev_commit)?;
        if !descends(&canonical, expected, base)? {
            return Err(RepoError("worker_binding_mismatch"));
        }
        let snapshot = self.snapshot(&self.worker_path(repo_id, task_id), false)?;
        let worker = Repository::open_bare(&snapshot.0)?;
        let task_ref = format!("refs/heads/{}", task_branch(task_id));
        // Never resolve symbolic refs supplied by an agent (including refs that alias main).
        let task = direct_commit_ref(&worker, &task_ref)?;
        validate_commit_tree(&worker, task)?;
        if !descends(&worker, task, base)? {
            return Err(RepoError("task_not_descendant"));
        }
        let incoming = incoming_ref(task_id);
        if let Ok(previous) = direct_commit_ref(&canonical, &incoming) {
            if !descends(&worker, task, previous)? {
                return Err(RepoError("task_rewritten"));
            }
        }
        let mut fetch_options = git2::FetchOptions::new();
        fetch_options.update_fetchhead(false);
        fetch_options.download_tags(git2::AutotagOption::None);
        canonical
            .remote_anonymous(path_string(&snapshot.0)?)?
            .fetch(
                &[&format!("{task_ref}:{incoming}")],
                Some(&mut fetch_options),
                None,
            )?;
        if direct_commit_ref(&canonical, &incoming)? != task {
            return Err(RepoError("task_changed"));
        }
        // Recheck ancestry using canonical objects after the transport validates them.
        if !descends(&canonical, task, base)? {
            return Err(RepoError("task_not_descendant"));
        }
        Ok(
            json!({"repoId":repo_id,"taskId":task_id,"devCommit":expected.to_string(),"baseCommit":base.to_string(),"taskCommit":task.to_string(),
                "before":{"devCommit":expected.to_string(),"mainCommit":main.to_string()},
                "after":{"devCommit":expected.to_string(),"mainCommit":main.to_string()}}),
        )
    }

    pub fn merge_dev(
        &self,
        repo_id: &str,
        task_id: &str,
        expected_dev: &str,
        expected_task: &str,
    ) -> Result<Value> {
        self.merge_dev_unlocked(repo_id, task_id, expected_dev, expected_task, None)
    }

    /// Merge bound to the exact persisted checker checkpoint. An interrupted
    /// caller may recover the original receipt, never a guessed before SHA.
    pub fn merge_dev_for_evidence(
        &self,
        repo_id: &str,
        task_id: &str,
        expected_dev: &str,
        expected_task: &str,
        evidence_id: &str,
        evidence_fingerprint: &str,
    ) -> Result<Value> {
        self.merge_dev_unlocked(
            repo_id,
            task_id,
            expected_dev,
            expected_task,
            Some((evidence_id, evidence_fingerprint)),
        )
    }

    fn merge_dev_unlocked(
        &self,
        repo_id: &str,
        task_id: &str,
        expected_dev: &str,
        expected_task: &str,
        evidence: Option<(&str, &str)>,
    ) -> Result<Value> {
        valid_id(repo_id)?;
        valid_id(task_id)?;
        let expected_dev = parse_oid(expected_dev)?;
        let expected_task = parse_oid(expected_task)?;
        let _lock = self.lock()?;
        let repo = self.open_repo(repo_id)?;
        if let Some((id, fingerprint)) = evidence {
            valid_id(id)?;
            valid_fingerprint(fingerprint)?;
            if let Some(journal) = self.read_merge_journal(repo_id, task_id, id)? {
                self.validate_merge_binding(
                    &journal,
                    repo_id,
                    task_id,
                    &expected_task.to_string(),
                    id,
                    fingerprint,
                )?;
                if journal.expected_dev != expected_dev.to_string() {
                    return Err(RepoError("merge_evidence_conflict"));
                }
                return self.apply_merge_journal(&repo, journal);
            }
        }
        let mut tx = repo.transaction()?;
        tx.lock_ref(DEV)?;
        tx.lock_ref(MAIN)?;
        let main = commit_ref(&repo, MAIN)?;
        let dev = commit_ref(&repo, DEV)?;
        if dev != expected_dev {
            return Err(RepoError("stale_dev"));
        }
        if direct_commit_ref(&repo, &incoming_ref(task_id))? != expected_task {
            return Err(RepoError("stale_task"));
        }
        validate_commit_tree(&repo, dev)?;
        validate_commit_tree(&repo, expected_task)?;
        let next = if descends(&repo, expected_task, dev)? {
            expected_task
        } else if descends(&repo, dev, expected_task)? {
            dev
        } else {
            let ours = repo.find_commit(dev)?;
            let theirs = repo.find_commit(expected_task)?;
            let mut index = repo.merge_commits(&ours, &theirs, None)?;
            if index.has_conflicts() {
                return Err(RepoError("merge_conflict"));
            }
            let tree_id = index.write_tree_to(&repo)?;
            let tree = repo.find_tree(tree_id)?;
            let sig = engine_signature()?;
            repo.commit(
                None,
                &sig,
                &sig,
                "Integrate checked worker task",
                &tree,
                &[&ours, &theirs],
            )?
        };
        validate_commit_tree(&repo, next)?;
        let mut journal = evidence.map(|(id, fingerprint)| MergeJournal {
            schema_version: 1,
            repo_id: repo_id.into(),
            task_id: task_id.into(),
            evidence_id: id.into(),
            evidence_fingerprint: fingerprint.into(),
            expected_dev: dev.to_string(),
            expected_task: expected_task.to_string(),
            expected_main: main.to_string(),
            next_dev: next.to_string(),
            state: "prepared".into(),
        });
        if let Some(journal) = &journal {
            // Objects and immutable intent are durable before the ref can move.
            sync_all(repo.path())?;
            self.write_merge_journal(journal)?;
        }
        tx.set_target(
            DEV,
            next,
            Some(&engine_signature()?),
            "checked worker integration",
        )?;
        tx.commit()?;
        sync_all(repo.path())?;
        if let Some(journal) = &mut journal {
            journal.state = "applied".into();
            self.write_merge_journal(journal)?;
        }
        Ok(
            json!({"repoId":repo_id,"taskId":task_id,"taskCommit":expected_task.to_string(),
            "devCommit":next.to_string(),"mainCommit":main.to_string(),
            "before":{"devCommit":dev.to_string(),"mainCommit":main.to_string()},
            "after":{"devCommit":next.to_string(),"mainCommit":main.to_string()}}),
        )
    }

    /// Only a native, authenticated journal with an exact checker fingerprint
    /// can recover an interrupted merge. Missing journals return no authority.
    pub fn recover_merge_dev(
        &self,
        repo_id: &str,
        task_id: &str,
        expected_task: &str,
        evidence_id: &str,
        evidence_fingerprint: &str,
    ) -> Result<Option<Value>> {
        valid_id(repo_id)?;
        valid_id(task_id)?;
        valid_id(evidence_id)?;
        parse_oid(expected_task)?;
        valid_fingerprint(evidence_fingerprint)?;
        let _lock = self.lock()?;
        let Some(journal) = self.read_merge_journal(repo_id, task_id, evidence_id)? else {
            return Ok(None);
        };
        self.validate_merge_binding(
            &journal,
            repo_id,
            task_id,
            expected_task,
            evidence_id,
            evidence_fingerprint,
        )?;
        let repo = self.open_repo(repo_id)?;
        self.apply_merge_journal(&repo, journal).map(Some)
    }

    fn merge_journal_path(&self, repo: &str, task: &str, evidence: &str) -> PathBuf {
        let scope = serde_json::to_vec(&(repo, task, evidence)).expect("string tuple serializes");
        self.private_root
            .join("merges")
            .join(repo)
            .join(format!("{}.json", hex(&Sha256::digest(scope))))
    }

    fn merge_key(&self) -> Result<[u8; 32]> {
        let path = self.private_root.join("merges/authentication.key");
        if !path.exists() {
            let mut key = [0; 32];
            rand::thread_rng().fill_bytes(&mut key);
            let mut file = OpenOptions::new()
                .create_new(true)
                .write(true)
                .mode(0o600)
                .custom_flags(libc::O_NOFOLLOW)
                .open(&path)?;
            file.write_all(&key)?;
            file.sync_all()?;
            sync_dir(path.parent().unwrap())?;
        }
        let mut file = OpenOptions::new()
            .read(true)
            .custom_flags(libc::O_NOFOLLOW)
            .open(path)?;
        use std::os::unix::fs::MetadataExt;
        let metadata = file.metadata()?;
        if !metadata.is_file()
            || metadata.len() != 32
            || metadata.nlink() != 1
            || metadata.uid() != unsafe { libc::geteuid() }
            || metadata.mode() & 0o777 != 0o600
        {
            return Err(RepoError("invalid_merge_key"));
        }
        let mut key = [0; 32];
        file.read_exact(&mut key)?;
        Ok(key)
    }

    fn write_merge_journal(&self, journal: &MergeJournal) -> Result<()> {
        secure_create_dir(&self.private_root.join("merges").join(&journal.repo_id))?;
        let bytes = serde_json::to_vec(journal).map_err(|_| RepoError("receipt_encoding"))?;
        let mut mac = Hmac::<Sha256>::new_from_slice(&self.merge_key()?).expect("HMAC accepts key");
        mac.update(&bytes);
        let value = json!({"journal": journal, "authentication":hex(&mac.finalize().into_bytes())});
        atomic_json(
            &self.merge_journal_path(&journal.repo_id, &journal.task_id, &journal.evidence_id),
            &value,
        )
    }

    fn read_merge_journal(
        &self,
        repo: &str,
        task: &str,
        evidence: &str,
    ) -> Result<Option<MergeJournal>> {
        let path = self.merge_journal_path(repo, task, evidence);
        match fs::symlink_metadata(&path) {
            Ok(metadata) if metadata.is_file() => {}
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
            _ => return Err(RepoError("invalid_merge_journal")),
        }
        let signed: SignedMergeJournal =
            read_json(&path).map_err(|_| RepoError("invalid_merge_journal"))?;
        let bytes =
            serde_json::to_vec(&signed.journal).map_err(|_| RepoError("invalid_merge_journal"))?;
        let signature = unhex(&signed.authentication)?;
        let mut mac = Hmac::<Sha256>::new_from_slice(&self.merge_key()?).expect("HMAC accepts key");
        mac.update(&bytes);
        mac.verify_slice(&signature)
            .map_err(|_| RepoError("invalid_merge_journal"))?;
        Ok(Some(signed.journal))
    }

    fn validate_merge_binding(
        &self,
        journal: &MergeJournal,
        repo: &str,
        task: &str,
        commit: &str,
        evidence: &str,
        fingerprint: &str,
    ) -> Result<()> {
        if journal.schema_version != 1 || !matches!(journal.state.as_str(), "prepared" | "applied")
        {
            return Err(RepoError("invalid_merge_journal"));
        }
        if journal.repo_id != repo
            || journal.task_id != task
            || journal.expected_task != commit
            || journal.evidence_id != evidence
            || journal.evidence_fingerprint != fingerprint
        {
            return Err(RepoError("merge_evidence_conflict"));
        }
        for oid in [
            &journal.expected_dev,
            &journal.expected_task,
            &journal.expected_main,
            &journal.next_dev,
        ] {
            parse_oid(oid)?;
        }
        Ok(())
    }

    fn apply_merge_journal(&self, repo: &Repository, mut journal: MergeJournal) -> Result<Value> {
        let mut tx = repo.transaction()?;
        tx.lock_ref(DEV)?;
        tx.lock_ref(MAIN)?;
        let dev = commit_ref(repo, DEV)?;
        let before = parse_oid(&journal.expected_dev)?;
        let next = parse_oid(&journal.next_dev)?;
        let main = parse_oid(&journal.expected_main)?;
        let task = parse_oid(&journal.expected_task)?;
        if commit_ref(repo, MAIN)? != main {
            return Err(RepoError("merge_recovery_refs_changed"));
        }
        if direct_commit_ref(repo, &incoming_ref(&journal.task_id))? != task {
            return Err(RepoError("stale_task"));
        }
        validate_commit_tree(repo, next)?;
        if !descends(repo, next, before)? || !descends(repo, next, task)? {
            return Err(RepoError("invalid_merge_journal"));
        }
        if dev == before && journal.state == "prepared" {
            tx.set_target(
                DEV,
                next,
                Some(&engine_signature()?),
                "recover checked worker integration",
            )?;
            tx.commit()?;
            sync_all(repo.path())?;
        } else if dev != next {
            // Never publish older evidence over a newer parallel integration.
            return Err(RepoError("merge_recovery_refs_changed"));
        }
        journal.state = "applied".into();
        self.write_merge_journal(&journal)?;
        Ok(
            json!({"repoId":journal.repo_id,"taskId":journal.task_id,"taskCommit":journal.expected_task,
            "devCommit":journal.next_dev,"mainCommit":journal.expected_main,
            "before":{"devCommit":journal.expected_dev,"mainCommit":journal.expected_main},
            "after":{"devCommit":journal.next_dev,"mainCommit":journal.expected_main}}),
        )
    }

    pub fn promote(
        &self,
        repo_id: &str,
        expected_dev: &str,
        expected_main: &str,
        confirmed: bool,
        request_id: &str,
    ) -> Result<Value> {
        valid_id(repo_id)?;
        valid_id(request_id)?;
        if !confirmed {
            return Err(RepoError("confirmation_required"));
        }
        let dev = parse_oid(expected_dev)?;
        let main = parse_oid(expected_main)?;
        let _lock = self.lock()?;
        let receipt_path = self
            .private_root
            .join("receipts")
            .join(format!("{request_id}.json"));
        self.bind_receipt_scope(repo_id, request_id)?;
        let mut receipt = if receipt_path.exists() {
            let old: PromotionReceipt = read_json(&receipt_path)?;
            if old.schema_version != 1
                || old.repo_id != repo_id
                || old.request_id != request_id
                || old.expected_dev != dev.to_string()
                || old.expected_main != main.to_string()
                || old.confirmed != confirmed
            {
                return Err(RepoError("request_id_conflict"));
            }
            if old.state == "applied" {
                return Ok(promotion_result(&old));
            }
            if old.state == "superseded" {
                return Err(RepoError("promotion_superseded"));
            }
            if old.state != "prepared" {
                return Err(RepoError("invalid_receipt"));
            }
            old
        } else {
            PromotionReceipt {
                schema_version: 1,
                repo_id: repo_id.into(),
                request_id: request_id.into(),
                expected_dev: dev.to_string(),
                expected_main: main.to_string(),
                confirmed,
                state: "prepared".into(),
                before: json!({"devCommit":dev.to_string(),"mainCommit":main.to_string()}),
                after: json!({"devCommit":dev.to_string(),"mainCommit":dev.to_string()}),
            }
        };
        // Before a later promotion can move main beyond an interrupted receipt, resolve
        // every earlier durable intent for this repository. Otherwise refs alone would
        // no longer distinguish an applied request from an unapplied one after restart.
        self.reconcile_earlier_promotions(repo_id, request_id)?;
        let repo = self.open_repo(repo_id)?;
        let mut tx = repo.transaction()?;
        tx.lock_ref(MAIN)?;
        tx.lock_ref(DEV)?;
        let current_main = commit_ref(&repo, MAIN)?;
        let current_dev = commit_ref(&repo, DEV)?;
        if receipt_path.exists() && current_main == dev {
            // Main was committed after durable intent but before durable acknowledgement.
            receipt.state = "applied".into();
            atomic_json(&receipt_path, &receipt)?;
            return Ok(promotion_result(&receipt));
        }
        if current_dev != dev {
            return Err(RepoError("stale_dev"));
        }
        if current_main != main {
            return Err(RepoError("stale_main"));
        }
        if !descends(&repo, dev, main)? {
            return Err(RepoError("promotion_not_fast_forward"));
        }
        // Intent and both SHA values reach storage before main changes.
        sync_all(repo.path())?;
        atomic_json(&receipt_path, &receipt)?;
        tx.set_target(MAIN, dev, Some(&engine_signature()?), "confirmed promotion")?;
        tx.commit()?;
        sync_tree_refs(&repo)?;
        receipt.state = "applied".into();
        atomic_json(&receipt_path, &receipt)?;
        Ok(promotion_result(&receipt))
    }

    fn reconcile_earlier_promotions(&self, repo_id: &str, request_id: &str) -> Result<()> {
        for entry in fs::read_dir(self.private_root.join("receipts"))? {
            let path = entry?.path();
            if path.extension() != Some(OsStr::new("json")) {
                continue;
            }
            // Scope is durable before a new intent. An unreadable body must block its
            // own repository, never every other one. Legacy readable JSON can provide
            // the scope before its strict receipt schema is interpreted.
            let id = path
                .file_stem()
                .and_then(OsStr::to_str)
                .ok_or(RepoError("invalid_receipt"))?;
            valid_id(id)?;
            if self.receipt_repo_id(&path, id)? != repo_id {
                continue;
            }
            let mut receipt: PromotionReceipt = read_json(&path)?;
            if receipt.repo_id != repo_id || receipt.request_id != id {
                return Err(RepoError("invalid_receipt"));
            }
            if receipt.schema_version != 1
                || !receipt.confirmed
                || !matches!(
                    receipt.state.as_str(),
                    "prepared" | "applied" | "superseded"
                )
            {
                return Err(RepoError("invalid_receipt"));
            }
            if receipt.request_id == request_id || receipt.state != "prepared" {
                continue;
            }
            let dev = parse_oid(&receipt.expected_dev)?;
            let main = parse_oid(&receipt.expected_main)?;
            let repo = self.open_repo(repo_id)?;
            let mut tx = repo.transaction()?;
            tx.lock_ref(MAIN)?;
            tx.lock_ref(DEV)?;
            let current_main = commit_ref(&repo, MAIN)?;
            let current_dev = commit_ref(&repo, DEV)?;
            if current_main == dev {
                receipt.state = "applied".into();
            } else if current_main == main && current_dev != dev {
                // Exact dev was not applied and has since changed. Never substitute a new SHA.
                receipt.state = "superseded".into();
            } else if current_main == main && current_dev == dev {
                if !descends(&repo, dev, main)? {
                    return Err(RepoError("promotion_not_fast_forward"));
                }
                sync_all(repo.path())?;
                tx.set_target(
                    MAIN,
                    dev,
                    Some(&engine_signature()?),
                    "confirmed promotion recovery",
                )?;
                tx.commit()?;
                sync_tree_refs(&repo)?;
                receipt.state = "applied".into();
            } else {
                return Err(RepoError("promotion_recovery_required"));
            }
            atomic_json(&path, &receipt)?;
        }
        Ok(())
    }

    fn bind_receipt_scope(&self, repo_id: &str, request_id: &str) -> Result<()> {
        let path = self
            .private_root
            .join("receipt-scopes")
            .join(format!("{request_id}.json"));
        let binding = json!({"repoId":repo_id,"requestId":request_id});
        if path.exists() {
            if read_json::<Value>(&path)? != binding {
                return Err(RepoError("request_id_conflict"));
            }
        } else {
            // Do not attach a new repository to an existing request body.
            let old = self
                .private_root
                .join("receipts")
                .join(format!("{request_id}.json"));
            if old.exists() && read_json::<Value>(&old)?["repoId"] != repo_id {
                return Err(RepoError("request_id_conflict"));
            }
            atomic_json(&path, &binding)?;
        }
        Ok(())
    }

    fn receipt_repo_id(&self, receipt: &Path, request_id: &str) -> Result<String> {
        let scope = self
            .private_root
            .join("receipt-scopes")
            .join(format!("{request_id}.json"));
        let body: Value = if scope.exists() {
            let binding: Value = read_json(&scope)?;
            if binding["requestId"] != request_id {
                return Err(RepoError("invalid_receipt"));
            }
            binding
        } else {
            // Completely corrupted legacy records have no trustworthy repository
            // scope. Keep that ambiguity fail-closed for operator review.
            read_json(receipt).map_err(|_| RepoError("legacy_receipt_scope_unknown"))?
        };
        let id = body["repoId"]
            .as_str()
            .ok_or(RepoError("legacy_receipt_scope_unknown"))?;
        valid_id(id)?;
        Ok(id.to_owned())
    }

    pub fn refs(&self, repo_id: &str) -> Result<Value> {
        valid_id(repo_id)?;
        let _lock = self.lock()?;
        self.refs_unlocked(repo_id)
    }

    /// Call only after the backend fences pipelines and snapshots all committed
    /// repository IDs. Receipts/scopes and retirement markers remain private audit
    /// records; repository IDs cannot be reused after their data was collected.
    pub fn collect_unreferenced_repositories(&self, retained_repo_ids: &[String]) -> Result<Value> {
        for id in retained_repo_ids {
            valid_id(id)?;
        }
        let retained: HashSet<&str> = retained_repo_ids.iter().map(String::as_str).collect();
        let _lock = self.lock()?;
        let repos = open_directory(&self.private_root.join("repos"))?;
        let imports = open_directory(&self.private_root.join("imports"))?;
        let records = open_directory(&self.private_root.join("workers"))?;
        let merges = open_directory(&self.private_root.join("merges"))?;
        let workers = open_directory(&self.worker_root)?;
        let mut candidates = HashSet::<String>::new();
        for (directory, suffix) in [(&repos, ".git"), (&imports, ".json")] {
            for name in directory_names(directory)? {
                let id = name
                    .to_str()
                    .and_then(|s| s.strip_suffix(suffix))
                    .ok_or(RepoError("unsafe_repository_metadata"))?;
                valid_id(id)?;
                candidates.insert(id.to_owned());
            }
        }
        for name in directory_names(&workers)? {
            let id = name.to_str().ok_or(RepoError("invalid_id"))?;
            valid_id(id)?;
            candidates.insert(id.to_owned());
        }
        for name in directory_names(&merges)? {
            if name == OsStr::new("authentication.key") {
                continue;
            }
            let id = name
                .to_str()
                .ok_or(RepoError("unsafe_repository_metadata"))?;
            valid_id(id)?;
            candidates.insert(id.to_owned());
        }
        let record_names = directory_names(&records)?;
        for name in &record_names {
            let (id, task) = name
                .to_str()
                .and_then(|s| s.strip_suffix(".json"))
                .and_then(|s| s.split_once('.'))
                .ok_or(RepoError("unsafe_repository_metadata"))?;
            valid_id(id)?;
            valid_id(task)?;
            candidates.insert(id.to_owned());
        }
        let mut collected: Vec<_> = candidates
            .into_iter()
            .filter(|id| !retained.contains(id.as_str()))
            .collect();
        collected.sort();
        for id in &collected {
            // Durable first: partial cleanup or process death cannot permit reuse of
            // an ID whose previous confirmed receipts still exist.
            // An authored import binding alone is not a published repository.
            // Failed imports must be correctable using the same editor ID. Once
            // canonical refs or worker authority exist, retirement stays durable.
            if self.repo_path(id).exists()
                || self.worker_root.join(id).exists()
                || record_names.iter().any(|name| {
                    name.to_str()
                        .is_some_and(|name| name.starts_with(&format!("{id}.")))
                })
                || self
                    .private_root
                    .join("retired")
                    .join(format!("{id}.json"))
                    .exists()
            {
                atomic_json(
                    &self.private_root.join("retired").join(format!("{id}.json")),
                    &json!({"repoId":id,"state":"retired"}),
                )?;
            }
            let mut count = 0;
            remove_collected_child(&workers, OsStr::new(id), 0, &mut count, true)?;
            count = 0;
            remove_collected_child(
                &repos,
                OsStr::new(&format!("{id}.git")),
                0,
                &mut count,
                true,
            )?;
            unlink_child(&imports, OsStr::new(&format!("{id}.json")), false)?;
            count = 0;
            remove_collected_child(&merges, OsStr::new(id), 0, &mut count, true)?;
            for name in &record_names {
                if name
                    .to_str()
                    .is_some_and(|s| s.starts_with(&format!("{id}.")))
                {
                    unlink_child(&records, name, false)?;
                }
            }
        }
        for directory in [&workers, &repos, &imports, &records, &merges] {
            sync_fd(directory)?;
        }
        Ok(json!({"removedRepoIds":collected,"receiptsRetained":true}))
    }
    fn refs_unlocked(&self, repo_id: &str) -> Result<Value> {
        let repo = self.open_repo(repo_id)?;
        Ok(
            json!({"repoId":repo_id,"mainCommit":commit_ref(&repo,MAIN)?.to_string(),"devCommit":commit_ref(&repo,DEV)?.to_string()}),
        )
    }
    fn repo_path(&self, repo: &str) -> PathBuf {
        self.private_root.join("repos").join(format!("{repo}.git"))
    }
    fn worker_path(&self, repo: &str, task: &str) -> PathBuf {
        self.worker_root.join(repo).join(task)
    }
    fn worker_record(&self, repo: &str, task: &str) -> PathBuf {
        self.private_root
            .join("workers")
            .join(format!("{repo}.{task}.json"))
    }
    fn open_repo(&self, id: &str) -> Result<Repository> {
        let path = self.repo_path(id);
        check_ancestors(&path)?;
        Repository::open_bare(path).map_err(|_| RepoError("repository_unavailable"))
    }
    fn lock(&self) -> Result<Lock> {
        let file = OpenOptions::new()
            .create(true)
            .truncate(false)
            .read(true)
            .write(true)
            .mode(0o600)
            .custom_flags(libc::O_NOFOLLOW | libc::O_CLOEXEC)
            .open(self.private_root.join("authority.lock"))?;
        if unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX) } != 0 {
            return Err(RepoError("repository_busy"));
        }
        Ok(Lock(file))
    }
    fn snapshot(&self, source: &Path, importing: bool) -> Result<Snapshot> {
        let source_fd = open_directory(source)?;
        let (git_fd, git_root) = match open_child(source_fd.as_raw_fd(), OsStr::new(".git")) {
            Ok(fd) => {
                if !is_directory(&fd)? {
                    return Err(RepoError("linked_repository"));
                }
                (fd, source.join(".git"))
            }
            Err(_) => {
                // Bare repositories have HEAD + objects at their root.
                let head = open_child(source_fd.as_raw_fd(), OsStr::new("HEAD"))?;
                if is_directory(&head)? {
                    return Err(RepoError("invalid_repository"));
                }
                (source_fd, source.to_path_buf())
            }
        };
        let path = self
            .private_root
            .join("staging")
            .join(uuid::Uuid::new_v4().to_string());
        secure_create_dir(&path)?;
        let snapshot = Snapshot(path);
        let mut budget = (0u64, 0usize);
        copy_directory(
            &git_fd,
            &snapshot.0,
            &mut budget,
            0,
            &git_root,
            Path::new(""),
        )?;
        // Never allow libgit2 config includes/alternates to read daemon-private files.
        for forbidden in [
            "objects/info/alternates",
            "objects/info/http-alternates",
            "commondir",
            "gitdir",
            "shallow",
            "info/grafts",
            "refs/replace",
        ] {
            if snapshot.0.join(forbidden).exists() {
                return Err(RepoError("unsafe_repository_metadata"));
            }
        }
        let config = fs::read_to_string(snapshot.0.join("config"))?;
        let lower = config.to_ascii_lowercase();
        if lower.contains("[include")
            || lower.contains("[extensions")
            || lower.contains("[url ")
            || (!importing && lower.contains("[remote"))
        {
            return Err(RepoError("unsafe_repository_config"));
        }
        // Import may carry a normal network origin; it is never fetched or retained.
        // Local/file/scp origins are refused rather than interpreting untrusted paths.
        for line in config.lines() {
            if let Some((key, value)) = line.trim().split_once('=') {
                if key.trim().eq_ignore_ascii_case("url") {
                    let value = value.trim().trim_matches('"');
                    if !["https://", "http://", "ssh://", "git://"]
                        .iter()
                        .any(|prefix| value.starts_with(prefix))
                    {
                        return Err(RepoError("unsafe_repository_config"));
                    }
                }
            }
        }
        fs::write(
            snapshot.0.join("config"),
            "[core]\n\trepositoryformatversion = 0\n\tbare = true\n",
        )?;
        // Native authority never executes any hook from untrusted input.
        if snapshot.0.join("hooks").exists() {
            fs::remove_dir_all(snapshot.0.join("hooks"))?;
        }
        validate_snapshot_objects(&snapshot.0)?;
        Ok(snapshot)
    }
}

fn promotion_result(r: &PromotionReceipt) -> Value {
    json!({"repoId":r.repo_id,"receiptId":r.request_id,"devCommit":r.expected_dev,
        "mainCommit":r.expected_dev,"before":r.before,"after":r.after,"state":"applied"})
}
fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}
fn unhex(value: &str) -> Result<Vec<u8>> {
    if value.len() != 64 || !value.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err(RepoError("invalid_merge_journal"));
    }
    (0..value.len())
        .step_by(2)
        .map(|i| {
            u8::from_str_radix(&value[i..i + 2], 16).map_err(|_| RepoError("invalid_merge_journal"))
        })
        .collect()
}
fn valid_fingerprint(value: &str) -> Result<()> {
    if value.len() != 64 || !value.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err(RepoError("invalid_merge_evidence"));
    }
    Ok(())
}

fn valid_id(id: &str) -> Result<()> {
    if id.is_empty()
        || id.len() > 128
        || !id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-')
    {
        return Err(RepoError("invalid_id"));
    }
    Ok(())
}
fn parse_oid(value: &str) -> Result<Oid> {
    if value.len() != 40 || !value.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err(RepoError("invalid_commit"));
    }
    Oid::from_str(value).map_err(|_| RepoError("invalid_commit"))
}
pub(crate) fn task_branch(task: &str) -> String {
    format!("task/{task}")
}
fn incoming_ref(task: &str) -> String {
    format!("refs/aiteam/collected/{task}")
}
fn engine_signature() -> Result<Signature<'static>> {
    Ok(Signature::now("Phone project engine", "engine@localhost")?)
}
fn direct_commit_ref(repo: &Repository, name: &str) -> Result<Oid> {
    let reference = repo.find_reference(name)?;
    let oid = reference
        .target()
        .ok_or(RepoError("symbolic_ref_refused"))?;
    repo.find_commit(oid)?;
    Ok(oid)
}
fn commit_ref(repo: &Repository, name: &str) -> Result<Oid> {
    direct_commit_ref(repo, name)
}
fn descends(repo: &Repository, next: Oid, before: Oid) -> Result<bool> {
    Ok(next == before || repo.graph_descendant_of(next, before)?)
}

/// Validate object trees before they can become canonical dev or reach a private
/// libgit2 checkout. Object metadata is agent input, including duplicate entries.
fn validate_commit_tree(repo: &Repository, commit: Oid) -> Result<()> {
    let tree = repo.find_commit(commit)?.tree()?;
    let mut remaining = MAX_SNAPSHOT_FILES;
    validate_tree(repo, &tree, 0, &mut remaining)
}

fn validate_tree(
    repo: &Repository,
    tree: &git2::Tree<'_>,
    depth: usize,
    remaining: &mut usize,
) -> Result<()> {
    if depth > 128 {
        return Err(RepoError("unsafe_task_tree"));
    }
    let mut names = HashSet::new();
    for entry in tree.iter() {
        if *remaining == 0 {
            return Err(RepoError("unsafe_task_tree"));
        }
        *remaining -= 1;
        let name = entry.name_bytes();
        if name.is_empty()
            || name == b"."
            || name == b".."
            || name.eq_ignore_ascii_case(b".git")
            || name.contains(&b'/')
            || !names.insert(name.to_vec())
        {
            return Err(RepoError("unsafe_task_tree"));
        }
        match entry.filemode() {
            0o040000 => validate_tree(repo, &repo.find_tree(entry.id())?, depth + 1, remaining)?,
            0o120000 => {
                let target = repo.find_blob(entry.id())?;
                let bytes = target.content();
                if bytes.is_empty()
                    || bytes.len() > 4096
                    || bytes.starts_with(b"/")
                    || bytes.contains(&0)
                    || bytes.split(|b| *b == b'/').any(|part| part == b"..")
                {
                    return Err(RepoError("unsafe_task_tree"));
                }
            }
            0o100644 | 0o100755 | 0o160000 => {}
            _ => return Err(RepoError("unsafe_task_tree")),
        }
    }
    Ok(())
}
fn remove_remotes(repo: &Repository) -> Result<()> {
    let names: Vec<String> = repo
        .remotes()?
        .iter()
        .flatten()
        .map(str::to_owned)
        .collect();
    for name in names {
        repo.remote_delete(&name)?;
    }
    Ok(())
}
fn path_string(path: &Path) -> Result<&str> {
    path.to_str().ok_or(RepoError("invalid_path"))
}
fn absolute(path: &Path) -> Result<PathBuf> {
    if !path.is_absolute()
        || path
            .components()
            .any(|c| matches!(c, Component::ParentDir | Component::CurDir))
    {
        return Err(RepoError("invalid_path"));
    }
    Ok(path.into())
}
fn check_ancestors(path: &Path) -> Result<()> {
    let mut current = PathBuf::from("/");
    for part in path.components().skip(1) {
        current.push(part);
        match fs::symlink_metadata(&current) {
            Ok(m) if m.file_type().is_symlink() => return Err(RepoError("symlink_refused")),
            Ok(_) => (),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => (),
            Err(e) => return Err(e.into()),
        }
    }
    Ok(())
}
fn secure_create_dir(path: &Path) -> Result<()> {
    let current = traverse_directory(path, true)?;
    if unsafe { libc::fchmod(current.as_raw_fd(), 0o700) } != 0 {
        return Err(RepoError("repository_io"));
    }
    Ok(())
}
fn mkdir_child(parent: &OwnedFd, name: &OsStr, exclusive: bool) -> Result<OwnedFd> {
    let name_c = CString::new(name.as_bytes()).map_err(|_| RepoError("invalid_path"))?;
    if unsafe { libc::mkdirat(parent.as_raw_fd(), name_c.as_ptr(), 0o700) } != 0 {
        let error = std::io::Error::last_os_error();
        if exclusive || error.kind() != std::io::ErrorKind::AlreadyExists {
            return Err(RepoError("worker_exists"));
        }
    }
    let fd = open_child(parent.as_raw_fd(), name)?;
    if !is_directory(&fd)? {
        return Err(RepoError("unsafe_repository_path"));
    }
    Ok(fd)
}

fn child_stat(parent: &OwnedFd, name: &OsStr) -> Result<Option<libc::stat>> {
    let name = CString::new(name.as_bytes()).map_err(|_| RepoError("invalid_path"))?;
    let mut metadata = std::mem::MaybeUninit::uninit();
    if unsafe {
        libc::fstatat(
            parent.as_raw_fd(),
            name.as_ptr(),
            metadata.as_mut_ptr(),
            libc::AT_SYMLINK_NOFOLLOW,
        )
    } != 0
    {
        if std::io::Error::last_os_error().kind() == std::io::ErrorKind::NotFound {
            return Ok(None);
        }
        return Err(RepoError("repository_io"));
    }
    Ok(Some(unsafe { metadata.assume_init() }))
}

fn unlink_child(parent: &OwnedFd, name: &OsStr, directory: bool) -> Result<()> {
    let name = CString::new(name.as_bytes()).map_err(|_| RepoError("invalid_path"))?;
    let flags = if directory { libc::AT_REMOVEDIR } else { 0 };
    if unsafe { libc::unlinkat(parent.as_raw_fd(), name.as_ptr(), flags) } != 0
        && std::io::Error::last_os_error().kind() != std::io::ErrorKind::NotFound
    {
        return Err(RepoError("repository_changed"));
    }
    Ok(())
}

fn sync_fd(fd: &OwnedFd) -> Result<()> {
    if unsafe { libc::fsync(fd.as_raw_fd()) } != 0 {
        return Err(RepoError("repository_io"));
    }
    Ok(())
}

/// Never recurse by a pathname derived from worker content. Open O_PATH first so
/// mode-000 abandoned directories can be recovered without a chmod symlink race.
fn remove_worker_child(
    parent: &OwnedFd,
    name: &OsStr,
    depth: usize,
    count: &mut usize,
) -> Result<()> {
    remove_collected_child(parent, name, depth, count, false)
}

fn remove_collected_child(
    parent: &OwnedFd,
    name: &OsStr,
    depth: usize,
    count: &mut usize,
    unlink_symlinks: bool,
) -> Result<()> {
    if depth > 128 || *count >= MAX_SNAPSHOT_FILES {
        return Err(RepoError("repository_too_large"));
    }
    let Some(metadata) = child_stat(parent, name)? else {
        return Ok(());
    };
    *count += 1;
    match metadata.st_mode & libc::S_IFMT {
        libc::S_IFREG if depth > 0 => unlink_child(parent, name, false),
        libc::S_IFDIR => {
            let name_c = CString::new(name.as_bytes()).map_err(|_| RepoError("invalid_path"))?;
            let fd = unsafe {
                libc::openat(
                    parent.as_raw_fd(),
                    name_c.as_ptr(),
                    libc::O_PATH | libc::O_DIRECTORY | libc::O_NOFOLLOW | libc::O_CLOEXEC,
                )
            };
            if fd < 0 {
                return Err(RepoError("unsafe_repository_path"));
            }
            let anchored = unsafe { OwnedFd::from_raw_fd(fd) };
            let actual = stat(&anchored)?;
            if actual.st_dev != metadata.st_dev || actual.st_ino != metadata.st_ino {
                return Err(RepoError("repository_changed"));
            }
            // /proc's fd magic link resolves the pinned directory inode, never the
            // untrusted name. O_PATH cannot itself be passed to fchmod on Linux.
            fs::set_permissions(
                format!("/proc/self/fd/{}", anchored.as_raw_fd()),
                fs::Permissions::from_mode(0o700),
            )?;
            let directory = open_child(anchored.as_raw_fd(), OsStr::new("."))?;
            for child in directory_names(&directory)? {
                remove_collected_child(&directory, &child, depth + 1, count, unlink_symlinks)?;
            }
            sync_fd(&directory)?;
            let Some(current) = child_stat(parent, name)? else {
                return Err(RepoError("repository_changed"));
            };
            if current.st_dev != actual.st_dev
                || current.st_ino != actual.st_ino
                || current.st_mode & libc::S_IFMT != libc::S_IFDIR
            {
                return Err(RepoError("repository_changed"));
            }
            unlink_child(parent, name, true)
        }
        libc::S_IFLNK if unlink_symlinks => unlink_child(parent, name, false),
        libc::S_IFLNK => Err(RepoError("symlink_refused")),
        _ if unlink_symlinks => unlink_child(parent, name, false),
        _ => Err(RepoError("unsafe_repository_metadata")),
    }
}

fn directory_names(fd: &OwnedFd) -> Result<Vec<std::ffi::OsString>> {
    let duplicate = unsafe { libc::dup(fd.as_raw_fd()) };
    if duplicate < 0 {
        return Err(RepoError("repository_io"));
    }
    let pointer = unsafe { libc::fdopendir(duplicate) };
    if pointer.is_null() {
        unsafe {
            libc::close(duplicate);
        }
        return Err(RepoError("repository_io"));
    }
    struct Directory(*mut libc::DIR);
    impl Drop for Directory {
        fn drop(&mut self) {
            unsafe {
                libc::closedir(self.0);
            }
        }
    }
    let directory = Directory(pointer);
    let mut names = Vec::new();
    loop {
        let entry = unsafe { libc::readdir(directory.0) };
        if entry.is_null() {
            break;
        }
        let name = unsafe { CStr::from_ptr((*entry).d_name.as_ptr()) }.to_bytes();
        if name == b"." || name == b".." {
            continue;
        }
        if names.len() >= MAX_SNAPSHOT_FILES {
            return Err(RepoError("repository_too_large"));
        }
        names.push(OsStr::from_bytes(name).to_owned());
    }
    Ok(names)
}

fn export_directory(source: &Path, destination: &OwnedFd) -> Result<()> {
    for entry in fs::read_dir(source)? {
        let entry = entry?;
        let name = entry.file_name();
        let metadata = fs::symlink_metadata(entry.path())?;
        if metadata.is_dir() {
            let child = mkdir_child(destination, &name, true)?;
            export_directory(&entry.path(), &child)?;
        } else {
            let name_c = CString::new(name.as_bytes()).map_err(|_| RepoError("invalid_path"))?;
            if metadata.file_type().is_symlink() {
                let target = fs::read_link(entry.path())?;
                let target_c = CString::new(target.as_os_str().as_bytes())
                    .map_err(|_| RepoError("invalid_path"))?;
                if unsafe {
                    libc::symlinkat(target_c.as_ptr(), destination.as_raw_fd(), name_c.as_ptr())
                } != 0
                {
                    return Err(RepoError("repository_io"));
                }
            } else if metadata.is_file() {
                let fd = unsafe {
                    libc::openat(
                        destination.as_raw_fd(),
                        name_c.as_ptr(),
                        libc::O_WRONLY
                            | libc::O_CREAT
                            | libc::O_EXCL
                            | libc::O_NOFOLLOW
                            | libc::O_CLOEXEC,
                        metadata.permissions().mode() & 0o777,
                    )
                };
                if fd < 0 {
                    return Err(RepoError("unsafe_repository_path"));
                }
                let mut output = unsafe { File::from_raw_fd(fd) };
                std::io::copy(&mut File::open(entry.path())?, &mut output)?;
            } else {
                return Err(RepoError("unsafe_repository_metadata"));
            }
        }
    }
    Ok(())
}
fn open_child(parent: i32, name: &OsStr) -> Result<OwnedFd> {
    let name = CString::new(name.as_bytes()).map_err(|_| RepoError("invalid_path"))?;
    let fd = unsafe {
        libc::openat(
            parent,
            name.as_ptr(),
            libc::O_RDONLY | libc::O_NOFOLLOW | libc::O_CLOEXEC | libc::O_NONBLOCK,
        )
    };
    if fd < 0 {
        return Err(RepoError("unsafe_repository_path"));
    }
    Ok(unsafe { OwnedFd::from_raw_fd(fd) })
}
fn open_directory(path: &Path) -> Result<OwnedFd> {
    traverse_directory(path, false)
}

/// Search-only system ancestors (notably Android /data) need not be readable.
/// Every component remains pinned and NOFOLLOW; only the final app-owned
/// directory is reopened readably for readdir/fsync and returned to callers.
fn traverse_directory(path: &Path, create: bool) -> Result<OwnedFd> {
    absolute(path)?;
    let root = CString::new("/").unwrap();
    let fd = unsafe {
        libc::open(
            root.as_ptr(),
            libc::O_PATH | libc::O_DIRECTORY | libc::O_NOFOLLOW | libc::O_CLOEXEC,
        )
    };
    if fd < 0 {
        return Err(RepoError("repository_io"));
    }
    let mut current = unsafe { OwnedFd::from_raw_fd(fd) };
    for part in path.components().skip(1) {
        let name =
            CString::new(part.as_os_str().as_bytes()).map_err(|_| RepoError("invalid_path"))?;
        let flags = libc::O_PATH | libc::O_DIRECTORY | libc::O_NOFOLLOW | libc::O_CLOEXEC;
        let mut fd = unsafe { libc::openat(current.as_raw_fd(), name.as_ptr(), flags) };
        let mut created = false;
        if fd < 0
            && create
            && std::io::Error::last_os_error().kind() == std::io::ErrorKind::NotFound
        {
            if unsafe { libc::mkdirat(current.as_raw_fd(), name.as_ptr(), 0o700) } != 0 {
                if std::io::Error::last_os_error().kind() != std::io::ErrorKind::AlreadyExists {
                    return Err(RepoError("repository_io"));
                }
            } else {
                created = true;
            }
            fd = unsafe { libc::openat(current.as_raw_fd(), name.as_ptr(), flags) };
        }
        if fd < 0 {
            return Err(RepoError("unsafe_repository_path"));
        }
        let child = unsafe { OwnedFd::from_raw_fd(fd) };
        if created {
            // New managed directories and their parent entries are durable;
            // existing search-only system ancestors are never read or changed.
            sync_fd(&open_child(child.as_raw_fd(), OsStr::new("."))?)?;
            sync_fd(&open_child(current.as_raw_fd(), OsStr::new("."))?)?;
        }
        current = child;
    }
    let leaf = open_child(current.as_raw_fd(), OsStr::new("."))?;
    if !is_directory(&leaf)? {
        return Err(RepoError("unsafe_repository_path"));
    }
    Ok(leaf)
}
fn stat(fd: &OwnedFd) -> Result<libc::stat> {
    let mut value = std::mem::MaybeUninit::uninit();
    if unsafe { libc::fstat(fd.as_raw_fd(), value.as_mut_ptr()) } != 0 {
        return Err(RepoError("repository_io"));
    }
    Ok(unsafe { value.assume_init() })
}
fn is_directory(fd: &OwnedFd) -> Result<bool> {
    Ok(stat(fd)?.st_mode & libc::S_IFMT == libc::S_IFDIR)
}

#[derive(Clone, Copy)]
enum ProotObjectKind {
    Loose,
    Pack,
}

fn lower_hex(bytes: &[u8]) -> bool {
    bytes
        .iter()
        .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(b))
}

fn proot_object_container(relative: &Path) -> Option<ProotObjectKind> {
    let mut parts = relative.components();
    if parts.next()?.as_os_str() != OsStr::new("objects") {
        return None;
    }
    let name = parts.next()?.as_os_str().as_bytes();
    if parts.next().is_some() {
        return None;
    }
    if name.len() == 2 && lower_hex(name) {
        Some(ProotObjectKind::Loose)
    } else if name == b"pack" {
        Some(ProotObjectKind::Pack)
    } else {
        None
    }
}

fn logical_object_name(name: &[u8], kind: ProotObjectKind) -> bool {
    match kind {
        ProotObjectKind::Loose => name.len() == 38 && lower_hex(name),
        ProotObjectKind::Pack => {
            name.starts_with(b"pack-")
                && name.len() > 45
                && lower_hex(&name[5..45])
                && [b".pack".as_slice(), b".idx", b".rev", b".bitmap"].contains(&&name[45..])
        }
    }
}

fn proot_backing_name(name: &[u8], kind: ProotObjectKind) -> bool {
    let prefix: &[u8] = match kind {
        ProotObjectKind::Loose => b".l2s.tmp_obj_",
        ProotObjectKind::Pack => b".l2s.tmp_pack_",
    };
    let Some(tail) = name.strip_prefix(prefix) else {
        return false;
    };
    if name.len() > 128 {
        return false;
    }
    let mut pieces = tail.split(|b| *b == b'.');
    let stem = pieces.next().unwrap_or_default();
    // Termux's first backing name has mkstemp's alphanumeric suffix followed
    // by its four-digit link counter. Later aliases append .NNNN counters.
    if stem.len() < 10
        || !stem.iter().all(u8::is_ascii_alphanumeric)
        || !stem[stem.len() - 4..].iter().all(u8::is_ascii_digit)
    {
        return false;
    }
    let counters: Vec<_> = pieces.collect();
    counters.len() <= 7
        && counters
            .iter()
            .all(|s| s.len() == 4 && s.iter().all(u8::is_ascii_digit))
}

fn proot_scratch_name(name: &[u8], kind: ProotObjectKind) -> bool {
    let prefix: &[u8] = match kind {
        ProotObjectKind::Loose => b"tmp_obj_",
        ProotObjectKind::Pack => b"tmp_pack_",
    };
    proot_backing_name(name, kind)
        || name.strip_prefix(prefix).is_some_and(|s| {
            s.len() >= 6 && s.len() <= 80 && s.iter().all(u8::is_ascii_alphanumeric)
        })
}

fn trusted_android_alias(path: &Path) -> PathBuf {
    // Only Android's system-managed app-data alias is normalized lexically.
    // No canonicalize/stat of attacker-supplied link targets is permitted.
    path.strip_prefix("/data/user/0")
        .map(|tail| Path::new("/data/data").join(tail))
        .unwrap_or_else(|_| path.to_path_buf())
}

fn open_proot_object(
    parent: &OwnedFd,
    logical: &OsStr,
    expected_parent: &Path,
    kind: ProotObjectKind,
) -> Result<OwnedFd> {
    let mut name = logical.to_os_string();
    let mut visited = HashSet::new();
    let mut hops = 0;
    loop {
        if !visited.insert(name.clone()) {
            return Err(RepoError("invalid_proot_object_link"));
        }
        let before = child_stat(parent, &name)?.ok_or(RepoError("repository_changed"))?;
        match before.st_mode & libc::S_IFMT {
            libc::S_IFLNK => {
                if hops == 8 {
                    return Err(RepoError("invalid_proot_object_link"));
                }
                hops += 1;
                let cname = CString::new(name.as_bytes())
                    .map_err(|_| RepoError("invalid_proot_object_link"))?;
                let mut text = [0u8; 4097];
                let length = unsafe {
                    libc::readlinkat(
                        parent.as_raw_fd(),
                        cname.as_ptr(),
                        text.as_mut_ptr().cast(),
                        text.len(),
                    )
                };
                if length <= 0 || length > 4096 {
                    return Err(RepoError("invalid_proot_object_link"));
                }
                let text = &text[..length as usize];
                // Reject lexical traversal and repeated separators before Path
                // normalizes them. All accepted targets are absolute siblings.
                if text.first() != Some(&b'/')
                    || text
                        .split(|b| *b == b'/')
                        .skip(1)
                        .any(|s| s.is_empty() || s == b"." || s == b"..")
                {
                    return Err(RepoError("invalid_proot_object_link"));
                }
                let target = Path::new(OsStr::from_bytes(text));
                if target.parent().is_none_or(|p| {
                    trusted_android_alias(p) != trusted_android_alias(expected_parent)
                }) {
                    return Err(RepoError("invalid_proot_object_link"));
                }
                let sibling = target
                    .file_name()
                    .filter(|s| proot_backing_name(s.as_bytes(), kind))
                    .ok_or(RepoError("invalid_proot_object_link"))?;
                // Discard the target parent. Even a concurrent namespace graft
                // cannot redirect this pinned directory descriptor elsewhere.
                name = sibling.to_os_string();
            }
            libc::S_IFREG => {
                let fd = open_child(parent.as_raw_fd(), &name)
                    .map_err(|_| RepoError("invalid_proot_object_link"))?;
                let after = stat(&fd)?;
                if after.st_mode & libc::S_IFMT != libc::S_IFREG
                    || after.st_nlink != 1
                    || after.st_dev != before.st_dev
                    || after.st_ino != before.st_ino
                {
                    return Err(RepoError("invalid_proot_object_link"));
                }
                return Ok(fd);
            }
            _ => return Err(RepoError("invalid_proot_object_link")),
        }
    }
}

fn validate_snapshot_objects(path: &Path) -> Result<()> {
    let repository = Repository::open_bare(path)?;
    let odb = repository.odb()?;
    let mut ids = Vec::new();
    let mut too_many = false;
    let enumerated = odb.foreach(|id| {
        if ids.len() >= MAX_SNAPSHOT_FILES {
            too_many = true;
            return false;
        }
        ids.push(*id);
        true
    });
    if too_many {
        return Err(RepoError("repository_too_large"));
    }
    enumerated.map_err(|_| RepoError("repository_object_hash_mismatch"))?;
    let mut decoded = 0u64;
    for id in ids {
        let (size, kind) = odb
            .read_header(id)
            .map_err(|_| RepoError("repository_object_hash_mismatch"))?;
        decoded = decoded
            .checked_add(size as u64)
            .ok_or(RepoError("repository_too_large"))?;
        if size > MAX_DECODED_OBJECT_BYTES || decoded > MAX_SNAPSHOT_BYTES {
            return Err(RepoError("repository_too_large"));
        }
        let object = odb
            .read(id)
            .map_err(|_| RepoError("repository_object_hash_mismatch"))?;
        if object.data().len() != size
            || object.kind() != kind
            || Oid::hash_object(kind, object.data())
                .map_err(|_| RepoError("repository_object_hash_mismatch"))?
                != id
        {
            return Err(RepoError("repository_object_hash_mismatch"));
        }
    }
    Ok(())
}

fn copy_directory(
    source: &OwnedFd,
    destination: &Path,
    budget: &mut (u64, usize),
    depth: usize,
    git_root: &Path,
    relative: &Path,
) -> Result<()> {
    if depth > 128 {
        return Err(RepoError("repository_too_large"));
    }
    let duplicate = unsafe { libc::dup(source.as_raw_fd()) };
    if duplicate < 0 {
        return Err(RepoError("repository_io"));
    }
    let dir = unsafe { libc::fdopendir(duplicate) };
    if dir.is_null() {
        unsafe {
            libc::close(duplicate);
        }
        return Err(RepoError("repository_io"));
    }
    struct Dir(*mut libc::DIR);
    impl Drop for Dir {
        fn drop(&mut self) {
            unsafe {
                libc::closedir(self.0);
            }
        }
    }
    let dir = Dir(dir);
    loop {
        let entry = unsafe { libc::readdir(dir.0) };
        if entry.is_null() {
            break;
        }
        let name = unsafe { CStr::from_ptr((*entry).d_name.as_ptr()) }.to_bytes();
        if name == b"." || name == b".." {
            continue;
        }
        budget.1 += 1;
        if budget.1 > MAX_SNAPSHOT_FILES {
            return Err(RepoError("repository_too_large"));
        }
        let name = OsStr::from_bytes(name);
        let object_kind = proot_object_container(relative);
        if object_kind.is_some_and(|kind| proot_scratch_name(name.as_bytes(), kind)) {
            // These are backing/temporary aliases, never logical Git objects.
            // Do not open them: unused malicious scratch entries cannot grant
            // the privileged reader authority over any target.
            continue;
        }
        let observed = child_stat(source, name)?.ok_or(RepoError("repository_changed"))?;
        let child = if observed.st_mode & libc::S_IFMT == libc::S_IFLNK {
            let kind = object_kind
                .filter(|kind| logical_object_name(name.as_bytes(), *kind))
                .ok_or(RepoError("unsafe_repository_path"))?;
            open_proot_object(source, name, &git_root.join(relative), kind)?
        } else {
            open_child(source.as_raw_fd(), name)?
        };
        let metadata = stat(&child)?;
        let output = destination.join(name);
        match metadata.st_mode & libc::S_IFMT {
            libc::S_IFDIR => {
                fs::create_dir(&output)?;
                copy_directory(
                    &child,
                    &output,
                    budget,
                    depth + 1,
                    git_root,
                    &relative.join(name),
                )?;
            }
            libc::S_IFREG => {
                if metadata.st_nlink != 1 {
                    return Err(RepoError("shared_repository_objects"));
                }
                if metadata.st_size < 0 {
                    return Err(RepoError("unsafe_repository_metadata"));
                }
                budget.0 = budget
                    .0
                    .checked_add(metadata.st_size as u64)
                    .ok_or(RepoError("repository_too_large"))?;
                if budget.0 > MAX_SNAPSHOT_BYTES {
                    return Err(RepoError("repository_too_large"));
                }
                let mut input = File::from(child).take((metadata.st_size as u64) + 1);
                let mut out = OpenOptions::new()
                    .create_new(true)
                    .write(true)
                    .mode(0o600)
                    .open(output)?;
                let bytes = std::io::copy(&mut input, &mut out)?;
                if bytes != metadata.st_size as u64 {
                    return Err(RepoError("repository_changed"));
                }
                let after = input.get_ref().metadata()?;
                use std::os::unix::fs::MetadataExt;
                if after.dev() != metadata.st_dev as u64
                    || after.ino() != metadata.st_ino as u64
                    || after.len() != metadata.st_size as u64
                    || after.nlink() != 1
                    || after.mtime() != metadata.st_mtime as i64
                    || after.ctime() != metadata.st_ctime as i64
                    || after.mtime_nsec() != metadata.st_mtime_nsec as i64
                    || after.ctime_nsec() != metadata.st_ctime_nsec as i64
                {
                    return Err(RepoError("repository_changed"));
                }
            }
            _ => return Err(RepoError("unsafe_repository_metadata")),
        }
    }
    Ok(())
}
fn atomic_json(path: &Path, value: &impl Serialize) -> Result<()> {
    let bytes = serde_json::to_vec(value).map_err(|_| RepoError("receipt_encoding"))?;
    let temporary = path.with_extension(format!("{}.pending", uuid::Uuid::new_v4()));
    let mut file = OpenOptions::new()
        .create_new(true)
        .write(true)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(&temporary)?;
    file.write_all(&bytes)?;
    file.sync_all()?;
    fs::rename(&temporary, path)?;
    sync_dir(path.parent().unwrap())?;
    Ok(())
}
fn read_json<T: for<'a> Deserialize<'a>>(path: &Path) -> Result<T> {
    let file = OpenOptions::new()
        .read(true)
        .custom_flags(libc::O_NOFOLLOW)
        .open(path)?;
    serde_json::from_reader(file.take(1024 * 1024)).map_err(|_| RepoError("invalid_receipt"))
}
fn sync_dir(path: &Path) -> Result<()> {
    File::open(path)?.sync_all()?;
    Ok(())
}
fn sync_all(path: &Path) -> Result<()> {
    for entry in fs::read_dir(path)? {
        let entry = entry?;
        let metadata = fs::symlink_metadata(entry.path())?;
        if metadata.is_dir() {
            sync_all(&entry.path())?;
        } else if metadata.is_file() {
            File::open(entry.path())?.sync_all()?;
        } else {
            return Err(RepoError("unsafe_repository_metadata"));
        }
    }
    sync_dir(path)
}
fn sync_tree_refs(repo: &Repository) -> Result<()> {
    for name in ["refs/heads/main", "logs/refs/heads/main"] {
        let path = repo.path().join(name);
        if path.exists() {
            File::open(&path)?.sync_all()?;
            sync_dir(path.parent().unwrap())?;
        }
    }
    sync_dir(repo.path())
}
fn install_hooks(repo: &Repository) -> Result<()> {
    let hooks = repo.path().join("hooks");
    fs::create_dir_all(&hooks)?;
    // No token exception: daemon uses libgit2 and its own authenticated authority.
    for (name,script) in [
        ("reference-transaction", "#!/bin/sh\n[ \"$1\" = prepared ] || exit 0\nwhile read old new ref; do\n [ \"$ref\" != refs/heads/main ] || exit 1\ndone\n"),
        ("pre-receive", "#!/bin/sh\nwhile read old new ref; do\n [ \"$ref\" != refs/heads/main ] || exit 1\ndone\n")
    ] {
        fs::write(hooks.join(name),script)?;
        fs::set_permissions(hooks.join(name),fs::Permissions::from_mode(0o700))?;
    }
    Ok(())
}
