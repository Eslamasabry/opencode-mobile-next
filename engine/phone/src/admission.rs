//! Scoped app authority, not an in-process priority promise.
use crate::{
    chat::{ChatAdmission, ChatHeartbeat, ChatLedger},
    daemon::Engine,
    opencode::GlobalStatusObservation,
};
use serde_json::{json, Value};
use std::{
    fs,
    io::{Read, Write},
    os::unix::fs::OpenOptionsExt,
    time::Duration,
};

pub fn now_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

pub fn app_alive(e: &Engine) -> Option<bool> {
    let pid = e.parent_pid;
    if pid <= 1 {
        return None;
    }
    if unsafe { libc::kill(pid, 0) } == 0 {
        return Some(true);
    }
    match std::io::Error::last_os_error().raw_os_error() {
        Some(libc::ESRCH) => Some(false),
        _ => None,
    }
}

pub fn load_directories(config: &crate::config::Config) -> ChatLedger {
    let mut ledger = ChatLedger::with_worker_root(&config.guest_worker_root);
    let path = config.private_root.join("person-directories.json");
    let file = fs::OpenOptions::new()
        .read(true)
        .custom_flags(libc::O_NOFOLLOW)
        .open(path);
    let Ok(file) = file else {
        return ledger;
    };
    let mut bytes = Vec::new();
    if file.take(1_048_577).read_to_end(&mut bytes).is_err() || bytes.len() > 1_048_576 {
        ledger.observation_unknown();
        return ledger;
    }
    let mut cleaned = false;
    match serde_json::from_slice::<Vec<String>>(&bytes) {
        Ok(dirs) if dirs.len() <= 256 => {
            for dir in dirs {
                cleaned |= ledger.is_worker_directory(&dir);
                if ledger.learn_directory(&dir).is_err() {
                    ledger.observation_unknown();
                    break;
                }
            }
        }
        _ => ledger.observation_unknown(),
    }
    if cleaned && persist_directory_root(&config.private_root, &ledger).is_err() {
        ledger.observation_unknown();
    }
    // Never reload a heartbeat as fresh authority after restart.
    ledger
}

fn persist_directories(e: &Engine, ledger: &ChatLedger) -> Result<(), &'static str> {
    persist_directory_root(&e.config.private_root, ledger)
}

fn persist_directory_root(root: &std::path::Path, ledger: &ChatLedger) -> Result<(), &'static str> {
    let bytes =
        serde_json::to_vec(&ledger.persisted_directories()).map_err(|_| "heartbeatInvalid")?;
    let path = root.join(format!(".person-directories-{}", uuid::Uuid::new_v4()));
    let result = (|| {
        let mut file = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(0o600)
            .custom_flags(libc::O_NOFOLLOW)
            .open(&path)
            .map_err(|_| "heartbeatStorageUnavailable")?;
        file.write_all(&bytes)
            .map_err(|_| "heartbeatStorageUnavailable")?;
        file.sync_all().map_err(|_| "heartbeatStorageUnavailable")?;
        fs::rename(&path, root.join("person-directories.json"))
            .map_err(|_| "heartbeatStorageUnavailable")?;
        fs::File::open(root)
            .and_then(|f| f.sync_all())
            .map_err(|_| "heartbeatStorageUnavailable")
    })();
    let _ = fs::remove_file(path);
    result
}

pub async fn accept(e: &Engine, heartbeat: ChatHeartbeat) -> Result<Value, &'static str> {
    // WRITE acknowledgment serializes with the READ-held engine prompt admission.
    // The app waits for this acknowledgment before dispatching its human turn.
    let mut ledger = e.chat.write().await;
    let store = e.store.lock().map_err(|_| "storeUnavailable")?;
    store.workspace().map_err(|_| "profileDeleted")?;
    let mut proposed = ledger.clone();
    let mut heartbeat = heartbeat;
    heartbeat
        .directories
        .retain(|dir| !worker_directory(&e.config.guest_worker_root, dir));
    proposed.accept(heartbeat, now_ms())?;
    persist_directories(e, &proposed)?;
    *ledger = proposed;
    drop(store);
    Ok(
        json!({"accepted":true,"admission":label(ledger.admission(now_ms(),app_alive(e),&crate::daemon::team_sessions(e)))}),
    )
}

pub fn label(value: ChatAdmission) -> &'static str {
    match value {
        ChatAdmission::Idle => "idle",
        ChatAdmission::Busy => "busy",
        ChatAdmission::Unknown => "unknown",
        ChatAdmission::PollKnownDirectories => "observing",
    }
}

pub async fn idle(e: &Engine, ledger: &ChatLedger) -> Result<bool, &'static str> {
    match ledger.admission(now_ms(), app_alive(e), &crate::daemon::team_sessions(e)) {
        ChatAdmission::Idle => Ok(true),
        ChatAdmission::Busy => Ok(false),
        ChatAdmission::Unknown => Err("chatStatusUnknown"),
        ChatAdmission::PollKnownDirectories => {
            if !*e
                .observer_connected
                .lock()
                .map_err(|_| "chatStatusUnknown")?
            {
                return Err("chatStatusUnknown");
            }
            e.server
                .person_directory_status(&ledger.directories(), &crate::daemon::team_sessions(e))
                .await
                .map_err(|_| "chatStatusUnknown")
        }
    }
}

