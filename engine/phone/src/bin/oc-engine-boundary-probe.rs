//! Destructive probes use an isolated sentinel fixture, NEVER production state.
//! Exit 0 means all listed attacks were denied and the worker positive control
//! succeeded; it is only one component of the full Android/proot proof.
use std::ffi::CString;
use std::os::unix::ffi::OsStrExt;
use std::os::unix::fs::MetadataExt;
use std::path::{Path, PathBuf};

fn prepare_git_fixture(root: &Path) -> Result<String, &'static str> {
    if !root.is_absolute()
        || root.file_name().and_then(|n| n.to_str()) != Some("protected")
        || root.canonicalize().map_err(|_| "fixture-invalid")? != root
    {
        return Err("fixture-invalid");
    }
    let parent = root.parent().ok_or("fixture-invalid")?;
    let generation = parent
        .file_name()
        .and_then(|n| n.to_str())
        .and_then(|n| n.strip_prefix(".phone-engine-proof-"))
        .ok_or("fixture-invalid")?;
    if uuid::Uuid::parse_str(generation).is_err() {
        return Err("fixture-invalid");
    }
    for (file, expected) in [
        (parent.join(".native-proof-fixture"), generation.as_bytes()),
        (
            root.join("sentinel"),
            b"proof-only-canonical-state".as_slice(),
        ),
    ] {
        let meta = std::fs::symlink_metadata(&file).map_err(|_| "fixture-invalid")?;
        if !meta.is_file()
            || meta.uid() != unsafe { libc::geteuid() }
            || meta.mode() & 0o077 != 0
            || meta.len() > 128
            || std::fs::read(file).map_err(|_| "fixture-invalid")? != expected
        {
            return Err("fixture-invalid");
        }
    }
    let entries = std::fs::read_dir(root)
        .map_err(|_| "fixture-invalid")?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| "fixture-invalid")?;
    if entries.len() != 1 || entries[0].file_name() != "sentinel" {
        return Err("fixture-not-empty");
    }
    let repo = git2::Repository::init_opts(
        root,
        git2::RepositoryInitOptions::new()
            .initial_head("main")
            .no_reinit(true),
    )
    .map_err(|_| "fixture-init-failed")?;
    let mut index = repo.index().map_err(|_| "fixture-init-failed")?;
    index
        .add_path(Path::new("sentinel"))
        .map_err(|_| "fixture-init-failed")?;
    index.write().map_err(|_| "fixture-init-failed")?;
    let tree_id = index.write_tree().map_err(|_| "fixture-init-failed")?;
    let tree = repo.find_tree(tree_id).map_err(|_| "fixture-init-failed")?;
    let signature = git2::Signature::new(
        "boundary-proof",
        "proof@invalid.example",
        &git2::Time::new(0, 0),
    )
    .map_err(|_| "fixture-init-failed")?;
    let main = repo
        .commit(
            Some("refs/heads/main"),
            &signature,
            &signature,
            "isolated boundary proof",
            &tree,
            &[],
        )
        .map_err(|_| "fixture-init-failed")?;
    repo.set_head("refs/heads/main")
        .map_err(|_| "fixture-init-failed")?;
    if repo.head().ok().and_then(|r| r.target()) != Some(main) {
        return Err("fixture-init-failed");
    }
    Ok(main.to_string())
}

// Android libc omits this AArch64 name. asm-generic/unistd.h defines
// __NR3264_truncate=45; bionic uses that 64-bit syscall on this target.
#[cfg(all(target_os = "android", target_arch = "aarch64"))]
const SYS_TRUNCATE: libc::c_long = 45;
#[cfg(not(all(target_os = "android", target_arch = "aarch64")))]
const SYS_TRUNCATE: libc::c_long = libc::SYS_truncate;

fn denied(control: &str, value: libc::c_long) -> bool {
    let errno = if value == -1 {
        std::io::Error::last_os_error().raw_os_error().unwrap_or(0)
    } else {
        0
    };
    denied_with(control, value, errno)
}

fn denied_with(control: &str, value: libc::c_long, errno: i32) -> bool {
    // Landlock deliberately returns EXDEV when REFER/link rights would cross
    // the allowed hierarchy. Other attacks still require permission denial.
    let passed = value == -1
        && (matches!(errno, libc::EACCES | libc::EPERM)
            || (matches!(control, "link" | "rename") && errno == libc::EXDEV));
    if !passed {
        // Static control identifiers and numeric results only. Never print a
        // path, environment/argv value, sentinel contents or raw OS error.
        println!("boundary-control:{control}:result:{value}:errno:{errno}");
    }
    passed
}

