//! Exercise the packaged daemon entrypoint, not a unit-test helper process.
use hmac::{Hmac, Mac};
use serde_json::{json, Value};
use sha2::Sha256;
use std::{
    fs,
    io::{BufRead, BufReader, Read, Write},
    net::{TcpListener, TcpStream},
    os::unix::fs::{symlink, PermissionsExt},
    path::PathBuf,
    process::{Child, Command, Stdio},
    sync::mpsc,
    time::{Duration, Instant},
};

struct Running {
    child: Child,
}
impl Drop for Running {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}
struct Fixture {
    _root: tempfile::TempDir,
    config: PathBuf,
    token: String,
}
fn fixture(aliased: bool) -> Fixture {
    let base = std::env::var_os("OC_ENGINE_TEST_ROOT")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/home/eslam/Storage/tmp/oc-phone-engine-tests"));
    fs::create_dir_all(&base).unwrap();
    let root = tempfile::Builder::new()
        .prefix("daemon-startup-")
        .tempdir_in(base)
        .unwrap();
    let actual = root.path().join("data");
    fs::create_dir(&actual).unwrap();
    let app = if aliased {
        let alias = root.path().join("user-0");
        symlink(&actual, &alias).unwrap();
        alias
    } else {
        actual
    };
    let private = app.join("oc.teamEngine.phone");
    fs::create_dir(&private).unwrap();
    let token = "ab".repeat(32);
    let auth = private.join("auth");
    let credentials = private.join("credentials");
    fs::write(&auth, &token).unwrap();
    fs::write(
        &credentials,
        r#"{"username":"opencode","password":"isolated-fixture"}"#,
    )
    .unwrap();
    for file in [&auth, &credentials] {
        fs::set_permissions(file, fs::Permissions::from_mode(0o600)).unwrap();
    }
    let config = private.join("config.json");
    fs::write(&config, json!({"schemaVersion":1,"profileId":"phone","privateRoot":private,"workerRoot":app.join("workers"),"guestWorkerRoot":"/root/aiteam/work/phone","port":0,"authTokenFile":auth,"serverCredentialFile":credentials,"serverBaseUrl":"http://127.0.0.1:4097","sourceRoots":[],"boundary":{"verified":false,"reason":"fixtureUnverified"}}).to_string()).unwrap();
    Fixture {
        _root: root,
        config,
        token,
    }
}
fn launch(f: &Fixture) -> (Running, u16) {
    let path = f.config.clone();
    // ProcessBuilder has the same short-lived launcher-thread shape on Android.
    let (child_send, child_recv) = mpsc::channel();
    let (release, released) = mpsc::channel();
    let launcher = std::thread::spawn(move || {
        let child = Command::new(env!("CARGO_BIN_EXE_oc-phone-engine"))
            .arg("--config")
            .arg(path)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()
            .unwrap();
        child_send.send(child).unwrap();
        // Android's MethodChannel launcher stays alive through awaitReady.
        let _ = released.recv();
    });
    let child = child_recv.recv_timeout(Duration::from_secs(5)).unwrap();
    let mut running = Running { child };
    let stdout = running.child.stdout.take().unwrap();
    let (send, recv) = mpsc::channel();
    std::thread::spawn(move || {
        let mut line = String::new();
        let result = BufReader::new(stdout)
            .take(1025)
            .read_line(&mut line)
            .map(|_| line);
        let _ = send.send(result);
    });
    let line = recv
        .recv_timeout(Duration::from_secs(5))
        .expect("real daemon did not publish readiness")
        .unwrap();
    let ready: Value =
        serde_json::from_str(&line).expect("daemon exited before authenticated readiness");
    assert_eq!(ready["schemaVersion"], 2);
    assert_eq!(ready["profileId"], "phone");
    assert_eq!(ready.as_object().unwrap().len(), 6);
    assert!(line.len() <= 1025); // The bounded frame plus its terminating newline.
    let port = u16::try_from(ready["port"].as_u64().unwrap()).unwrap();
    let identity = ready["identity"].as_object().unwrap();
    assert_eq!(identity.len(), 5);
    let pid = identity["pid"].as_i64().unwrap();
    let ticks = identity["startTicks"].as_i64().unwrap();
    let parent = identity["parent"].as_i64().unwrap();
    let group = identity["group"].as_i64().unwrap();
    let session = identity["session"].as_i64().unwrap();
    assert!((2..=i64::from(i32::MAX)).contains(&pid));
    assert!(ticks > 0);
    assert!((0..=i64::from(i32::MAX)).contains(&parent));
    assert!((1..=i64::from(i32::MAX)).contains(&group));
    assert!((0..=i64::from(i32::MAX)).contains(&session));
    assert_eq!(pid, i64::from(running.child.id()));
    // Spawning on a short-lived thread does not change the child process's
    // parent TGID, inherited process group, or inherited session.
    assert_eq!(parent, i64::from(std::process::id()));
    assert_eq!(group, i64::from(unsafe { libc::getpgrp() }));
    assert_eq!(session, i64::from(unsafe { libc::getsid(0) }));
    // Read our own stat only: the real child is deliberately nondumpable and
    // Android hidepid may deny the parent's /proc/<child>/stat access.
    let parent_stat = fs::read_to_string("/proc/self/stat").unwrap();
    let parent_ticks = parent_stat
        .rsplit_once(')')
        .unwrap()
        .1
        .split_ascii_whitespace()
        .nth(19)
        .unwrap()
        .parse::<i64>()
        .unwrap();
    assert!(ticks >= parent_ticks);
    let message = format!(
        "oc-phone-engine-ready-v2\nphone\n{port}\n{}\n{pid}\n{ticks}\n{parent}\n{group}\n{session}",
        ready["nonce"].as_str().unwrap()
    );
    let mut mac = Hmac::<Sha256>::new_from_slice(f.token.as_bytes()).unwrap();
    mac.update(message.as_bytes());
    let signature: String = mac
        .finalize()
        .into_bytes()
        .iter()
        .map(|b| format!("{b:02x}"))
        .collect();
    assert_eq!(ready["mac"], signature);
    assert!(!line.contains(&f.token));
    assert_ne!(port, 0);
    release.send(()).unwrap();
    launcher.join().unwrap();
    (running, port)
}
fn health(port: u16, token: &str) -> Value {
    let mut stream = TcpStream::connect(("127.0.0.1", port)).unwrap();
    stream
        .set_read_timeout(Some(Duration::from_secs(5)))
        .unwrap();
    write!(stream, "GET /v1/health HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer {token}\r\nConnection: close\r\n\r\n").unwrap();
    let mut response = String::new();
    stream.read_to_string(&mut response).unwrap();
    assert!(response.starts_with("HTTP/1.1 200"));
    serde_json::from_str(response.split_once("\r\n\r\n").unwrap().1).unwrap()
}
#[test]
fn actual_daemon_survives_launcher_thread_then_exits_on_app_pipe_eof() {
    let f = fixture(false);
    let (mut running, port) = launch(&f);
    std::thread::sleep(Duration::from_millis(100));
    assert!(
        running.child.try_wait().unwrap().is_none(),
        "launcher-thread death killed real daemon"
    );
    assert_eq!(health(port, &f.token)["profileId"], "phone");
    drop(running.child.stdin.take());
    let deadline = Instant::now() + Duration::from_secs(5);
    while running.child.try_wait().unwrap().is_none() {
        assert!(
            Instant::now() < deadline,
            "real daemon outlived app-owned pipe EOF"
        );
        std::thread::sleep(Duration::from_millis(20));
    }
}
#[test]
fn actual_daemon_boots_through_system_data_alias_but_refuses_descendant_link() {
    let f = fixture(true);
    let (_running, port) = launch(&f);
    assert_eq!(health(port, &f.token)["capabilities"]["boundary"], false);
    let mut config: Value = serde_json::from_slice(&fs::read(&f.config).unwrap()).unwrap();
    let workers = PathBuf::from(config["workerRoot"].as_str().unwrap());
    let linked = workers.with_file_name("linked-workers");
    symlink(&workers, &linked).unwrap();
    config["workerRoot"] = json!(linked);
    fs::write(&f.config, config.to_string()).unwrap();
    let mut rejected = Running {
        child: Command::new(env!("CARGO_BIN_EXE_oc-phone-engine"))
            .arg("--config")
            .arg(&f.config)
            .stdin(Stdio::piped())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn()
            .unwrap(),
    };
    let deadline = Instant::now() + Duration::from_secs(5);
    loop {
        if let Some(status) = rejected.child.try_wait().unwrap() {
            assert!(!status.success());
            break;
        }
        assert!(Instant::now() < deadline, "descendant symlink was admitted");
        std::thread::sleep(Duration::from_millis(20));
    }
}
#[test]
fn occupied_legacy_port_never_receives_the_real_daemon_bearer() {
    let squatter = TcpListener::bind(("127.0.0.1", 4098))
        .expect("isolated regression requires port 4098 free");
    squatter.set_nonblocking(true).unwrap();
    let f = fixture(false);
    let (_running, port) = launch(&f);
    assert_ne!(port, 4098);
    assert_eq!(health(port, &f.token)["profileId"], "phone");
    assert_eq!(
        squatter.accept().unwrap_err().kind(),
        std::io::ErrorKind::WouldBlock
    );
}

#[test]
fn actual_cleanup_cli_erases_mode_zero_tree_without_following_escape_links() {
    let f = fixture(false);
    let target = f._root.path().join("delete-target");
    let outside = f._root.path().join("must-survive");
    fs::write(&outside, "retained").unwrap();
    fs::create_dir(&target).unwrap();
    let locked = target.join("locked");
    fs::create_dir(&locked).unwrap();
    fs::write(locked.join("file"), "remove").unwrap();
    symlink(&outside, locked.join("escape")).unwrap();
    fs::set_permissions(&locked, fs::Permissions::from_mode(0)).unwrap();
    let mut child = Running {
        child: Command::new(env!("CARGO_BIN_EXE_oc-phone-engine"))
            .arg("--erase-tree")
            .arg(&target)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn()
            .unwrap(),
    };
    let deadline = Instant::now() + Duration::from_secs(5);
    loop {
        if let Some(status) = child.child.try_wait().unwrap() {
            assert!(status.success());
            break;
        }
        assert!(Instant::now() < deadline, "cleanup timed out");
        std::thread::sleep(Duration::from_millis(20));
    }
    assert!(!target.exists());
    assert_eq!(fs::read_to_string(outside).unwrap(), "retained");
}
