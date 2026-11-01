// Every plugin a module uses goes on the root classpath once, with `apply false` (plan 03 §3.2).
// Slice 0 lists only the plugins its modules apply; ksp, room3, roborazzi, baselineprofile, kover and
// dependency-analysis join this block in the slice that first uses them.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.metro) apply false
}
