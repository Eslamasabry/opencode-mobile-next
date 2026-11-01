package oc.buildlogic

internal object Namespaces {
    const val ROOT = "io.github.eslamasabry.oc"

    /** The app's namespace keeps the Flutter-era package so OS-visible FQCNs survive (ADR §5.2, §9.9). */
    const val APP = "io.github.eslamasabry.opencode_mobile"

    /** Slice 0 ships only the side-by-side preview id (ADR §10.1); the real id is not used until the switch. */
    const val PREVIEW_APPLICATION_ID = "$APP.native"

    /** ":ui:kit-chat" → "io.github.eslamasabry.oc.ui.kitchat". Hyphens drop out: a namespace is a Java package. */
    fun of(path: String): String =
        when (path) {
            ":androidApp" -> APP
            ":desktopApp" -> "$ROOT.desktop"
            else -> ROOT + "." + path.removePrefix(":").split(':').joinToString(".") { it.replace("-", "") }
        }
}
