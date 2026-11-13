package oc.buildlogic

import java.io.File
import java.util.Properties

/**
 * Where the release key file is found (E01-S01), shared by `:androidApp`'s build and its test. The file names the
 * keystore and its passwords (storeFile, storePassword, keyAlias, keyPassword); values are never logged.
 *
 * Order, first match wins:
 * 1. `OC_KEY_PROPERTIES` (environment or Gradle property): any file of that shape, for example CI's temp file.
 * 2. `native/key.properties` (git-ignored): the place that outlives the Flutter folders. A relative storeFile resolves
 *    against `native/`, the folder of the file.
 * 3. `android/key.properties`, the old place the Flutter build used. A relative storeFile resolves against
 *    `android/app/`, exactly as before. Using it prints [Located.notice].
 *
 * When none exists the result names `native/key.properties`, so the "no key file" message points at the new place.
 * A file named by `OC_KEY_PROPERTIES` that IS the old file keeps the old resolution.
 */
public object ReleaseKey {
    public const val NEW_PLACE: String = "native/key.properties"
    public const val OLD_PLACE: String = "android/key.properties"

    public class Located(
        /** The key file that was chosen (it may not exist). */
        public val source: File,
        /** The keystore, resolved; null when the file has no storeFile. */
        public val storeFile: File?,
        public val storePassword: String?,
        public val keyAlias: String?,
        public val keyPassword: String?,
        /** One line to print when the old place was used; null otherwise. */
        public val notice: String?,
    )

    /** [named] is OC_KEY_PROPERTIES (blank counts as unset); [repo] is the repository root (the parent of native/). */
    public fun locate(named: String?, repo: File): Located {
        val old = File(repo, OLD_PLACE)
        val new = File(repo, NEW_PLACE)
        val source: File =
            when {
                !named.isNullOrBlank() -> File(named)
                new.isFile -> new
                old.isFile -> old
                else -> new
            }
        val oldPlace = source.canonicalFile == old.canonicalFile
        val base: File = if (oldPlace) File(repo, "android/app") else source.absoluteFile.parentFile
        val props = Properties()
        if (source.isFile) source.inputStream().use(props::load)
        val storePath: String = props.getProperty("storeFile").orEmpty().trim()
        val store: File? =
            when {
                storePath.isEmpty() -> null
                File(storePath).isAbsolute -> File(storePath)
                else -> File(base, storePath)
            }
        val notice =
            if (oldPlace && source.isFile && named.isNullOrBlank()) {
                "Release signing: using the old key file $OLD_PLACE; move it to $NEW_PLACE (a relative storeFile then " +
                    "resolves against native/), since android/ goes away with the Flutter app."
            } else {
                null
            }
        return Located(
            source,
            store,
            props.getProperty("storePassword"),
            props.getProperty("keyAlias"),
            props.getProperty("keyPassword"),
            notice,
        )
    }
}
