plugins { id("oc.kmp.library") }

// :engine:voice (unit VOICE, 2026-11-01; plan 10 §5–§8 and MV-1b/MV-5/MV-6/MV-7 logic). Voice typing, model packs,
// first-mic setup and read aloud as pure Kotlin over platform seams; the Android side is :android:voice.
// Not in ModuleRules.engineOrder yet, so it may depend on :core:* only (and no engine may depend on it); see
// docs/native/units/VOICE.md "requests".
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        implementation(project(":core:data")) // KeyValueVoicePrefs over the one KeyValueStore
        implementation(libs.kotlinx.io.core) // SystemModelFiles: files/voice_models in place (plan 10 §7.2)
    }
}
