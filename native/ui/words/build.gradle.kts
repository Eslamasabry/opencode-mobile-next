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
        val rows = listOf("en", "ar", "es", "ja", "pt", "ru", "zh").map { tag ->
            val translated = if (tag == "en") source else names(resources.dir("values-$tag").asFile).intersect(source)
            "        \"$tag\" to ${if (source.isEmpty()) 0 else translated.size * 100 / source.size}"
        }
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText("package io.github.eslamasabry.oc.ui.words\n\n/** Share of native message identities with a translation in the shipped catalogue. */\npublic object LanguageCoverage {\n    public val percent: Map<String, Int> = mapOf(\n${rows.joinToString(",\n")}\n    )\n}\n")
        }
    }
}
kotlin.sourceSets.named("commonMain") { kotlin.srcDir(coverageDirectory) }
tasks.matching { it.name.startsWith("compile") }.configureEach { dependsOn(generateLanguageCoverage) }
