use serde::Deserialize;
use std::{
    fs,
    path::{Path, PathBuf},
};

#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Config {
    pub schema_version: u32,
    pub profile_id: String,
    pub private_root: PathBuf,
    pub worker_root: PathBuf,
    pub guest_worker_root: String,
    pub port: u16,
    pub auth_token_file: PathBuf,
    #[serde(alias = "oc1CredentialFile")]
    pub server_credential_file: PathBuf,
    #[serde(alias = "oc1BaseUrl")]
    pub server_base_url: String,
    #[serde(default)]
    pub source_roots: Vec<SourceRoot>,
    pub boundary: Boundary,
}
#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct SourceRoot {
    pub host_root: PathBuf,
    pub guest_root: String,
}
#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Boundary {
    pub verified: bool,
    pub reason: String,
    #[serde(default)]
    pub restart_required: bool,
    #[serde(default)]
    pub receipt_file: Option<PathBuf>,
    #[serde(default)]
    pub public_key_file: Option<PathBuf>,
    #[serde(default)]
    pub generation: Option<String>,
}
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ServerCredentials {
    pub username: String,
    pub password: String,
}

impl Config {
    pub fn read(path: &Path) -> Result<Self, &'static str> {
        let raw = fs::read(path).map_err(|_| "configUnavailable")?;
        if raw.len() > 65536 {
            return Err("configInvalid");
        }
        let mut value: Self = serde_json::from_slice(&raw).map_err(|_| "configInvalid")?;
        value.normalize_paths()?;
        value.validate()?;
        Ok(value)
    }
    // Android supplies a trusted app-data path whose system-managed ancestors
    // may be symlinks (/data/user/0). Resolve ONLY that trusted ancestor, then
    // reject every symlink below it before normalizing configured paths.
    fn normalize_paths(&mut self) -> Result<(), &'static str> {
        let raw_base = self
            .private_root
            .parent()
            .ok_or("configInvalid")?
            .to_path_buf();
        let base = fs::canonicalize(&raw_base).map_err(|_| "configInvalid")?;
        let normalize = |path: &Path| normalize_below(&raw_base, &base, path);
        self.private_root = normalize(&self.private_root)?;
        self.worker_root = normalize(&self.worker_root)?;
        self.auth_token_file = normalize(&self.auth_token_file)?;
        self.server_credential_file = normalize(&self.server_credential_file)?;
        for root in &mut self.source_roots {
            root.host_root = normalize(&root.host_root)?;
        }
        if let Some(path) = &mut self.boundary.receipt_file {
            *path = normalize(path)?;
        }
        if let Some(path) = &mut self.boundary.public_key_file {
            *path = normalize(path)?;
        }
        Ok(())
    }
    pub fn validate(&self) -> Result<(), &'static str> {
        if self.schema_version != 1
            || !valid_id(&self.profile_id)
            || !self.private_root.is_absolute()
            || !self.worker_root.is_absolute()
            || !self.guest_worker_root.starts_with("/root/aiteam/work/")
        {
            return Err("configInvalid");
        }
        let private = fs::canonicalize(&self.private_root).map_err(|_| "configInvalid")?;
        if self.worker_root.components().any(|part| {
            matches!(
                part,
                std::path::Component::ParentDir | std::path::Component::CurDir
            )
        }) {
            return Err("configInvalid");
        }
        // A first start has no worker directory yet. RepositoryAuthority checks
        // every ancestor and creates it via nofollow directory descriptors.
        let worker = match fs::canonicalize(&self.worker_root) {
            Ok(path) => path,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => self.worker_root.clone(),
            Err(_) => return Err("configInvalid"),
        };
        if private.starts_with(&worker) || worker.starts_with(&private) {
            return Err("boundaryInvalid");
        }
        for file in [&self.auth_token_file, &self.server_credential_file] {
            let canonical = fs::canonicalize(file).map_err(|_| {
                if file == &self.server_credential_file {
                    "server_auth_unavailable"
                } else {
                    "credentialsUnavailable"
                }
            })?;
            if !canonical.starts_with(&private) {
                return Err("boundaryInvalid");
            }
            let metadata = fs::metadata(&canonical).map_err(|_| "credentialsUnavailable")?;
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                if metadata.permissions().mode() & 0o077 != 0 {
                    return Err("credentialsUnsafe");
                }
            }
            if !metadata.is_file() || metadata.len() > 16384 {
                return Err("credentialsInvalid");
            }
        }
        if self.server_base_url != "http://127.0.0.1:4097" {
            return Err("serverNotLoopback");
        }
        // Only native-parent-pinned signed receipts confer protected authority.
        // A mutable boolean alone must never switch protected execution on.
        if self.boundary.verified {
            return Err("boundaryProofRequired");
        }
        Ok(())
    }
    pub fn source_path(&self, guest: &str) -> Result<PathBuf, &'static str> {
        if guest.contains('\0') || guest.split('/').any(|s| s == ".." || s == ".") {
            return Err("repoPathInvalid");
        }
        for root in &self.source_roots {
            let prefix = format!("{}/", root.guest_root.trim_end_matches('/'));
            if let Some(relative) = guest.strip_prefix(&prefix) {
                let host_root = fs::canonicalize(&root.host_root).map_err(|_| "repoPathInvalid")?;
                let result =
                    fs::canonicalize(host_root.join(relative)).map_err(|_| "repoPathInvalid")?;
                if result.starts_with(&host_root)
                    && !result.starts_with(&self.private_root)
                    && !result.starts_with(&self.worker_root)
                {
                    return Ok(result);
                }
            }
        }
        Err("repoPathInvalid")
    }
}
fn normalize_below(raw_base: &Path, base: &Path, path: &Path) -> Result<PathBuf, &'static str> {
    let relative = path.strip_prefix(raw_base).map_err(|_| "boundaryInvalid")?;
    let mut normalized = base.to_path_buf();
    for part in relative.components() {
        let std::path::Component::Normal(name) = part else {
            return Err("configInvalid");
        };
        normalized.push(name);
        match fs::symlink_metadata(&normalized) {
            Ok(metadata) if metadata.file_type().is_symlink() => return Err("symlinkRefused"),
            Ok(_) => {}
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(_) => return Err("configInvalid"),
        }
    }
    Ok(normalized)
}
pub fn valid_id(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 128
        && id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-')
}

