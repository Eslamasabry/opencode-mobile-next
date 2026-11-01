# LiteRT-LM (the on-device assistant, LocalLlmBridge.kt) ships no consumer
# rules, and its native library (liblitertlm_jni.so) looks Java classes up by
# name through JNI: LiteRtLmJni and its callbacks, BenchmarkInfo, InputData,
# EmbeddingResponse and LiteRtLmJniException. Under the release shrinker those
# names would be renamed or removed and loading the model would fail only in
# release builds. Keep the library's package whole.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
