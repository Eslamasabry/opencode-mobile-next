plugins { id("oc.android.library") }

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:security"))
    implementation(project(":core:domain"))
    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.3-oc1")
    implementation("com.github.termux.termux-app:terminal-view:v0.118.3") {
        exclude(group = "com.github.termux.termux-app", module = "terminal-emulator")
    }
}
