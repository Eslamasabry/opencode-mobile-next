//! Static failure frames and the real shipped-ELF inspection subject entrypoint.
use serde_json::{json, Value};
use std::{
    fs,
    io::{BufRead, BufReader, Read},
    os::{
        fd::FromRawFd,
        unix::fs::{symlink, PermissionsExt},
    },
    path::PathBuf,
    process::{Child, Command, Stdio},
    sync::mpsc,
    time::{Duration, Instant},
};
struct Running(Child);
impl Drop for Running {
    fn drop(&mut self) {
        let _ = self.0.kill();
        let _ = self.0.wait();
    }
}
fn root() -> tempfile::TempDir {
    let base = std::env::var_os("OC_ENGINE_TEST_ROOT")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/home/eslam/Storage/tmp/oc-phone-engine-tests"));
    fs::create_dir_all(&base).unwrap();
    tempfile::Builder::new()
        .prefix("startup-report-")
        .tempdir_in(base)
        .unwrap()
}
fn launch(args: &[&str]) -> Running {
    Running(
        Command::new(env!("CARGO_BIN_EXE_oc-phone-engine"))
            .args(args)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .env("PRIVATE_CANARY", "must-never-echo-test-secret")
            .spawn()
            .unwrap(),
    )
}
fn line(child: &mut Running) -> String {
    let output = child.0.stdout.take().unwrap();
    let (send, recv) = mpsc::channel();
    std::thread::spawn(move || {
        let mut value = String::new();
        let result = BufReader::new(output)
            .take(1025)
            .read_line(&mut value)
            .map(|_| value);
        let _ = send.send(result);
    });
    recv.recv_timeout(Duration::from_secs(5))
        .expect("bounded private frame missing")
        .unwrap()
}
fn exited(child: &mut Running) -> std::process::ExitStatus {
    let deadline = Instant::now() + Duration::from_secs(5);
    loop {
        if let Some(status) = child.0.try_wait().unwrap() {
            return status;
        }
        assert!(Instant::now() < deadline, "startup child failed to exit");
        std::thread::sleep(Duration::from_millis(10));
    }
}
#[test]
fn missing_server_credentials_reports_typed_failure_without_ready_or_secrets() {
    let root = root();
    let private = root.path().join("oc.teamEngine.test");
    fs::create_dir(&private).unwrap();
    let token = private.join("auth.token");
    fs::write(&token, "ab".repeat(32)).unwrap();
    fs::set_permissions(&token, fs::Permissions::from_mode(0o600)).unwrap();
    let config = private.join("config.json");
    fs::write(&config, json!({"schemaVersion":1,"profileId":"test","privateRoot":private,
        "workerRoot":root.path().join("workers"),"guestWorkerRoot":"/root/aiteam/work/test",
        "port":0,"authTokenFile":token,"serverCredentialFile":private.join("missing-credentials"),
        "serverBaseUrl":"http://127.0.0.1:4097","sourceRoots":[],"boundary":{"verified":false,"reason":"test"}}).to_string()).unwrap();
    let mut child = launch(&["--config", config.to_str().unwrap()]);
    let value: Value = serde_json::from_str(&line(&mut child)).unwrap();
    assert_eq!(
        value,
        json!({"schemaVersion":1,"startupError":"server_auth_unavailable"})
    );
    assert!(!exited(&mut child).success());
}
#[test]
fn malformed_configuration_returns_only_static_bounded_private_frame() {
    let root = root();
    let file = root.path().join("config.json");
    fs::write(&file, "providerPassword=must-never-echo-test-secret").unwrap();
    let mut child = launch(&["--config", file.to_str().unwrap()]);
    let output = line(&mut child);
    assert!(output.len() < 128);
    assert_eq!(
        serde_json::from_str::<Value>(&output).unwrap(),
        json!({"schemaVersion":1,"startupError":"configInvalid"})
    );
    assert!(!output.contains("must-never-echo-test-secret"));
    assert!(!output.contains(root.path().to_str().unwrap()));
    assert!(!exited(&mut child).success());
}
fn fixture_subject(root: &tempfile::TempDir) -> PathBuf {
    let fixture = root
        .path()
        .join(format!(".phone-engine-view-{}", uuid::Uuid::new_v4()));
    fs::create_dir(&fixture).unwrap();
    fs::set_permissions(&fixture, fs::Permissions::from_mode(0o700)).unwrap();
    fs::write(fixture.join("sentinel"), "private-fixture-control").unwrap();
    fs::set_permissions(fixture.join("sentinel"), fs::Permissions::from_mode(0o600)).unwrap();
    fixture
}
#[test]
fn actual_subject_keeps_private_fd_live_closes_inherited_fds_and_exits_on_eof() {
    let root = root();
    let fixture = fixture_subject(&root);
    // Deliberately non-CLOEXEC descriptors; subject must not inherit this pipe.
    let mut pipe = [-1; 2];
    assert_eq!(unsafe { libc::pipe(pipe.as_mut_ptr()) }, 0);
    let mut reader = unsafe { fs::File::from_raw_fd(pipe[0]) };
    let writer = unsafe { fs::File::from_raw_fd(pipe[1]) };
    assert_eq!(
        unsafe { libc::fcntl(pipe[0], libc::F_SETFL, libc::O_NONBLOCK) },
        0
    );
    let mut child = launch(&["--proot-proof-subject", fixture.to_str().unwrap()]);
    drop(writer);
    let ready = line(&mut child);
    let fields = ready.trim_end().split(':').collect::<Vec<_>>();
    assert_eq!(fields.len(), 3);
    assert_eq!(fields[0], "READY_SUBJECT");
    assert_eq!(fields[1].parse::<u32>().unwrap(), child.0.id());
    assert!(fields[2].parse::<i32>().unwrap() > 2);
    assert!(child.0.try_wait().unwrap().is_none());
    assert_eq!(
        reader.read(&mut [0u8; 1]).unwrap(),
        0,
        "subject inherited app descriptor"
    );
    drop(child.0.stdin.take());
    assert!(exited(&mut child).success());
    assert_eq!(
        fs::read_to_string(fixture.join("sentinel")).unwrap(),
        "private-fixture-control"
    );
}
#[test]
fn subject_refuses_symlink_sentinel_and_invalid_fixture_without_echoing_path() {
    let root = root();
    let fixture = fixture_subject(&root);
    let outside = root.path().join("outside");
    fs::write(&outside, "outside-must-survive").unwrap();
    fs::remove_file(fixture.join("sentinel")).unwrap();
    symlink(&outside, fixture.join("sentinel")).unwrap();
    let mut child = launch(&["--proot-proof-subject", fixture.to_str().unwrap()]);
    assert_eq!(
        serde_json::from_str::<Value>(&line(&mut child)).unwrap(),
        json!({"schemaVersion":1,"startupError":"prootProofSubjectUnavailable"})
    );
    assert!(!exited(&mut child).success());
    assert_eq!(
        fs::read_to_string(&outside).unwrap(),
        "outside-must-survive"
    );
    let mut child = launch(&["--proot-proof-subject", root.path().to_str().unwrap()]);
    assert_eq!(
        serde_json::from_str::<Value>(&line(&mut child)).unwrap()["startupError"],
        "prootProofSubjectUnavailable"
    );
    assert!(!exited(&mut child).success());
}