/// How a disposable attack child ended.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Attempt {
    /// The syscall returned; `(value, errno)`.
    Returned(libc::c_long, i32),
    /// The kernel killed the child with this signal before it could report.
    Signaled(i32),
    /// No report, no signal, or no answer within the deadline.
    Lost,
}

/// Runs one raw attack syscall in a disposable child.
///
/// Android's app seccomp policy (bionic, inherited by every process the app
/// starts) answers a syscall outside its allowlist with `SECCOMP_RET_TRAP`.
/// The kernel applies the most restrictive action of all stacked filters,
/// and TRAP outranks the sandbox's own `SECCOMP_RET_ERRNO`, so such a call
/// raises SIGSYS instead of returning EPERM. Android 16 added Landlock to
/// that allowlist (so a 6.12 kernel now takes the Landlock tier) but still
/// traps `io_uring_setup`: in-process, that attack killed the whole probe and
/// every Android 16 start failed with `boundary_proof_failed` (issue #111).
///
/// A SIGSYS death means the kernel refused the call before it ran; it is
/// counted as denied in [`attack_denied`]. Every other outcome keeps its
/// previous meaning. No allocation, lock or Rust destructor runs in the child.
fn attempt(attack: &dyn Fn() -> libc::c_long) -> Attempt {
    let mut pipe = [-1; 2];
    if unsafe { libc::pipe2(pipe.as_mut_ptr(), libc::O_CLOEXEC) } != 0 {
        return Attempt::Lost;
    }
    let pid = unsafe { libc::fork() };
    if pid == 0 {
        unsafe {
            libc::close(pipe[0]);
            // The kernel, not an inherited crash handler, must end a trapped
            // child: no tombstone, no core dump of probe memory.
            libc::signal(libc::SIGSYS, libc::SIG_DFL);
            let limit = libc::rlimit {
                rlim_cur: 0,
                rlim_max: 0,
            };
            libc::setrlimit(libc::RLIMIT_CORE, &limit);
            let value = attack() as i64;
            let errno = if value == -1 {
                std::io::Error::last_os_error().raw_os_error().unwrap_or(0)
            } else {
                0
            };
            let mut report = [0_u8; 12];
            report[..8].copy_from_slice(&value.to_ne_bytes());
            report[8..].copy_from_slice(&errno.to_ne_bytes());
            let written = libc::write(pipe[1], report.as_ptr().cast(), report.len());
            libc::_exit(if written == report.len() as isize {
                0
            } else {
                1
            });
        }
    }
    unsafe { libc::close(pipe[1]) };
    if pid < 0 {
        unsafe { libc::close(pipe[0]) };
        return Attempt::Lost;
    }
    let status = wait_child(pid, std::time::Duration::from_secs(5));
    let mut report = [0_u8; 12];
    let mut filled = 0;
    while filled < report.len() {
        let n = unsafe {
            libc::read(
                pipe[0],
                report[filled..].as_mut_ptr().cast(),
                report.len() - filled,
            )
        };
        if n <= 0 {
            break;
        }
        filled += n as usize;
    }
    unsafe { libc::close(pipe[0]) };
    match status {
        Some(status) if libc::WIFSIGNALED(status) => Attempt::Signaled(libc::WTERMSIG(status)),
        Some(status)
            if libc::WIFEXITED(status)
                && libc::WEXITSTATUS(status) == 0
                && filled == report.len() =>
        {
            let value = i64::from_ne_bytes(report[..8].try_into().unwrap_or([0; 8]));
            let errno = i32::from_ne_bytes(report[8..].try_into().unwrap_or([0; 4]));
            Attempt::Returned(value as libc::c_long, errno)
        }
        _ => Attempt::Lost,
    }
}

