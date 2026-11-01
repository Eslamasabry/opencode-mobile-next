package io.github.eslamasabry.oc.android.migration

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.github.eslamasabry.oc.migration.readers.secure.FlutterSecret
import io.github.eslamasabry.oc.migration.readers.secure.FlutterSecureStorageReader
import io.github.eslamasabry.oc.migration.readers.secure.FssFormat
import io.github.eslamasabry.oc.migration.readers.secure.FssKeyUnwrapper
import io.github.eslamasabry.oc.migration.readers.secure.FssStores
import io.github.eslamasabry.oc.migration.readers.secure.KeystoreAliasMissingException
import io.github.eslamasabry.oc.migration.readers.secure.fssOaepSpec
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import javax.crypto.Cipher
import javax.crypto.SecretKey

/** P5-readers §4 step 2: the plugin's own transformation, provider and OAEP parameters. Never creates the alias. */
internal class AndroidKeystoreUnwrapper(private val packageName: String) : FssKeyUnwrapper {
    override fun unwrap(wrappedKey: ByteArray): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val priv = ks.getKey(FssFormat.keystoreAlias(packageName), null) as? PrivateKey ?: throw KeystoreAliasMissingException()
        return Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStoreBCWorkaround").run {
            init(Cipher.UNWRAP_MODE, priv, fssOaepSpec())
            unwrap(wrappedKey, "AES", Cipher.SECRET_KEY) as SecretKey
        }
    }
}

/**
 * Slice-0 P5 proof runner (switch flavour only). On a background thread, in this order:
 * 1. S0: byte copy of the Flutter prefs and secure-storage files into `files/migration/flutter-v57/`, timed, with SHA-256.
 * 2. Adopt: open `FlutterSharedPreferences` in place through the platform and count keys by kind (no write).
 * 3. M2: decrypt every flutter_secure_storage v11 entry with the package's AndroidKeyStore alias. Plaintexts are never
 *    logged or written: only names, lengths, a SHA-256 and whether the value equals the seeded fixture value.
 * The result goes to `files/native/p5-proof.json` and logcat tag `P5Proof`.
 */
internal object P5Proof {
    private const val TAG = "P5Proof"

    fun run(context: Context) {
        val out = linkedMapOf<String, Any?>()
        val dataDir = File(context.applicationInfo.dataDir)
        val prefsDir = File(dataDir, "shared_prefs")
        out["processStartToRunMs"] = SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()
        out["onMainThread"] = Looper.myLooper() == Looper.getMainLooper()

        // 1. S0 byte backup (fence thread, never main)
        val t0 = SystemClock.elapsedRealtimeNanos()
        val backup = File(context.filesDir, "migration/flutter-v57").apply { mkdirs() }
        val copied = prefsDir.listFiles().orEmpty().filter { it.name.startsWith("Flutter") }.sortedBy { it.name }.map { f ->
            val dst = File(backup, f.name)
            f.copyTo(dst, overwrite = true)
            mapOf("name" to f.name, "bytes" to f.length(), "sha256" to sha256(dst.readBytes()))
        }
        out["s0Ms"] = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
        out["s0Files"] = copied

        // 2. Adopt FlutterSharedPreferences.xml in place (read only here)
        val t1 = SystemClock.elapsedRealtimeNanos()
        val all = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).all
        out["prefsLoadMs"] = (SystemClock.elapsedRealtimeNanos() - t1) / 1_000_000.0
        out["prefsKeys"] = all.size
        out["prefsFlutterPrefixed"] = all.keys.count { it.startsWith("flutter.") }
        out["prefsByKind"] = all.values.groupingBy { it?.javaClass?.simpleName ?: "null" }.eachCount()
        val profiles = all["flutter.oc.profiles"] as? String
        out["profilesJsonChars"] = profiles?.length
        out["sampleKeys"] = all.keys.filter { it.startsWith("flutter.oc.") }.sorted().take(40)

        // 3. M2: decrypt flutter_secure_storage v11 with the Keystore alias that survived `adb install -r`
        val t2 = SystemClock.elapsedRealtimeNanos()
        val read = FlutterSecureStorageReader(AndroidKeystoreUnwrapper(context.packageName)).read(FssStores.fromDirectory(prefsDir))
        out["m2Ms"] = (SystemClock.elapsedRealtimeNanos() - t2) / 1_000_000.0
        out["fssCensus"] = read.census.toString()
        out["secrets"] = read.secrets.map { s ->
            when (s) {
                is FlutterSecret.Plain -> {
                    val v = s.value.reveal()
                    mapOf("name" to s.name, "ok" to true, "length" to v.length, "sha256" to sha256(v.toByteArray()))
                }
                is FlutterSecret.Failed -> mapOf("name" to s.name, "ok" to false, "reason" to s.reason.name)
            }
        }
        out["keystoreAliasPresent"] = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .containsAlias(FssFormat.keystoreAlias(context.packageName))

        val json = toJson(out)
        File(context.filesDir, "native").apply { mkdirs() }.resolve("p5-proof.json").writeText(json)
        Log.i(TAG, json)
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun toJson(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        is Number, is Boolean -> v.toString()
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { (k, x) -> toJson(k.toString()) + ":" + toJson(x) }
        is Iterable<*> -> v.joinToString(",", "[", "]") { toJson(it) }
        else -> toJson(v.toString())
    }
}

/** Starts [P5Proof] on its own thread at process start. Returns at once: the main thread never waits for it. */
public class P5ProofProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context!!.applicationContext ?: context!!
        Thread({ runCatching { P5Proof.run(ctx) }.onFailure { Log.e("P5Proof", "failed: ${it.javaClass.name}") } }, "p5-fence").start()
        return true
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
