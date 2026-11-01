// :android:linux — slice 1 (S1-LINUX, plan 11 §0.2 S11-1, §4.1): the built-in Linux Kotlin copied unchanged from the
// Flutter app (android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile). Every copy is a twin in
// native/TWINS.md. The only native-authored sources are the package-level shims under src/shims/kotlin, which stand in
// for Flutter-app classes that are not part of this module (see docs/native/units/S1-LINUX.md §3).
plugins { id("oc.android.library") }

android {
    // The copies call BuildConfig.BUILTIN_RUNTIME_QA; the shim forwards to this field (QA hooks stay off by default,
    // as in the Flutter app; -PocBuiltinRuntimeQa=true turns them on).
    buildFeatures { buildConfig = true }
    defaultConfig {
        val qa = providers.gradleProperty("ocBuiltinRuntimeQa").orNull == "true"
        buildConfigField("boolean", "BUILTIN_RUNTIME_QA", qa.toString())
    }
    sourceSets {
        getByName("main") { kotlin.directories += "src/shims/kotlin" }
    }
}

// TODO(catalog): commons-compress and org.json are the Flutter app's exact pins (android/app/build.gradle.kts) and are
// requested for the version catalog (S1-LINUX report, "Catalog requests"). The catalog entry wins as soon as it exists;
// the literal coordinates are only the fallback until then.
fun catalogOr(alias: String, fallback: String): Any =
    extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary(alias).map<Any> { it.get() }.orElse(fallback)

dependencies {
    implementation(catalogOr("commons-compress", "org.apache.commons:commons-compress:1.27.1"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit4)
    testImplementation(catalogOr("json", "org.json:json:20180813"))
}