#[cfg(test)]
mod tests {
    #[test]
    fn persisted_server_credentials_survive_config_field_rename() {
        for (credential_key, base_key) in [
            ("serverCredentialFile", "serverBaseUrl"),
            ("oc1CredentialFile", "oc1BaseUrl"),
        ] {
            let mut value = serde_json::json!({
                "schemaVersion":1, "profileId":"profile", "privateRoot":"/private",
                "workerRoot":"/workers", "guestWorkerRoot":"/root/workers", "port":0,
                "authTokenFile":"/private/token", "sourceRoots":[],
                "boundary":{"verified":false,"reason":"fixture"}
            });
            value[credential_key] = serde_json::json!("/private/credentials");
            value[base_key] = serde_json::json!("http://127.0.0.1:4097");
            let config: super::Config = serde_json::from_value(value).unwrap();
            assert_eq!(
                config.server_credential_file,
                std::path::PathBuf::from("/private/credentials")
            );
            assert_eq!(config.server_base_url, "http://127.0.0.1:4097");
        }
    }

    use super::*;
    use std::os::unix::fs::PermissionsExt;

    #[test]
    fn config_read_normalizes_alias_and_rejects_linked_credentials() {
        use std::os::unix::fs::symlink;
        let scratch = Path::new("/home/eslam/Storage/tmp/aiteam-phone-engine-tests");
        fs::create_dir_all(scratch).unwrap();
        let temp = tempfile::tempdir_in(scratch).unwrap();
        let real = temp.path().join("data/data/app/files");
        let private = real.join("private");
        fs::create_dir_all(&private).unwrap();
        let alias = temp.path().join("data/user0");
        symlink(temp.path().join("data/data"), &alias).unwrap();
        let raw_base = alias.join("app/files");
        for name in ["auth.token", "credentials.json"] {
            let file = private.join(name);
            fs::write(&file, "fixture").unwrap();
            fs::set_permissions(file, fs::Permissions::from_mode(0o600)).unwrap();
        }
        let value = serde_json::json!({"schemaVersion":1,"profileId":"profile",
            "privateRoot":raw_base.join("private"),"workerRoot":raw_base.join("rootfs/work/new"),
            "guestWorkerRoot":"/root/aiteam/work/profile","port":0,
            "authTokenFile":raw_base.join("private/auth.token"),
            "serverCredentialFile":raw_base.join("private/credentials.json"),
            "serverBaseUrl":"http://127.0.0.1:4097","sourceRoots":[{"hostRoot":raw_base,"guestRoot":"/root/projects"}],
            "boundary":{"verified":false,"reason":"boundary_unverified"}});
        let path = private.join("native-config.json");
        fs::write(&path, value.to_string()).unwrap();
        let config = Config::read(&path).unwrap();
        assert_eq!(config.private_root, fs::canonicalize(&private).unwrap());
        assert_eq!(
            config.worker_root,
            fs::canonicalize(&real).unwrap().join("rootfs/work/new")
        );
        assert_eq!(
            config.source_path("/root/projects/private").unwrap_err(),
            "repoPathInvalid"
        );
        fs::remove_file(private.join("auth.token")).unwrap();
        symlink(private.join("credentials.json"), private.join("auth.token")).unwrap();
        assert!(matches!(Config::read(&path), Err("symlinkRefused")));
    }

