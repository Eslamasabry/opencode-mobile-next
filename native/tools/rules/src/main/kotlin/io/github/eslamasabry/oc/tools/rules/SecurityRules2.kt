package io.github.eslamasabry.oc.tools.rules

import io.gitlab.arturbosch.detekt.api.Config
import java.io.File
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/** A property or val/var constructor parameter of a class: name, declared type text, start offset. */
private data class Prop(val name: String?, val type: String?, val offset: Int)

private fun KtClass.props(): List<Prop> =
    (getProperties().map { Prop(it.name, it.typeReference?.text, it.textRange.startOffset) } +
        primaryConstructorParameters.filter { it.hasValOrVar() }.map { Prop(it.name, it.typeReference?.text, it.textRange.startOffset) })
        .distinctBy { it.name }

private fun lineIn(text: String, offset: Int): Int = text.substring(0, offset.coerceIn(0, text.length)).count { it == '\n' } + 1

private fun Source.androidSide() = isSecProduction && (layer == "android" || layer == "androidApp")

/** K-SEC-4 (replaces KSecRulesTest K-SEC-4): secret-named String properties of data and Serializable classes are Secret; reveal() only where listed. */
internal class OcSecSecretTypeAndReveal(config: Config) :
    OcSecRule(
        config, "OcSecSecretTypeAndReveal", "Secret-named text properties are Secret, and Secret.reveal() is called only in listed files.",
        "hold it as a Secret (core/security) so it cannot reach a log or saved state; rename it only if it is not a credential.",
    ) {
    private val secretName = Regex("""(?i).*(password|passwd|secret|token|apikey|api_key|privatekey|bearer|credential).*""")
    private val exemptSuffixes = listOf("Name", "Id", "Count", "Rejected", "Reentry")
    private val stringLike = setOf("String", "CharSequence", "CharArray", "ByteArray")
    private val reveal = Regex("""\.reveal\s*\(\s*\)""")

    override fun check(src: Source) {
        if (!src.isSecProduction) return
        val names = secList(src, "secret-name-allowlist.txt").toSet()
        val dtoPackages = secList(src, "secret-dto-allowlist.txt")
        val pkg = src.packageName()
        src.psi.collectDescendantsOfType<KtClass>().filter { !it.isLocal() && !it.isInterface() }.forEach { c ->
            val serial = c.annotationEntries.any { it.shortName?.asString() == "Serializable" }
            if (!(c.isData() || serial) || dtoPackages.any { pkg.startsWith(it) }) return@forEach
            c.props().forEach { (name, typeText, offset) ->
                val type = typeText?.replace(" ", "")?.removeSuffix("?")?.substringAfterLast('.')
                if (name != null && type != null && type in stringLike && secretName.matches(name) &&
                    exemptSuffixes.none { name.endsWith(it) } && name !in names
                ) {
                    violation(src, src.lineOf(offset), "${c.name}.$name: $typeText must be Secret")
                }
            }
        }
        if (src.name !in secList(src, "secret-reveal-allowlist.txt")) {
            hits(src, reveal) { "Secret.reveal() outside secret-reveal-allowlist.txt" }
        }
    }
}

