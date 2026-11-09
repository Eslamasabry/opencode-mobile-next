import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Kyant0/AndroidLiquidGlass "backdrop" 2.0.1 (Apache-2.0), vendored with one change: see README.md. Android only (the
// kit draws liquid glass on Android 13+; desktop keeps its stand-in). Served in place of io.github.kyant0:backdrop by
// the dependency substitution in settings.gradle.kts, so :ui:kit still declares the library it always used.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

val cmp = libs.versions.cmp.asProvider().get()

kotlin {
    jvmToolchain(17)
    (this as ExtensionAware).extensions.getByType<KotlinMultiplatformAndroidLibraryTarget>().apply {
        namespace = "com.kyant.backdrop"
        compileSdk { version = release(37) { minorApiLevel = 1 } }
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    sourceSets.commonMain.dependencies {
        implementation("org.jetbrains.compose.foundation:foundation:$cmp")
        implementation("org.jetbrains.compose.ui:ui:$cmp")
        implementation("org.jetbrains.compose.ui:ui-graphics:$cmp")
        implementation("io.github.kyant0:shapes:1.2.1")
        implementation("org.jetbrains:annotations:26.1.0")
    }
}
