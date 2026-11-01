package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.concurrent.Executors

/**
 * The on-device assistant (contract: docs/design/gemma-assistant-2026-10-31.md).
 * Both halves of `oc/local_llm` (methods) and `oc/local_llm/events` live here and in
 * lib/local_llm/local_llm_platform.dart. Inference runs on one worker thread; cancel and unload
 * never wait behind it.
 */
class LocalLlmBridge(private val context: Context, messenger: BinaryMessenger) : EventChannel.StreamHandler {
    private val methods = MethodChannel(messenger, "oc/local_llm")
    private val events = EventChannel(messenger, "oc/local_llm/events")
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "local-llm").apply { isDaemon = true } }
    private var sink: EventChannel.EventSink? = null

    @Volatile private var engine: Engine? = null
    @Volatile private var backendName: String? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var generationId: String? = null
    @Volatile private var loading = false
    @Volatile private var disposed = false

    init {
        events.setStreamHandler(this)
        methods.setMethodCallHandler { call, result ->
            when (call.method) {
                "status" -> result.success(status())
                "loadModel" -> loadModel(call.argument<String>("path"), call.argument<String>("backend"), result)
                "generate" -> generate(
                    call.argument<String>("id"),
                    call.argument<String>("prompt"),
                    call.argument<Int>("maxTokens"),
                    result,
                )
                "cancel" -> { cancel(); result.success(null) }
                "unload" -> { unload("requested"); result.success(null) }
                else -> result.notImplemented()
            }
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) { sink = events }
    override fun onCancel(arguments: Any?) { sink = null }

    private fun emit(event: Map<String, Any?>) {
        main.post { if (!disposed) sink?.success(event) }
    }

    private fun status(): Map<String, Any?> = mapOf(
        "state" to when {
            loading -> "loading"
            generationId != null -> "generating"
            engine != null -> "ready"
            else -> "unloaded"
        },
        "backend" to backendName,
        "supported" to true,
    )

    private fun loadModel(path: String?, requested: String?, result: MethodChannel.Result) {
        val roots = listOf(context.filesDir.absolutePath, context.getExternalFilesDir(null)?.absolutePath).filterNotNull()
        when {
            disposed -> result.error("unavailable", "Assistant unavailable", null)
            path.isNullOrEmpty() || !LocalLlmPolicy.isInsideAppStorage(path, roots) || !File(path).isFile ->
                result.error("model_missing", "Model file not found", null)
            loading -> result.error("busy", "Model is already loading", null)
            else -> {
                loading = true
                worker.execute {
                    releaseEngine()
                    var lastError: Throwable? = null
                    var loaded: Engine? = null
                    var used: String? = null
                    for (name in LocalLlmPolicy.backendOrder(requested)) {
                        try {
                            val backend = if (name == "gpu") Backend.GPU() else Backend.CPU()
                            val candidate = Engine(
                                EngineConfig(
                                    modelPath = path,
                                    backend = backend,
                                    maxNumTokens = LocalLlmPolicy.CONTEXT_TOKENS,
                                    cacheDir = context.cacheDir.path,
                                ),
                            )
                            candidate.initialize()
                            loaded = candidate
                            used = name
                            break
                        } catch (t: Throwable) {
                            lastError = t
                        }
                    }
                    main.post {
                        loading = false
                        if (loaded != null && !disposed) {
                            engine = loaded
                            backendName = used
                            result.success(mapOf("backend" to used, "fellBack" to (requested != "cpu" && used == "cpu")))
                        } else {
                            try { loaded?.close() } catch (_: Throwable) { }
                            result.error("load_failed", "Model could not be loaded", lastError?.javaClass?.simpleName)
                        }
                    }
                }
            }
        }
    }

    private fun generate(id: String?, prompt: String?, maxTokens: Int?, result: MethodChannel.Result) {
        val current = engine
        when {
            current == null -> result.error("not_loaded", "Model is not loaded", null)
            id.isNullOrEmpty() || prompt.isNullOrBlank() || prompt.length > LocalLlmPolicy.MAX_PROMPT_CHARS ->
                result.error("bad_prompt", "Prompt is empty or too long", null)
            generationId != null -> result.error("busy", "Another summary is running", null)
            else -> {
                generationId = id
                result.success(null)
                val cap = LocalLlmPolicy.clampMaxTokens(maxTokens)
                worker.execute { run(current, id, prompt, cap) }
            }
        }
    }

    private fun run(engine: Engine, id: String, prompt: String, cap: Int) {
        val started = SystemClock.elapsedRealtime()
        var firstTokenMs = -1L
        var chunks = 0
        val finished = java.util.concurrent.CountDownLatch(1)
        var failure: Throwable? = null
        try {
            val chat = engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
                    maxOutputToken = cap,
                ),
            )
            conversation = chat
            chat.sendMessageAsync(
                prompt,
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val text = message.toString()
                        if (text.isEmpty()) return
                        if (firstTokenMs < 0) firstTokenMs = SystemClock.elapsedRealtime() - started
                        chunks++
                        emit(mapOf("id" to id, "type" to "token", "text" to text))
                    }
                    override fun onDone() { finished.countDown() }
                    override fun onError(throwable: Throwable) { failure = throwable; finished.countDown() }
                },
            )
            finished.await()
        } catch (t: Throwable) {
            failure = t
        } finally {
            try { conversation?.close() } catch (_: Throwable) { }
            conversation = null
        }
        val cancelled = generationId == null || generationId != id
        generationId = null
        val error = failure
        when {
            cancelled -> emit(mapOf("id" to id, "type" to "cancelled"))
            error != null -> emit(mapOf("id" to id, "type" to "error", "code" to (error.javaClass.simpleName)))
            else -> emit(
                mapOf(
                    "id" to id,
                    "type" to "done",
                    "firstTokenMs" to firstTokenMs,
                    "totalMs" to SystemClock.elapsedRealtime() - started,
                    "chunks" to chunks,
                    "backend" to backendName,
                ),
            )
        }
    }

    private fun cancel() {
        // Clearing the id first tells run() the end was a cancel, not a finish.
        generationId = null
        try { conversation?.cancelProcess() } catch (_: Throwable) { }
    }

    private fun releaseEngine() {
        cancel()
        val old = engine
        engine = null
        backendName = null
        try { old?.close() } catch (_: Throwable) { }
    }

    private fun unload(reason: String) {
        cancel()
        worker.execute {
            releaseEngine()
            emit(mapOf("type" to "unloaded", "reason" to reason))
        }
    }

    fun onTrimMemory(level: Int) {
        if (engine == null || loading) return
        if (LocalLlmPolicy.unloadOnTrim(level, generationId != null)) unload("memory")
    }

    fun dispose() {
        disposed = true
        methods.setMethodCallHandler(null)
        events.setStreamHandler(null)
        cancel()
        worker.execute { releaseEngine() }
        worker.shutdown()
    }
}
