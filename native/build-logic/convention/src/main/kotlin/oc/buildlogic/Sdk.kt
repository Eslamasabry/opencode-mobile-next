package oc.buildlogic

import com.android.build.api.dsl.CompileSdkSpec

/** SDK levels (ADR §8). Audit BLOCKER 2: Compose 1.13 requires compileSdk 37 minor level 1, so it is not optional. */
internal object Sdk {
    const val COMPILE = 37
    const val COMPILE_MINOR = 1 // platforms;android-37.1 must be installed (sdkmanager "platforms;android-37.1")
    const val TARGET = 36 // targetSdk 37 is its own slice after the switch (ADR §8)
    const val MIN = 26

    /** The one way every convention sets compileSdk (application, library and AGP-KMP DSL share CompileSdkSpec). */
    fun CompileSdkSpec.ocCompileSdk() {
        version = release(COMPILE) { minorApiLevel = COMPILE_MINOR }
    }
}

/** JDK levels (ADR §8): bytecode and module toolchains on 17; Android host tests and Robolectric launch on 21 (audit #10). */
internal object Jdk {
    const val BUILD = 17
    const val TEST_LAUNCHER = 21
}
