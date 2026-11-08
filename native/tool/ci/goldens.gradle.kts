// CI lanes for picture tests (tool/ci/LANES.md). Applied from the root build.gradle.kts. Nothing changes unless a
// property is passed, so local runs and the other agents' `tasks` runs behave as before.
//
//   -PskipGoldens=true   exclude every golden / screenshot test class (fast lane: no pictures, only behaviour)
//   -PonlyGoldens=true   run only the classes that compare against, or record, a desktop golden under
//                        src/desktopTest/goldens (the goldens lane, together with -Poc.recordGoldens=true)
//
// A class counts as a picture test when its source (a *Test source set) mentions the Flutter gate
// (FlutterGoldenGate.), the desktop golden switches (oc.goldens.*, compareGolden), the LooksCapture helper or
// looks-out/, or, in ui/ and feature/ modules, when the file is named *Golden* or *LooksCapture*. Classes are found
// by reading the sources, so adding a golden test needs no list edit. The scan runs at configuration time and only
// when one of the two properties is set.
val skipGoldens = providers.gradleProperty("skipGoldens").orNull == "true"
val onlyGoldens = providers.gradleProperty("onlyGoldens").orNull == "true"

if (skipGoldens || onlyGoldens) {
    // Pictures: anything that renders and compares (excluded from the fast lane).
    val pictureMarker = Regex(
        "FlutterGoldenGate\\.|oc\\.goldens\\.(dir|record)|compareGolden|looks-out|import .*\\.LooksCapture\\b|captureRoboImage",
    )
    // Recordable: writes into src/desktopTest/goldens when oc.goldens.record=true (the goldens lane).
    val recordMarker = Regex("oc\\.goldens\\.(dir|record)|compareGolden|E20Goldens")
    val pictureName = Regex("Goldens?(Test)?\\.kt$|LooksCapture(Test)?\\.kt$")
    val pkgRx = Regex("^package\\s+([\\w.]+)", RegexOption.MULTILINE)
    val classRx = Regex("^(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:internal |public |private |open |abstract )*class\\s+(\\w+)", RegexOption.MULTILINE)

    fun classesOf(dir: File, picture: Boolean): List<String> {
        val result = ArrayList<String>()
        val srcRoot = dir.resolve("src")
        if (!srcRoot.isDirectory) return result
        val inFeatureOrUi = dir.parentFile.name == "ui" || dir.parentFile.name == "feature"
        srcRoot.listFiles { f -> f.isDirectory && f.name.lowercase().endsWith("test") && f.name != "androidHostTest" }
            ?.forEach { set ->
                set.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                    val text = f.readText()
                    val marker = if (picture) pictureMarker else recordMarker
                    val byName = picture && inFeatureOrUi && pictureName.containsMatchIn(f.name)
                    if (!marker.containsMatchIn(text) && !byName) return@forEach
                    val pkg = pkgRx.find(text)?.groupValues?.get(1)?.plus(".") ?: ""
                    classRx.findAll(text).map { it.groupValues[1] }
                        .filter { it.endsWith("Test") || it.endsWith("Tests") }
                        .forEach { result += "$pkg$it" }
                }
            }
        return result.distinct()
    }

    allprojects {
        val projectDirectory = projectDir
        tasks.withType<Test>().configureEach {
            if (skipGoldens) {
                classesOf(projectDirectory, picture = true).forEach { filter.excludeTestsMatching(it) }
            }
            if (onlyGoldens) {
                val classes = classesOf(projectDirectory, picture = false)
                if (classes.isEmpty()) enabled = false
                else {
                    filter.isFailOnNoMatchingTests = false
                    classes.forEach { filter.includeTestsMatching(it) }
                }
            }
        }
    }
}
