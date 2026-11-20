package io.github.eslamasabry.oc.quality

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoParameterDeclaration
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider
import java.io.File

internal object NullableInjectionRule {
    private val injected = setOf("Inject", "AssistedInject", "ContributesBinding", "ContributesIntoMap", "ContributesIntoSet", "ViewModelKey")
    private val markers = injected + setOf("Provides", "Binds")
    private val mainSets = listOf("commonMain", "androidMain", "desktopMain", "main")
    private val typeName = Regex("""[\w`]+(?:\.[\w`]+)*""")
    private val implicitPackages = listOf("kotlin", "kotlin.annotation", "kotlin.collections", "kotlin.comparisons",
        "kotlin.io", "kotlin.ranges", "kotlin.sequences", "kotlin.text", "kotlin.jvm", "java.lang")

    private data class Type(val file: KoFileDeclaration, val text: String)
    private data class Input(val owner: String, val name: String, val type: Type) {
        val key = "$owner#$name"

        fun message(): String {
            val plain = type.text.removeSuffix("?")
            return "$key\n$owner.$name: `$plain?` is never filled by Metro from a non-null binding, so it is always null on " +
                "the phone. Use a non-null type with a no-op default, or bind `$plain?` explicitly (@Provides fun x(x: $plain): $plain? = x)."
        }
    }

    fun problems(modules: List<File>, baseline: Set<String>): List<String> {
        val dirs = modules.flatMap { module -> mainSets.map { File(module, "src/$it") } }.filter(File::isDirectory)
        require(dirs.isNotEmpty()) { "No production source sets found for the nullable injection guard" }
        val files = Konsist.scopeFromExternalDirectories(dirs.map(File::getAbsolutePath)).files
            .filter { it.path.endsWith(".kt") && !it.path.endsWith("Previews.kt") }
        val inputs = mutableListOf<Input>()
        val bindings = mutableListOf<Type>()

        for (file in files) {
            val source = file.text
            if (markers.none { it in source }) continue
            val aliases = file.imports.mapNotNull { imp -> imp.alias?.name?.let { it to imp.name.substringAfterLast('.') } }.toMap()
            fun annotation(name: String) = aliases[name] ?: name

            fun collect(owner: String, parameters: List<KoParameterDeclaration>) {
                for (p in parameters) {
                    if (p.type.text.endsWith('?') && p.annotations.none { annotation(it.name) == "Assisted" }) {
                        inputs += Input(owner, p.name, Type(file, p.type.text))
                    }
                }
            }
            for (cls in file.classes(includeNested = true, includeLocal = false)) {
                if (cls.annotations.any { annotation(it.name) in injected }) {
                    collect(checkNotNull(cls.fullyQualifiedName), cls.primaryConstructor?.parameters.orEmpty())
                }
            }
            for (fn in file.functions(includeNested = true, includeLocal = false)) {
                val provides = fn.annotations.any { annotation(it.name) == "Provides" }
                if (provides) {
                    val owner = (fn.containingDeclaration as? KoFullyQualifiedNameProvider)?.fullyQualifiedName
                    collect(checkNotNull(owner ?: fn.fullyQualifiedName), fn.parameters)
                }
                if (provides || fn.annotations.any { annotation(it.name) == "Binds" }) {
                    fn.returnType?.text?.takeIf { it.endsWith('?') }?.let { bindings += Type(file, it) }
                }
            }
            for (property in file.properties(includeNested = true)) {
                if (property.annotations.any { annotation(it.name) == "Binds" }) {
                    property.type?.text?.takeIf { it.endsWith('?') }?.let { bindings += Type(file, it) }
                }
            }
        }

        // A star import contributes only types in that package. Include imported library types
        // and fully qualified references as well as declarations in the scanned source sets.
        val names = buildSet {
            for (file in files) {
                file.classesAndInterfacesAndObjects(includeNested = true, includeLocal = false).mapNotNullTo(this) { it.fullyQualifiedName }
                file.typeAliases.mapNotNullTo(this) { it.fullyQualifiedName }
            }
            for (type in bindings + inputs.map { it.type }) {
                type.file.imports.filterNot { it.isWildcard }.mapTo(this) { it.name }
                typeName.findAll(type.text).map { it.value }.filter { '.' in it }.forEach(::add)
            }
        }
        val bound = bindings.mapTo(mutableSetOf()) { it.qualified(names) }
        val unfilled = inputs.filter { it.type.qualified(names) !in bound }
        val stale = baseline - unfilled.map { it.key }.toSet()
        return unfilled.filter { it.key !in baseline }.map { it.message() }.sorted() +
            stale.sorted().map { "Remove stale nullable injection baseline row: $it" }
    }

    private fun Type.qualified(names: Set<String>): String {
        val imports = file.imports.filterNot { it.isWildcard }.associate {
            (it.alias?.name ?: it.name.substringAfterLast('.')) to it.name
        }
        val stars = file.imports.filter { it.isWildcard }.map { it.name.removeSuffix(".*") }
        val pkg = file.packagee?.name.orEmpty()
        return typeName.replace(text) { match ->
            val name = match.value
            val head = name.substringBefore('.')
            val resolved = imports[head] ?: listOf(pkg).plus(stars).plus(implicitPackages)
                .map { "$it.$head" }.firstOrNull { it in names } ?: head
            resolved + name.removePrefix(head)
        }.filterNot(Char::isWhitespace)
    }
}