/// Waits for the exact owned child; a hung or stopped one is killed and
/// reaped at the deadline and reported as `None`.
fn wait_child(pid: libc::pid_t, timeout: std::time::Duration) -> Option<i32> {
    let deadline = std::time::Instant::now() + timeout;
    loop {
        let mut status = 0;
        let waited = unsafe { libc::waitpid(pid, &mut status, libc::WNOHANG) };
        if waited == pid {
            return Some(status);
        }
        if waited < 0 && std::io::Error::last_os_error().raw_os_error() != Some(libc::EINTR) {
            return None;
        }
        if std::time::Instant::now() >= deadline {
            unsafe { libc::kill(pid, libc::SIGKILL) };
            loop {
                let waited = unsafe { libc::waitpid(pid, &mut status, 0) };
                if waited >= 0
                    || std::io::Error::last_os_error().raw_os_error() != Some(libc::EINTR)
                {
                    break;
                }
            }
            return None;
        }
        std::thread::sleep(std::time::Duration::from_millis(2));
    }
}

/// The verdict for one isolated attack. Only a permission-style return or a
/// seccomp SIGSYS (the syscall never executed) is a denial.
fn attack_denied(control: &str, outcome: Attempt) -> bool {
    match outcome {
        Attempt::Returned(value, errno) => denied_with(control, value, errno),
        Attempt::Signaled(signal) if signal == libc::SIGSYS => true,
        Attempt::Signaled(signal) => {
            println!("boundary-control:{control}:signal:{signal}");
            false
        }
        Attempt::Lost => {
            println!("boundary-control:{control}:no-answer");
            false
        }
    }
}

fn path(value: &Path) -> CString {
    CString::new(value.as_os_str().as_bytes()).expect("fixture path")
}