/** K-SEC-5 (replaces K-SEC-5): no Secret in route keys, saved state, work data, intents or bundles. */
internal class OcSecNoSecretInPersistedState(config: Config) :
    OcSecRule(
        config, "OcSecNoSecretInPersistedState", "No Secret in route keys, saved state, work data, intents or bundles.",
        "saved state, work data and intents are written to disk; keep the secret in SecretStore and pass its id.",
    ) {
    private val secretDecl = Regex("""\b(?:val|var)?\s*(\w+)\s*:\s*Secret\??(?![\w.])""")
    private val persistCalls =
        Regex("""\b(rememberSaveable|rememberTextFieldState|workDataOf|putExtra|putString|putCharSequence|putParcelable|putSerializable|putByteArray|putCharArray|putStringArray|putAll)\s*(<[^>(]*>)?\s*[({]""")
    private val savedStateSet = Regex("""\b(savedStateHandle|SavedStateHandle\b[^\n]*)\s*\[[^\]]*]\s*=\s*([^\n]+)|\bsavedStateHandle\.set\s*\(""")
    private val secretWord = Regex("""\bSecret\b""")

    private fun braceBody(code: String, open: Int): String {
        var depth = 0
        for (i in open until code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return code.substring(open + 1, i) }
            }
        }
        return code.substring(open + 1)
    }

    override fun check(src: Source) {
        if (!src.isSecProduction) return
        // route keys: a class implementing NavKey or AppRoute holds no Secret
        src.psi.collectDescendantsOfType<KtClass>().filter { !it.isLocal() }.forEach { c ->
            val routeLike = c.superTypeListEntries.any { it.typeReference?.text?.substringBefore('<') in setOf("NavKey", "AppRoute") }
            if (!routeLike) return@forEach
            c.props().forEach { (name, typeText, offset) ->
                if (typeText?.let { secretWord.containsMatchIn(it) } == true) {
                    violation(src, src.lineOf(offset), "route ${c.name}.$name holds a Secret")
                }
            }
        }
        val code = src.code
        val names = secretDecl.findAll(code).map { it.groupValues[1] }.filter { it.isNotEmpty() }.toSet()
        fun mentionsSecret(s: String) = secretWord.containsMatchIn(s) || names.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(s) }
        persistCalls.findAll(code).forEach { m ->
            val open = m.range.last
            val body = if (code[open] == '(') KotlinText.balancedArgs(code, open) else braceBody(code, open)
            if (mentionsSecret(body) || m.groupValues[2].contains("Secret")) {
                violation(src, src.lineOf(m.range.first), "Secret into ${m.groupValues[1]}")
            }
        }
        savedStateSet.findAll(code).forEach { m ->
            val rest = if (m.value.endsWith("(")) KotlinText.balancedArgs(code, m.range.last) else m.groupValues[2]
            if (mentionsSecret(rest)) violation(src, src.lineOf(m.range.first), "Secret into SavedStateHandle")
        }
    }
}

/** K-SEC-6 (replaces K-SEC-6): clipboard writes go through the kit boundary with an explicit mode. */
internal class OcSecClipboardBoundary(config: Config) :
    OcSecRule(
        config, "OcSecClipboardBoundary", "Clipboard writes use the kit boundary and an explicit KitClipboardMode.",
        "copy through rememberKitClipboard and pass KitClipboardMode.Sensitive or KitClipboardMode.Public.",
    ) {
    private val boundaries =
        setOf(
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitClipboard.kt",
            "ui/kit/src/androidMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitClipboard.android.kt",
        )
    private val rawClip = Regex("""\bClipData\s*\.\s*newPlainText\b|\bClipEntry\s*\(|\.\s*(setPrimaryClip|setClipEntry|setContents)\s*\(|\.\s*primaryClip\s*=""")
    private val textWrite = Regex("""\b(\w+)\s*\.\s*setText\s*\(""")
    private val kitWrite = Regex("""\bKitAndroidClipboard\s*\.\s*write\s*\(""")
    private val mode = Regex("""\bKitClipboardMode\.(Sensitive|Public)\b""")

    override fun check(src: Source) {
        if (!src.isSecProduction || src.rel in boundaries) return
        val code = KotlinText.executable(src.text)
        fun at(offset: Int) = lineIn(code, offset)
        rawClip.findAll(code).forEach { violation(src, at(it.range.first), "raw clipboard write outside KitClipboard") }
        val typed = Regex("""\b(\w+)\s*:\s*(?:androidx\.compose\.ui\.platform\.)?Clipboard(?:Manager)?\b""").findAll(code).map { it.groupValues[1] }.toSet()
        val locals = Regex("""\b(?:val|var)\s+(\w+)\s*=\s*(?:[\w.]+\.)?LocalClipboard(?:Manager)?\.current\b""").findAll(code).map { it.groupValues[1] }.toSet()
        textWrite.findAll(code).forEach { m ->
            val receiver = m.groupValues[1]
            val args = KotlinText.balancedArgs(code, m.range.last)
            val looksLikeClipboard = receiver.startsWith("clipboard", true) || receiver in typed || receiver in locals ||
                (receiver == "current" && "LocalClipboard" in code) || "AnnotatedString" in args
            val kitReceiver = Regex("""\bval\s+${Regex.escape(receiver)}\s*=\s*(?:[\w.]+\.)?rememberKitClipboard\s*\(""").containsMatchIn(code)
            val forwarding =
                when (src.rel) {
                    "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitCopyButton.kt",
                    "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitIconButton.kt",
                    -> "clipboardMode: KitClipboardMode" in code && Regex("""\bclipboardMode\b""").containsMatchIn(args)
                    "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/header/KitChatHeaderParts.kt" ->
                        "mode: KitClipboardMode" in code && Regex("""\bmode\b""").containsMatchIn(args)
                    else -> false
                }
            when {
                !looksLikeClipboard -> Unit
                !kitReceiver -> violation(src, at(m.range.first), "raw text clipboard write outside KitClipboard")
                !forwarding && !mode.containsMatchIn(args) -> violation(src, at(m.range.first), "clipboard write needs an explicit KitClipboardMode")
            }
        }
        kitWrite.findAll(code).forEach { m ->
            val args = KotlinText.balancedArgs(code, m.range.last)
            val adapter = src.module == "ui/kit" && src.name == "AndroidKitPlatform.kt" && Regex("""\bmode\b""").containsMatchIn(args)
            if (!(adapter || mode.containsMatchIn(args))) violation(src, at(m.range.first), "clipboard write needs an explicit KitClipboardMode")
        }
    }
}

