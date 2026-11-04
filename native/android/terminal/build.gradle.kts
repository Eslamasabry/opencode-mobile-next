plugins { id("oc.android.library") }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":engine:project-terminal"))
    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.3")
    implementation("com.github.termux.termux-app:terminal-view:v0.118.3")
}