pub async fn observe(e: std::sync::Arc<Engine>) {
    loop {
        if let Ok(mut connected) = e.observer_connected.lock() {
            *connected = false;
        }
        match e.server.global_status_observer().await {
            Ok(mut observer) => {
                if let Ok(mut connected) = e.observer_connected.lock() {
                    *connected = true;
                }
                loop {
                    match observer.next().await {
                        Ok(GlobalStatusObservation::Status {
                            directory,
                            session_id,
                            busy,
                        }) => {
                            if worker_directory(&e.config.guest_worker_root, &directory)
                                || crate::daemon::team_sessions(&e).contains(&session_id)
                            {
                                continue;
                            }
                            let mut ledger = e.chat.write().await;
                            if ledger.observe(&directory, &session_id, busy).is_err()
                                || persist_directories(&e, &ledger).is_err()
                            {
                                ledger.observation_unknown();
                            }
                        }
                        Ok(GlobalStatusObservation::Other) => {}
                        Err(_) => {
                            if let Ok(mut connected) = e.observer_connected.lock() {
                                *connected = false;
                            }
                            e.chat.write().await.observation_unknown();
                            break;
                        }
                    }
                }
            }
            Err(_) => {
                e.chat.write().await.observation_unknown();
            }
        }
        if let Ok(mut connected) = e.observer_connected.lock() {
            *connected = false;
        }
        // Reconnection never clears cached busy evidence.
        tokio::time::sleep(Duration::from_secs(2)).await;
    }
}

fn worker_directory(root: &str, directory: &str) -> bool {
    let root = root.trim_end_matches('/');
    directory == root
        || directory
            .strip_prefix(root)
            .is_some_and(|suffix| suffix.starts_with('/'))
}

pub async fn reconcile_busy(e: &Engine) {
    let (revision, needed) = {
        let ledger = e.chat.read().await;
        (ledger.observation_revision(), ledger.needs_reconciliation())
    };
    if !needed || !observer_connected(e) {
        return;
    }
    // OC2's active list covers the whole server, so no known chat folder is
    // needed. Requiring one left a stream drop before the person opened any
    // chat project "unknown" forever (issue #111/#112 Run A).
    let snapshot = e
        .server
        .server_idle_except(&crate::daemon::team_sessions(e))
        .await
        .map_err(|_| ());
    // A malformed frame, disconnect, or a newer status event increments the
    // ledger revision. Reconnecting alone cannot produce admission authority.
    let mut ledger = e.chat.write().await;
    ledger.reconcile_snapshot(revision, observer_connected(e), snapshot);
}

fn observer_connected(e: &Engine) -> bool {
    e.observer_connected
        .lock()
        .map(|connected| *connected)
        .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn restart_cleans_worker_directories_without_reviving_authority() {
        let root = std::env::var_os("OC_ENGINE_TEST_ROOT")
            .map(std::path::PathBuf::from)
            .unwrap_or_else(|| {
                std::path::PathBuf::from("/home/eslam/Storage/tmp/aiteam-phone-engine-tests")
            })
            .join(format!("admission-{}", uuid::Uuid::new_v4()));
        fs::create_dir_all(&root).unwrap();
        let config = crate::config::Config {
            schema_version: 1,
            profile_id: "test".into(),
            private_root: root.clone(),
            worker_root: root.join("unused-workers"),
            guest_worker_root: "/root/aiteam/work/test".into(),
            port: 4100,
            auth_token_file: root.join("unused-token"),
            server_credential_file: root.join("unused-credential"),
            server_base_url: "http://127.0.0.1:4097".into(),
            source_roots: vec![],
            boundary: crate::config::Boundary {
                verified: false,
                reason: "test".into(),
                restart_required: false,
                receipt_file: None,
                public_key_file: None,
                generation: None,
            },
        };
        let path = root.join("person-directories.json");
        fs::write(
            &path,
            serde_json::to_vec(&vec![
                "/root/aiteam/work/test/repo/old-task",
                "/root/projects/person",
            ])
            .unwrap(),
        )
        .unwrap();
        let ledger = load_directories(&config);
        assert_eq!(ledger.directories(), vec!["/root/projects/person"]);
        assert_eq!(
            serde_json::from_slice::<Vec<String>>(&fs::read(&path).unwrap()).unwrap(),
            vec!["/root/projects/person"]
        );
        assert_eq!(
            ledger.admission(1000, Some(true), &[]),
            ChatAdmission::Unknown
        );
        assert_eq!(
            ledger.admission(1000, Some(false), &[]),
            ChatAdmission::PollKnownDirectories
        );
        fs::remove_dir_all(root).unwrap();
    }
}
