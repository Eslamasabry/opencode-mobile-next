# E01-S08: keep rules for the shipped release (R8 shrink + optimize + obfuscate, resources shrunk).
# ReleaseShapeTest reads this file: every class the component freeze names (quality/config/component-freeze) must have
# a -keep line here, and the JNI and serialization rules must stay. Add a rule here, never `-dontobfuscate`.

# ---- kotlinx.serialization (the library's own rules, repeated so a jar without embedded rules cannot break it) -------
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$$serializer { *** INSTANCE; }

# ---- Metro DI ----------------------------------------------------------------------------------------------------------
# Metro builds the graph at compile time (no reflection). The graph classes keep their names so crash reports and the
# start-up traces stay readable; their members may still be optimized.
-keepnames @dev.zacsweers.metro.DependencyGraph class *
-keepnames class **$$MetroGraph

# ---- WorkManager -------------------------------------------------------------------------------------------------------
# WorkManager stores the worker class NAME with every enqueued request, and that work outlives an update (the Flutter
# app's ScheduledPromptWorker and WidgetStatusWorker requests are still queued on the phone). Never rename a worker.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class io.github.eslamasabry.opencode_mobile.ScheduledPromptWorker { <init>(...); }
-keep class io.github.eslamasabry.opencode_mobile.WidgetStatusWorker { <init>(...); }

# ---- Home-screen widgets and Quick Settings tiles ----------------------------------------------------------------------
# Placed widgets and added tiles name their provider/service class; the code also compares provider class names.
-keep class * extends android.appwidget.AppWidgetProvider { <init>(); }
-keep class * extends android.service.quicksettings.TileService { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.SessionsWidgetProvider { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.SessionsWideWidgetProvider { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.SessionsListWidgetProvider { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.SessionsStripWidgetProvider { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.UsageWidgetProvider { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.UsageWideWidgetProvider { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.StatusWidgetProvider { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.AttentionTileService { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.PauseTileService { <init>(); }

# ---- Frozen components (E00-S13 freeze list): activities, receivers, services ------------------------------------------
# AAPT already keeps manifest classes; these lines make the freeze explicit so a manifest edit cannot drop one quietly.
-keep class io.github.eslamasabry.opencode_mobile.MainActivity { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.ManageSpaceActivity { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.WidgetConfigureActivity { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.WidgetRouteActivity { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.WidgetOverlayActivity { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.ScheduledPromptReceiver { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.WidgetActionReceiver { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.CodingActionReceiver { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.StatusActionReceiver { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.LivePauseReceiver { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.BuiltinRestoreReceiver { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.BuiltinIdleReceiver { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.TermuxResultService { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.BuiltinServerService { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.SetupService { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.BackgroundConnectionService { <init>(); }
-keep class io.github.eslamasabry.opencode_mobile.LocalPdfService { <init>(); }

# ---- JNI: classes native code finds by name ----------------------------------------------------------------------------
# sherpa-onnx's JNI reads its Kotlin config classes field by field (FindClass/GetFieldID); its AAR ships no rules.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# sherpa.keep: the Kokoro streaming callback is looked up by method name (KokoroSink.kt).
-keepclassmembers class io.github.eslamasabry.oc.android.voice.speech.KokoroSink {
    java.lang.Integer invoke(float[]);
    public int invoke(float[]);
    public java.lang.Object invoke(java.lang.Object);
}
-keep class io.github.eslamasabry.oc.android.voice.speech.KokoroSink
# Termux terminal-emulator PTY bridge (native methods).
-keep class com.termux.terminal.JNI { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

# ---- Crash reports -----------------------------------------------------------------------------------------------------
# Line numbers for deobfuscated stack traces (the mapping file is kept with each release build).
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
