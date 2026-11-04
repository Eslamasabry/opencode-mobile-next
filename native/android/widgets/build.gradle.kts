// :android:widgets (plan 11 §11, plan 15 S4-WIDGETS, unit WIDGETS): the RemoteViews home-screen
// widgets copied from the
// Flutter app (android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile), provider class
// names frozen. Every
// copy is a twin in native/TWINS.md; the only edits are the compile seams listed in README.md. The
// native-authored
// seams and the engine feed live in the module's own package
// (io.github.eslamasabry.oc.android.widgets).
plugins { id("oc.android.library") }

android {
    testOptions {
        // The render tests inflate the real widget layouts (Robolectric reads the merged
        // resources).
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = false
    }
}

// TODO(catalog): work-runtime and org.json are the Flutter app's pins
// (android/app/build.gradle.kts), requested for the
// version catalog in docs/native/units/WIDGETS.md. The catalog entry wins as soon as it exists.
fun catalogOr(alias: String, fallback: String): Any =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findLibrary(alias)
        .map<Any> { it.get() }
        .orElse(fallback)

dependencies {
    // The Status widget's "this phone's Linux" line is fed from the runtime engine's status (plan
    // 11 §4.4 E10).
    implementation(project(":engine:runtime"))
    implementation(project(":core:data"))
    implementation(project(":engine:profiles"))
    api(project(":engine:attention"))
    implementation(project(":core:domain"))
    implementation(project(":core:security"))
    implementation(project(":core:model"))
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.androidx.core.ktx)
    implementation(catalogOr("androidx-work-runtime", "androidx.work:work-runtime:2.12.0"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(catalogOr("json", "org.json:json:20180813"))
}

// widgets-MAJOR-6: an independent Kotlin compilation, deliberately without unit-test friend paths.
val ingressConsumerCompiler = configurations.create("ingressConsumerCompiler")

dependencies {
    ingressConsumerCompiler(
        "org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}"
    )
}

tasks.register<JavaExec>("testWidgetIngressConsumer") {
    group = "verification"
    description = "widgets-MAJOR-6 public ingress compiles without friend paths"
    dependsOn("compileDebugKotlin")
    classpath = ingressConsumerCompiler
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    maxHeapSize = "512m"
    inputs.dir("src/ingressConsumer/kotlin")
    val destination = layout.buildDirectory.dir("ingress-consumer")
    outputs.dir(destination)
    val consumerClasspath =
        files(
            configurations
                .getByName("debugCompileClasspath")
                .incoming
                .artifactView {
                    attributes {
                        attribute(
                            org.gradle.api.attributes.Attribute.of(
                                "artifactType",
                                String::class.java,
                            ),
                            "android-classes-jar",
                        )
                    }
                }
                .files,
            androidComponents.sdkComponents.bootClasspath,
            tasks.named("compileDebugKotlin").map { it.outputs.files },
        )
    inputs
        .files(consumerClasspath)
        .withPropertyName("consumerClasspath")
        .withNormalizer(org.gradle.api.tasks.ClasspathNormalizer::class.java)
    doFirst {
        args =
            listOf(
                "-no-stdlib",
                "-no-reflect",
                "-jvm-target",
                "17",
                "-classpath",
                consumerClasspath.asPath,
                "-d",
                destination.get().asFile.path,
                file("src/ingressConsumer/kotlin").path,
            )
    }
}
