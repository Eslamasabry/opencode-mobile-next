package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Failure

/** Which platform the secret store runs on; only a Linux desktop can lack a keyring (B-SEC-134). */
public enum class SecretPlatform { Android, LinuxDesktop, OtherDesktop, Ios }

/**
 * Why a secret could not be kept, for the one sentence `:ui:words` shows (Flutter `SecureStorageUnavailable`):
 * a Linux desktop without a keyring gets its own sentence, every other platform the generic device sentence.
 * The engine holds no copy of either sentence and never the store's error text.
 */
public enum class SecureStorageProblem {
    /** Flutter `SecureStorageUnavailable.linuxMessage`. */
    LinuxKeyringMissing,

    /** Flutter `SecureStorageUnavailable.genericMessage`. */
    DeviceSecretStorageUnavailable,
    ;

    public companion object {
        /** Null when [failure] is not a secret-storage failure. */
        public fun of(failure: Failure, platform: SecretPlatform): SecureStorageProblem? =
            if (failure !is Failure.SecretUnavailable) {
                null
            } else if (platform == SecretPlatform.LinuxDesktop) {
                LinuxKeyringMissing
            } else {
                DeviceSecretStorageUnavailable
            }
    }
}
