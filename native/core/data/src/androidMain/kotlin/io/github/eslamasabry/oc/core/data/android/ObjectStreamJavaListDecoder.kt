package io.github.eslamasabry.oc.core.data.android

import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass

/**
 * `ListEncoder.decode` of `shared_preferences_android` before 2.4.3: Java serialisation restricted to
 * `StringListObjectInputStream`'s allowlist. Copied from `flutter-migration-readers` (P5). Never writes this form.
 */
internal object ObjectStreamJavaListDecoder : JavaListDecoder {
    override fun decode(serialized: ByteArray): List<String>? =
        try {
            AllowListObjectInputStream(ByteArrayInputStream(serialized)).use { ois ->
                val obj = ois.readObject() as? List<*>
                if (obj == null || !obj.all { it is String }) null else obj.filterIsInstance<String>()
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            null // any failure is "unreadable" (BadJavaList); the value is never echoed
        }

    private class AllowListObjectInputStream(input: InputStream) : ObjectInputStream(input) {
        override fun resolveClass(desc: ObjectStreamClass?): Class<*>? {
            val name = desc?.name
            if (name != null && name !in FlutterPrefsCodec.JAVA_LIST_ALLOWED_CLASSES) throw ClassNotFoundException("not allowed")
            return super.resolveClass(desc)
        }
    }
}