/** K-SEC-7 (replaces K-SEC-7): every PendingIntent is FLAG_IMMUTABLE except the reviewed files. */
internal class OcSecPendingIntentImmutable(config: Config) :
    OcSecRule(
        config, "OcSecPendingIntentImmutable", "Every PendingIntent is immutable except reviewed platform contracts.",
        "add PendingIntent.FLAG_IMMUTABLE; a mutable one can be rewritten by another app. Reviewed exceptions go in mutable-pendingintent-allowlist.txt.",
    ) {
    private val pendingIntent = Regex("""\bPendingIntent\s*\.\s*(getActivity|getActivities|getBroadcast|getService|getForegroundService)\s*\(""")

    private fun installerReceiverPrivate(src: Source): Boolean {
        val manifest = File(src.root ?: return false, "android/tiles/src/main/AndroidManifest.xml").takeIf { it.isFile }?.readText() ?: return false
        val name = "io.github.eslamasabry.opencode_mobile.AppUpdateStatusReceiver"
        val starts = Regex("""<receiver\b[^>]*android:name\s*=\s*"${Regex.escape(name)}"""").findAll(manifest).toList()
        if (starts.size != 1) return false
        val from = starts.single().range.first
        val tagEnd = manifest.indexOf('>', from)
        val selfClosing = manifest[tagEnd - 1] == '/'
        val tag = manifest.substring(from, tagEnd)
        val inner = if (selfClosing) "" else manifest.substring(tagEnd, manifest.indexOf("</receiver>", tagEnd).takeIf { it > 0 } ?: manifest.length)
        return Regex("""android:exported\s*=\s*"false"""").containsMatchIn(tag) && "<intent-filter" !in inner
    }

    override fun check(src: Source) {
        if (!src.androidSide() || src.name in secList(src, "mutable-pendingintent-allowlist.txt")) return
        val code = src.code
        pendingIntent.findAll(code).forEach { m ->
            val args = KotlinText.balancedArgs(code, m.range.last)
            val installer = src.rel == "android/tiles/src/main/kotlin/io/github/eslamasabry/oc/android/tiles/updates/AndroidUpdates.kt"
            val installerCall = installer && m.groupValues[1] == "getBroadcast" &&
                args.replace(Regex("""\s"""), "").trimEnd(',') == "app,sessionId,intent,PendingIntent.FLAG_UPDATE_CURRENTorPendingIntent.FLAG_MUTABLE" &&
                Regex("""val\s+intent\s*=\s*Intent\(\)\.setClassName\(app,\s*STATUS_RECEIVER\)""").containsMatchIn(code) &&
                "session.commit(pending.intentSender)" in code &&
                "params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)" in code &&
                "STATUS_RECEIVER: String = \"io.github.eslamasabry.opencode_mobile.AppUpdateStatusReceiver\"" in code &&
                installerReceiverPrivate(src)
            if ("FLAG_IMMUTABLE" !in args && !installerCall) violation(src, src.lineOf(m.range.first), "PendingIntent.${m.groupValues[1]} without FLAG_IMMUTABLE")
        }
    }
}

/** K-SEC-8 (replaces K-SEC-8): no parseUri, getIntent or relaunching an intent taken from extras. */
internal class OcSecNoIntentFromExtras(config: Config) :
    OcSecRule(
        config, "OcSecNoIntentFromExtras", "No parseUri, getIntent or relaunched intents taken from extras.",
        "an intent built from outside data lets another app start any screen; start only known screens with fixed targets.",
    ) {
    private val parse = Regex("""\bIntent\s*\.\s*parseUri\s*\(|\bparseUri\s*\(|\bIntent\s*\.\s*getIntent\s*\(""")
    private val fromExtras = Regex("""\b(?:val|var)\s+(\w+)\b[^\n=]*=\s*[^\n]*\bgetParcelable(Extra)?\s*(<[^>]*>)?\s*\(""")
    private val start = Regex("""\bstartActivit(y|ies)\s*\(""")

    override fun check(src: Source) {
        if (!src.androidSide() || src.name == "PackageInstallerUpdater.kt") return
        val code = src.code
        val tainted = fromExtras.findAll(code).map { it.groupValues[1] }.toSet()
        start.findAll(code).forEach { m ->
            val args = KotlinText.balancedArgs(code, m.range.last)
            val bad = Regex("""\bgetParcelable(Extra)?\s*(<[^>]*>)?\s*\(""").containsMatchIn(args) ||
                tainted.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(args) }
            if (bad) violation(src, src.lineOf(m.range.first), "startActivity with an intent taken from extras")
        }
        hits(src, parse)
    }
}

/** K-SEC-10 (replaces K-SEC-10): every URL literal names a KnownHosts host or loopback. */
internal class OcSecUrlsOnKnownHosts(config: Config) :
    OcSecRule(
        config, "OcSecUrlsOnKnownHosts", "Every URL literal names a host listed in KnownHosts, or loopback.",
        "add the host to KnownHosts (core/security) with the security owner, so the app's network reach stays one reviewed list.",
    ) {
    private val urlLiteral = Regex(""""(https?|wss?)://([^"/:?#\s$\\]*)""")
    private val loopback = setOf("127.0.0.1", "localhost", "[::1]", "")
    private val hostLiteral = Regex(""""(\*\.)?([a-z0-9-]+\.)+[a-z]{2,}"""")

    private fun knownHosts(root: File): Set<String> {
        val file = root.resolve("core/security").walkTopDown().onEnter { it.name != "build" }.firstOrNull { it.isFile && it.name == "KnownHosts.kt" }
            ?: return emptySet()
        return hostLiteral.findAll(KotlinText.stripComments(file.readText())).map { it.value.trim('"') }.toSet()
    }

    override fun check(src: Source) {
        val root = src.root ?: return
        if (!src.isSecProduction || src.name.endsWith("Fixtures.kt") || "/src/qa/" in "/${src.rel}") return
        val allowed = hostsCache.getOrPut(root.path) { knownHosts(root) } + secList(src, "knownhosts-pending.txt")
        val code = src.code
        urlLiteral.findAll(code).forEach { m ->
            val host = m.groupValues[2]
            val templated = code.getOrNull(m.range.last + 1) == '$' && host.isEmpty()
            val ok = templated || host.lowercase() in loopback ||
                host.lowercase() in allowed || allowed.any { it.startsWith("*.") && host.lowercase().endsWith(it.substring(1)) }
            if (!ok) violation(src, src.lineOf(m.range.first), "$host is not in KnownHosts")
        }
    }

    private companion object {
        val hostsCache = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()
    }
}
