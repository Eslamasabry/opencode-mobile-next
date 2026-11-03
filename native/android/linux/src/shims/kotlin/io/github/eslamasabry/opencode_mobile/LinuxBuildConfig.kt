// S1-LINUX shim (native-authored, not a twin). P12-FIX-LINUX-VOICE: renamed from BuildConfig (the app generates that FQCN); later: AppBuildInfo.qaHooks.builtinRuntime (plan 11 §4.1).
package io.github.eslamasabry.opencode_mobile

/** The copies read `BuildConfig.BUILTIN_RUNTIME_QA` in the app package; this module's flag (-PocBuiltinRuntimeQa) answers. */
internal object LinuxBuildConfig {
    val BUILTIN_RUNTIME_QA: Boolean get() = io.github.eslamasabry.oc.android.linux.BuildConfig.BUILTIN_RUNTIME_QA
}
