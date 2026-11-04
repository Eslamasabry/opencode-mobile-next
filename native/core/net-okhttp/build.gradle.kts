plugins { id("oc.jvm.library") }

dependencies {
    api("com.squareup.okhttp3:okhttp:${libs.versions.okhttp.get()}")
}