    #[test]
    fn trusted_android_style_ancestor_is_resolved_but_descendant_symlink_is_refused() {
        use std::os::unix::fs::symlink;
        let scratch = Path::new("/home/eslam/Storage/tmp/aiteam-phone-engine-tests");
        fs::create_dir_all(scratch).unwrap();
        let temp = tempfile::tempdir_in(scratch).unwrap();
        let real = temp.path().join("data/data/app/files");
        fs::create_dir_all(&real).unwrap();
        let alias = temp.path().join("data/user0");
        symlink(temp.path().join("data/data"), &alias).unwrap();
        let raw_base = alias.join("app/files");
        let canonical_base = fs::canonicalize(&raw_base).unwrap();
        let resolved = normalize_below(
            &raw_base,
            &canonical_base,
            &raw_base.join("rootfs/work/new"),
        )
        .unwrap();
        assert_eq!(resolved, real.join("rootfs/work/new"));
        fs::create_dir_all(real.join("rootfs")).unwrap();
        symlink(temp.path(), real.join("rootfs/work")).unwrap();
        assert_eq!(
            normalize_below(
                &raw_base,
                &canonical_base,
                &raw_base.join("rootfs/work/new")
            )
            .unwrap_err(),
            "symlinkRefused"
        );
        symlink(temp.path(), real.join("private")).unwrap();
        assert_eq!(
            normalize_below(
                &raw_base,
                &canonical_base,
                &raw_base.join("private/auth.token")
            )
            .unwrap_err(),
            "symlinkRefused"
        );
        assert!(normalize_below(&raw_base, &canonical_base, &raw_base.join("../escape")).is_err());
    }
    #[test]
    fn first_start_allows_missing_worker_root_but_never_a_boolean_proof() {
        let scratch = Path::new("/home/eslam/Storage/tmp/aiteam-phone-engine-tests");
        fs::create_dir_all(scratch).unwrap();
        let temp = tempfile::tempdir_in(scratch).unwrap();
        let private = temp.path().join("private");
        fs::create_dir(&private).unwrap();
        let token = private.join("auth.token");
        let credentials = private.join("credentials.json");
        for file in [&token, &credentials] {
            fs::write(file, "fixture").unwrap();
            fs::set_permissions(file, fs::Permissions::from_mode(0o600)).unwrap();
        }
        let mut config = Config {
            schema_version: 1,
            profile_id: "profile".into(),
            private_root: private,
            worker_root: temp.path().join("rootfs/work/profile"),
            guest_worker_root: "/root/aiteam/work/profile".into(),
            port: 4098,
            auth_token_file: token,
            server_credential_file: credentials,
            server_base_url: "http://127.0.0.1:4097".into(),
            source_roots: vec![],
            boundary: Boundary {
                verified: false,
                reason: "boundary_unverified".into(),
                restart_required: false,
                receipt_file: None,
                public_key_file: None,
                generation: None,
            },
        };
        assert!(config.validate().is_ok());
        config.boundary.verified = true;
        assert_eq!(config.validate().unwrap_err(), "boundaryProofRequired");
        config.boundary.verified = false;
        config.worker_root = config.private_root.join("work");
        assert_eq!(config.validate().unwrap_err(), "boundaryInvalid");
    }
}
