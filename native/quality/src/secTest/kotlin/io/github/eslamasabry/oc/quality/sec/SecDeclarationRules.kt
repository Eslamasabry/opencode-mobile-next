package io.github.eslamasabry.oc.quality.sec

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import java.io.File

/**
 * The declaration-level K-SEC rules (plan 16 §2.1), on Konsist: K-SEC-4 (secret-named String-like properties of data
 * and `@Serializable` classes), K-SEC-5 (a `Secret` inside a route key) and K-SEC-11 (analytics event properties).
 */
class SecDeclarationRules(private val root: File, files: List<SecFile>) {
    private val production = files.filter { it.isKotlin && it.isProduction }

    /** One Konsist scope over the production Kotlin files of the scanned tree (null when there are none). */
    private val scope: KoScope? =
        production.takeIf { it.isNotEmpty() }?.let { list ->
            // one directory per source set (src/<set>), so nested folders are never scanned twice
            val dirs = list.map { File(root, it.rel.substringBefore("/src/") + "/src/" + it.sourceSet).absolutePath }.distinct()
            Konsist.scopeFromExternalDirectories(dirs)
        }

    private val productionPaths = production.map { File(root, it.rel).absoluteFile.invariantSeparatorsPath }.toSet()

    private fun classes(): List<KoClassDeclaration> =
        scope?.classes(includeNested = true, includeLocal = false).orEmpty()
            .filter { File(it.path).absoluteFile.invariantSeparatorsPath in productionPaths }

    private fun rel(path: String) = File(path).absoluteFile.relativeTo(root.absoluteFile).invariantSeparatorsPath

    private fun line(location: String) = location.substringAfterLast(".kt:").substringBefore(':').toIntOrNull() ?: 0

    /** (name, declared type text or null, location, the class it belongs to) for every property and val/var parameter. */
    private data class Prop(val name: String, val type: String?, val path: String, val location: String)

    private fun KoClassDeclaration.props(): List<Prop> {
        val fromProperties = properties(includeNested = false).map { Prop(it.name, it.type?.text, it.path, it.location) }
        val fromCtor =
            primaryConstructor?.parameters.orEmpty().filter { it.isVal || it.isVar }
                .map { Prop(it.name, it.type.text, it.path, it.location) }
        return (fromProperties + fromCtor).distinctBy { it.name }
    }

    /** Non-vacuity: how many data/@Serializable classes and AnalyticsEvent implementors the scope saw. */
    fun seen(): Pair<Int, Int> =
        classes().count { it.hasDataModifier || it.annotations.any { a -> a.name == "Serializable" } } to
            classes().count { c -> c.parents(indirectParents = true).any { it.name.substringBefore('<') == "AnalyticsEvent" } }

    // ---- K-SEC-4
    private val secretName = Regex("""(?i).*(password|passwd|secret|token|apikey|api_key|privatekey|bearer|credential).*""")
    private val exemptSuffixes = listOf("Name", "Id", "Count", "Rejected", "Reentry")
    private val stringLike = setOf("String", "CharSequence", "CharArray", "ByteArray")

    fun kSec4(config: SecConfig): List<Violation> =
        classes()
            .filter { it.hasDataModifier || it.annotations.any { a -> a.name == "Serializable" } }
            .filterNot { c -> config.secretDtoPackages.any { p -> (c.packagee?.name ?: "").startsWith(p) } }
            .flatMap { c ->
                c.props().filter { p ->
                    val type = p.type?.replace(" ", "")?.removeSuffix("?")?.substringAfterLast('.')
                    type != null && type in stringLike && secretName.matches(p.name) &&
                        exemptSuffixes.none { p.name.endsWith(it) } && p.name !in config.secretNames
                }.map { p -> Violation("K-SEC-4", rel(p.path), line(p.location), "${c.name}.${p.name}: ${p.type} must be Secret") }
            }

    // ---- K-SEC-5 (route keys): no Secret in a class implementing NavKey or AppRoute
    fun kSec5Routes(): List<Violation> =
        classes()
            .filter { c -> c.parents(indirectParents = false).any { it.name.substringBefore('<') in setOf("NavKey", "AppRoute") } }
            .flatMap { c ->
                c.props().filter { p -> p.type?.let { Regex("""\bSecret\b""").containsMatchIn(it) } == true }
                    .map { p -> Violation("K-SEC-5", rel(p.path), line(p.location), "route ${c.name}.${p.name} holds a Secret") }
            }

    // ---- K-SEC-11: analytics event constructor properties are enums, Boolean or the three bucket types
    private val allowedEventTypes = setOf("Boolean", "CountBucket", "DurationBucket", "VersionCode")

    fun kSec11(): List<Violation> {
        val enums = scope?.classes(includeNested = true, includeLocal = false).orEmpty().filter { it.hasEnumModifier }.map { it.name }.toSet()
        return classes()
            .filter { c -> c.parents(indirectParents = true).any { it.name.substringBefore('<') == "AnalyticsEvent" } }
            .flatMap { c ->
                c.primaryConstructor?.parameters.orEmpty().mapNotNull { p ->
                    val type = p.type.text.replace(" ", "").substringAfterLast('.')
                    if (type in allowedEventTypes || type in enums) null
                    else Violation("K-SEC-11", rel(p.path), line(p.location), "${c.name}.${p.name}: ${p.type.text} is not an enum, Boolean or bucket")
                }
            }
    }
}