fn check(root: &Path, worker: &Path, parent: i32) -> bool {
    let target = root.join("sentinel");
    let mut ok = true;
    for (candidate_id, candidate) in [
        target.clone(),
        root.join("../protected/sentinel"),
        PathBuf::from(format!("/proc/self/root{}", target.display())),
        PathBuf::from(format!("/proc/{parent}/root{}", target.display())),
        worker.join("protected-alias/sentinel"),
    ]
    .into_iter()
    .enumerate()
    {
        let name = path(&candidate);
        for (flags_id, flags) in [
            libc::O_RDONLY,
            libc::O_WRONLY,
            libc::O_WRONLY | libc::O_TRUNC,
        ]
        .into_iter()
        .enumerate()
        {
            let fd =
                unsafe { libc::syscall(libc::SYS_openat, libc::AT_FDCWD, name.as_ptr(), flags, 0) };
            if !denied(&format!("open-{candidate_id}-{flags_id}"), fd) {
                ok = false;
                if fd >= 0 {
                    unsafe { libc::close(fd as i32) };
                }
            }
        }
    }
    let canonical = path(&target);
    // These files belong to a valid seeded Git repository prepared by the
    // trusted parent. ENOENT is NEVER accepted as a main-protection proof.
    for (id, file) in [
        root.join(".git/refs/heads/main"),
        root.join(".git/config"),
        root.join(".git/HEAD"),
    ]
    .into_iter()
    .enumerate()
    {
        let name = path(&file);
        for (mode, flags) in [
            libc::O_RDONLY,
            libc::O_WRONLY,
            libc::O_WRONLY | libc::O_TRUNC,
        ]
        .into_iter()
        .enumerate()
        {
            let fd =
                unsafe { libc::syscall(libc::SYS_openat, libc::AT_FDCWD, name.as_ptr(), flags, 0) };
            if !denied(&format!("canonical-git-{id}-{mode}"), fd) {
                ok = false;
                if fd >= 0 {
                    unsafe { libc::close(fd as i32) };
                }
            }
        }
    }
    let escaped = path(&worker.join("stolen"));
    let attacks: &[&dyn Fn() -> libc::c_long] = &[
        &|| unsafe { libc::syscall(SYS_TRUNCATE, canonical.as_ptr(), 0) },
        &|| unsafe {
            libc::syscall(
                libc::SYS_renameat,
                libc::AT_FDCWD,
                canonical.as_ptr(),
                libc::AT_FDCWD,
                escaped.as_ptr(),
            )
        },
        &|| unsafe {
            libc::syscall(
                libc::SYS_linkat,
                libc::AT_FDCWD,
                canonical.as_ptr(),
                libc::AT_FDCWD,
                escaped.as_ptr(),
                0,
            )
        },
        &|| unsafe {
            libc::syscall(
                libc::SYS_fchmodat,
                libc::AT_FDCWD,
                canonical.as_ptr(),
                0o777,
            )
        },
        &|| unsafe {
            libc::syscall(
                libc::SYS_fchownat,
                libc::AT_FDCWD,
                canonical.as_ptr(),
                libc::geteuid(),
                libc::getegid(),
                0,
            )
        },
        &|| unsafe {
            libc::syscall(
                libc::SYS_utimensat,
                libc::AT_FDCWD,
                canonical.as_ptr(),
                std::ptr::null::<libc::timespec>(),
                0,
            )
        },
        &|| unsafe {
            libc::syscall(
                libc::SYS_setxattr,
                canonical.as_ptr(),
                c"user.boundary".as_ptr(),
                c"bad".as_ptr(),
                3,
                0,
            )
        },
        &|| unsafe { libc::syscall(libc::SYS_kill, parent, 0) },
        &|| unsafe { libc::syscall(libc::SYS_socket, libc::AF_UNIX, libc::SOCK_STREAM, 0) },
        &|| unsafe { libc::syscall(libc::SYS_io_uring_setup, 1, std::ptr::null::<u8>()) },
    ];
    for (id, attack) in [
        "truncate",
        "rename",
        "link",
        "chmod",
        "chown",
        "utime",
        "xattr",
        "signal",
        "unix-socket",
        "io-uring",
    ]
    .into_iter()
    .zip(attacks)
    {
        // Disposable child per attack: see `attempt` (Android 16 SIGSYS).
        if !attack_denied(id, attempt(*attack)) {
            ok = false;
        }
    }
    // Parent is an isolated proof process outside the Landlock domain. A
    // successful seize ends with the disposable child, which detaches it.
    if !attack_denied(
        "ptrace",
        attempt(&|| unsafe { libc::ptrace(0x4206 as _, parent, 0, 0) } as libc::c_long),
    ) {
        ok = false;
    }
    let mut byte = 0_u8;
    let local = libc::iovec {
        iov_base: (&mut byte as *mut u8).cast(),
        iov_len: 1,
    };
    let remote = libc::iovec {
        iov_base: std::ptr::null_mut(),
        iov_len: 1,
    };
    if !attack_denied(
        "process-vm-read",
        attempt(&|| unsafe {
            libc::syscall(libc::SYS_process_vm_readv, parent, &local, 1, &remote, 1, 0)
        }),
    ) {
        ok = false;
    }
    for (id, entry) in ["environ", "mem", "fd/0"].into_iter().enumerate() {
        let name = path(&PathBuf::from(format!("/proc/{parent}/{entry}")));
        let fd = unsafe {
            libc::syscall(
                libc::SYS_openat,
                libc::AT_FDCWD,
                name.as_ptr(),
                libc::O_RDONLY,
                0,
            )
        };
        if !denied(&format!("parent-proc-{id}"), fd) {
            ok = false;
            if fd >= 0 {
                unsafe { libc::close(fd as i32) };
            }
        }
    }
    // A namespace handle identifies a public kernel object; it is not a
    // credential or a data file. Acquiring it must NEVER grant authority to
    // enter the parent's namespace. The seccomp policy denies every setns.
    let namespace = path(&PathBuf::from(format!("/proc/{parent}/ns/mnt")));
    let fd = unsafe {
        libc::syscall(
            libc::SYS_openat,
            libc::AT_FDCWD,
            namespace.as_ptr(),
            libc::O_RDONLY | libc::O_CLOEXEC,
            0,
        )
    };
    if fd >= 0 {
        if !attack_denied(
            "namespace-setns",
            attempt(&|| unsafe { libc::syscall(libc::SYS_setns, fd as i32, 0) }),
        ) {
            ok = false;
        }
        unsafe { libc::close(fd as i32) };
    } else if !denied("namespace-open", fd) {
        ok = false;
    }
    if !attack_denied(
        "namespace-unshare",
        attempt(&|| unsafe { libc::syscall(libc::SYS_unshare, libc::CLONE_NEWNS) }),
    ) {
        ok = false;
    }
    // Ordinary worker writes must still work. A blanket deny policy is not proof.
    if let Err(error) = std::fs::write(worker.join("positive-control"), b"worker") {
        println!(
            "boundary-control:worker-write:errno:{}",
            error.raw_os_error().unwrap_or(0)
        );
        ok = false;
    }
    // Real worker ref/file renames remain permitted; protected-source REFER
    // denial must not be confused with blanket mutation failure.
    let rename = std::fs::rename(
        worker.join("positive-control"),
        worker.join("renamed-control"),
    )
    .and_then(|_| {
        std::fs::rename(
            worker.join("renamed-control"),
            worker.join("positive-control"),
        )
    });
    if let Err(error) = rename {
        println!(
            "boundary-control:worker-rename:errno:{}",
            error.raw_os_error().unwrap_or(0)
        );
        ok = false;
    }
    ok
}

