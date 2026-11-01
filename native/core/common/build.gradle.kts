plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.androidMain.dependencies { implementation(libs.kotlinx.coroutines.android) }
    sourceSets.getByName("desktopMain").dependencies { implementation(libs.kotlinx.coroutines.swing) }
}
