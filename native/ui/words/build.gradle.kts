plugins { id("oc.kmp.compose") }

kotlin { sourceSets.commonMain.dependencies { implementation(project(":core:common")) } }

// Production metadata, generated from the exact XML catalogue compiled into this candidate.
val coverageDirectory = layout.buildDirectory.dir("generated/languageCoverage")
val generateLanguageCoverage by tasks.registering {
    val resources = layout.projectDirectory.dir("src/commonMain/composeResources")
    inputs.files(fileTree(resources) { include("values*/strings*.xml") })
    val output = coverageDirectory.map { it.file("io/github/eslamasabry/oc/ui/words/LanguageCoverage.kt") }
    outputs.file(output)
    doLast {
        fun names(folder: java.io.File): Set<String> = folder.listFiles().orEmpty()
            .filter { it.name.startsWith("strings") && it.extension == "xml" }
            .flatMap { file -> Regex("""<(?:string|plurals)\s+name="([^"]+)""").findAll(file.readText()).map { it.groupValues[1] }.toList() }.toSet()
        val source = names(resources.dir("values").asFile)
        // Every values-<tag> folder is a language; English is the source. The 95 % rule is mirrored in
        // androidApp/build.gradle.kts (locales_config.xml) and pinned by :tools:strings LanguageGateTest.
        val tags = listOf("en") + resources.asFile.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") && Regex("[a-z]{2,3}").matches(it.name.removePrefix("values-")) }
            .map { it.name.removePrefix("values-") }.sorted().let { l -> listOf("ar").filter { it in l } + (l - "ar") }
        val counts = tags.associateWith { tag ->
            if (tag == "en") source.size else names(resources.dir("values-$tag").asFile).intersect(source).size
        }
        val rows = tags.map { tag -> "        \"$tag\" to ${if (source.isEmpty()) 0 else counts.getValue(tag) * 100 / source.size}" }
        val offered = tags.filter { it == "en" || (source.isNotEmpty() && counts.getValue(it) * 100 >= 95 * source.size) }
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                "package io.github.eslamasabry.oc.ui.words\n\n" +
                    "/** Share of native message identities with a translation in the shipped catalogue. */\n" +
                    "public object LanguageCoverage {\n" +
                    "    public val percent: Map<String, Int> = mapOf(\n${rows.joinToString(",\n")}\n    )\n\n" +
                    "    /** A language is offered (picker, system language) only at 95 % of English; below that the app is English as a whole. */\n" +
                    "    public val offered: Set<String> = setOf(${offered.joinToString { "\"$it\"" }})\n\n" +
                    "    /** [tag]'s language when it is offered, else null (the caller falls back to English as a whole). */\n" +
                    "    public fun offeredOrNull(tag: String?): String? =\n" +
                    "        tag?.substringBefore('-')?.substringBefore('_')?.lowercase()?.takeIf { it in offered }\n" +
                    "}\n",
            )
        }
    }
}
kotlin.sourceSets.named("commonMain") { kotlin.srcDir(coverageDirectory) }
tasks.matching { it.name.startsWith("compile") }.configureEach { dependsOn(generateLanguageCoverage) }