fn main() {
    let args: Vec<_> = std::env::args_os().skip(1).collect();
    if args
        .first()
        .is_some_and(|arg| arg == "--prepare-git-fixture")
    {
        if args.len() != 2 {
            std::process::exit(64);
        }
        match prepare_git_fixture(&PathBuf::from(&args[1])) {
            Ok(main) => {
                println!("prepared-main:{main}");
                return;
            }
            Err(code) => {
                println!("boundary-control:{code}");
                std::process::exit(1);
            }
        }
    }
    if args.len() < 3 || args.len() > 4 {
        std::process::exit(64);
    }
    let root = PathBuf::from(&args[0]);
    let worker = PathBuf::from(&args[1]);
    let parent: i32 = args[2].to_str().and_then(|s| s.parse().ok()).unwrap_or(0);
    if parent <= 1 || !root.is_absolute() || !worker.is_absolute() {
        std::process::exit(64);
    }
    let mut ok = check(&root, &worker, parent);
    // exec and descendants must inherit exactly the same restrictions.
    if args.len() == 3 {
        use std::os::unix::process::ExitStatusExt;
        let status = std::env::current_exe().ok().and_then(|exe| {
            std::process::Command::new(exe)
                .args(&args)
                .arg("leaf")
                .status()
                .ok()
        });
        let descendant = status.is_some_and(|s| s.success());
        if !descendant {
            // Static shape only: how the confined descendant ended.
            match status {
                None => println!("boundary-control:descendant:spawn"),
                Some(s) => match s.signal() {
                    Some(signal) => println!("boundary-control:descendant:signal:{signal}"),
                    None => println!(
                        "boundary-control:descendant:exit:{}",
                        s.code().unwrap_or(-1)
                    ),
                },
            }
            println!("boundary-control:descendant:failed");
        }
        ok &= descendant;
    }
    println!(
        "{}",
        if ok {
            "native-boundary-probes-passed"
        } else {
            "native-boundary-probes-failed"
        }
    );
    if !ok {
        std::process::exit(1);
    }
}

#[cfg(test)]
mod isolated_attack_tests {
    use super::*;

    #[test]
    fn seccomp_sigsys_in_the_disposable_child_is_a_denial_not_a_probe_crash() {
        // Android's app policy traps unlisted syscalls: the kernel raises
        // SIGSYS before the call runs. Simulated with the same signal.
        let outcome = attempt(&|| unsafe { libc::raise(libc::SIGSYS) } as libc::c_long);
        assert_eq!(outcome, Attempt::Signaled(libc::SIGSYS));
        assert!(attack_denied("io-uring", outcome));
    }

    #[test]
    fn only_permission_denial_or_sigsys_pass() {
        let eperm = attempt(&|| unsafe {
            *libc_errno() = libc::EPERM;
            -1
        });
        assert_eq!(eperm, Attempt::Returned(-1, libc::EPERM));
        assert!(attack_denied("chmod", eperm));
        // A successful attack, an unrelated errno, another signal or a lost
        // child are never accepted as proof.
        assert!(!attack_denied("chmod", attempt(&|| 0)));
        assert!(!attack_denied("chmod", Attempt::Returned(-1, libc::ENOSYS)));
        let other = attempt(&|| unsafe { libc::raise(libc::SIGUSR1) } as libc::c_long);
        assert_eq!(other, Attempt::Signaled(libc::SIGUSR1));
        assert!(!attack_denied("chmod", other));
        assert!(!attack_denied("chmod", Attempt::Lost));
        // EXDEV stays accepted only for link/rename (Landlock REFER).
        assert!(attack_denied("link", Attempt::Returned(-1, libc::EXDEV)));
        assert!(!attack_denied(
            "truncate",
            Attempt::Returned(-1, libc::EXDEV)
        ));
    }

    #[cfg(target_os = "android")]
    unsafe fn libc_errno() -> *mut i32 {
        libc::__errno()
    }
    #[cfg(not(target_os = "android"))]
    unsafe fn libc_errno() -> *mut i32 {
        libc::__errno_location()
    }
}
