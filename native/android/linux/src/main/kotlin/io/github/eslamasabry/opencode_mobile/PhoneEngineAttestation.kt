package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** AndroidKeystore signs native-observed controls; receipts name the actual tier. */
internal class PhoneEngineAttestation(private val context: Context) {
    data class Receipt(val file: File, val publicKeyFile: File, val generation: String,
        val keySha256: String, val policySha256: String)

    fun issue(profile: String, root: File, generation: String, policy: List<String>,
        controls: Map<String, Any?>): Receipt {
        val tier = controls["tier"] as? String
            ?: throw PhoneEngineNative.Failure("boundary_proof_failed")
        val required = when (tier) {
            "landlock" -> listOf("nativeAttacksDenied")
            "proot" -> listOf("canonicalPathsDenied", "daemonProcDenied", "fdHygiene", "parentInspectionDenied")
            else -> throw PhoneEngineNative.Failure("boundary_proof_failed")
        } + listOf("prootGitCompatible", "fixtureUnchanged", "complete")
        if (required.any { controls[it] != true } || controls["nativeAttacksDenied"] !is Boolean)
            throw PhoneEngineNative.Failure("boundary_proof_failed")
        // Preserve observed controls rather than converting a weaker proof into
        // a claim that all native attacks were denied.
        val signedControls = JSONObject()
        for (name in listOf("nativeAttacksDenied", "prootGitCompatible", "fixtureUnchanged", "complete",
            "canonicalPathsDenied", "daemonProcDenied", "fdHygiene", "parentInspectionDenied")) {
            val value = controls[name]
            if (value != null && value !is Boolean) throw PhoneEngineNative.Failure("boundary_proof_failed")
            if (value is Boolean) signedControls.put(name, value)
        }
        PhoneEngineNative.verifyBundle(context, "libaiteam_engine.so", "libaiteam_sandbox.so",
            "libaiteam_boundary_probe.so")
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256).build())
            }.generateKeyPair()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw PhoneEngineNative.Failure("boundary_signer_unavailable")
        val publicKey = entry.certificate.publicKey.encoded
        val policyDigest = digest(policy.joinToString("\u0000").toByteArray(Charsets.UTF_8))
        val bootId = File("/proc/sys/kernel/random/boot_id").readText().trim()
        java.util.UUID.fromString(bootId) // Unsupported boot metadata is fail closed.
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val payload = JSONObject().put("schemaVersion", 2).put("tier", tier).put("profileId", profile)
            .put("parentPid", Process.myPid()).put("generation", generation)
            .put("bootId", bootId).put("kernelRelease", Os.uname().release)
            .put("policySha256", policyDigest)
            .put("engineSha256", hash(File(nativeDir, "libaiteam_engine.so")))
            .put("sandboxSha256", hash(File(nativeDir, "libaiteam_sandbox.so")))
            .put("probeSha256", hash(File(nativeDir, "libaiteam_boundary_probe.so")))
            .put("issuedAtElapsedMs", SystemClock.elapsedRealtime())
            .put("protectedLaunchesRequired", true)
            .put("controls", signedControls)
            .toString().toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(entry.privateKey)
            update(payload)
        }.sign()
        val receipt = File(root, "boundary-receipt.json")
        val pub = File(root, "boundary-public-key.der")
        write(pub, publicKey)
        write(File(root, "boundary-receipt.json.sig"), signature)
        write(receipt, payload)
        return Receipt(receipt, pub, generation, digest(publicKey), policyDigest)
    }

    companion object {
        private const val KEY_ALIAS = "oc.phoneEngineBoundary.v1"
        fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        fun hash(file: File): String {
            val fd = Os.open(file.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            val hash = MessageDigest.getInstance("SHA-256")
            java.io.FileInputStream(fd).use { input ->
                val buffer = ByteArray(65536)
                while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
            }
            return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        fun write(file: File, bytes: ByteArray) {
            val temporary = File(file.parentFile, ".${file.name}.new")
            try { Os.remove(temporary.absolutePath) } catch (e: android.system.ErrnoException) {
                if (e.errno != OsConstants.ENOENT) throw e
            }
            val fd = Os.open(temporary.absolutePath, OsConstants.O_WRONLY or OsConstants.O_CREAT or
                OsConstants.O_EXCL or OsConstants.O_NOFOLLOW, 384)
            FileOutputStream(fd).use { it.write(bytes); it.flush(); it.fd.sync() }
            Os.rename(temporary.absolutePath, file.absolutePath)
            val directory = Os.open(file.parentFile!!.absolutePath, OsConstants.O_RDONLY or
                OsConstants.O_NOFOLLOW or oCloexec, 0)
            try { Os.fsync(directory) } finally { Os.close(directory) }
        }
    }
}

/** O_CLOEXEC is an API 27 constant in OsConstants; on Linux it is 0x80000. */
internal val oCloexec: Int =
    if (android.os.Build.VERSION.SDK_INT >= 27) OsConstants.O_CLOEXEC else 0x80000
