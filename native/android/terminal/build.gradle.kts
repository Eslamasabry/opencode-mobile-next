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
    implementation(project(":engine:project-terminal")) // LINUX-2: the local terminal session engine
    // AGENT-2: the app's patched emulator; the stock view must not pull the unpatched one back in.
    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.3") // TerminalSession stripped at resolve time (TermuxSessionFork)
    implementation("com.github.termux.termux-app:terminal-view:v0.118.3") {
        exclude(group = "com.github.termux.termux-app", module = "terminal-emulator")
    }
}
