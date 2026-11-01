package io.github.eslamasabry.oc.migration.readers.migration

/**
 * Credential changes across rollback transitions (audit 04+05 finding 7).
 *
 * The problem: Flutter reads `FlutterSecureStorage.xml` directly. If native changed or cleared a password,
 * the old FSS entry is still there, so going back to Flutter (R1) would silently re-activate the old
 * credential. Coming back to native, enumerating existing FSS entries cannot see a deletion made in Flutter.
 *
 * The fix modelled here:
 * - native keeps a per-name revision counter and deletion tombstones ([NativeCredentialState]);
 * - at each transition native → Flutter, a [TransitionBaseline] records, per FSS name, a fingerprint of the
 *   FSS **ciphertext** (never of the secret) and the native revision at that moment;
 * - before Flutter bootstraps, the R1 guard deletes the FSS entries listed by [staleLegacyEntries]: names
 *   native changed or deleted since the FSS copy was written, so Flutter shows "Enter password" instead of
 *   using an outdated credential;
 * - on return, [onReturn] compares the current FSS entries with the baseline of THIS transition: changed or
 *   added in Flutter → overwrite native; deleted in Flutter → delete native; unchanged → keep native.
 */
public object CredentialReconciler {

    /** Native-side bookkeeping, persisted by `:core:data` next to `oc_secrets_v1.xml` (names and counters only). */
    public data class NativeCredentialState(
        /** name → revision, incremented on every native write. */
        val revisions: Map<String, Long>,
        /** names native deleted (sign-out, server removed), with the revision of the deletion. */
        val tombstones: Map<String, Long>,
        /** name → native revision that is known to equal the FSS copy (set by M2, and by [onReturn]). */
        val fssSyncedAt: Map<String, Long>,
    )

    /** Written when native hands over to the Flutter rollback build. */
    public data class TransitionBaseline(
        val transitionId: Long,
        /** FSS name → SHA-256 of its stored ciphertext string at the transition. */
        val fssFingerprints: Map<String, String>,
    )

    public sealed interface ReturnAction {
        public val name: String
        public data class OverwriteNative(override val name: String) : ReturnAction
        public data class DeleteNative(override val name: String) : ReturnAction
        public data class KeepNative(override val name: String) : ReturnAction
    }

    /**
     * FSS names the R1 guard must delete before Flutter reads them: native wrote a newer value (revision past
     * the synced one) or deleted the credential, so the FSS copy is stale.
     */
    public fun staleLegacyEntries(native: NativeCredentialState, fssNames: Set<String>): Set<String> =
        fssNames.filterTo(sortedSetOf()) { name ->
            val synced = native.fssSyncedAt[name]
            val rev = native.revisions[name]
            val deletedAt = native.tombstones[name]
            when {
                deletedAt != null && (synced == null || deletedAt > synced) -> true
                rev != null && (synced == null || rev > synced) -> true
                else -> false
            }
        }

    /** Fingerprints for the baseline: computed over the ciphertext as stored, never over a decrypted value. */
    public fun fingerprint(storedCiphertext: String): String = Fs.sha256Hex(storedCiphertext.toByteArray(Charsets.UTF_8))

    /** What to do on return to native, per name, against the baseline of the transition being closed. */
    public fun onReturn(baseline: TransitionBaseline, currentFss: Map<String, String>): List<ReturnAction> {
        val names = (baseline.fssFingerprints.keys + currentFss.keys).toSortedSet()
        return names.map { name ->
            val before = baseline.fssFingerprints[name]
            val now = currentFss[name]?.let(::fingerprint)
            when {
                before != null && now == null -> ReturnAction.DeleteNative(name)
                before == null && now != null -> ReturnAction.OverwriteNative(name)
                before != now -> ReturnAction.OverwriteNative(name)
                else -> ReturnAction.KeepNative(name)
            }
        }
    }
}
