package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.UUID

/** Transient overlay interactions, including the single in-flight dictation and submission. */
internal class WidgetOverlaySession(
    private val activity: WidgetOverlayActivity,
    private val launch: Map<String, Any?>,
    private val mode: String,
    widgetId: Int,
    savedState: Bundle?,
) {
    private val style = WidgetOverlayStyle(activity, launch, widgetId)
    private val submissionId = savedState?.getString("submissionId") ?: UUID.randomUUID().toString()
    private var submitted = savedState?.getBoolean("submitted") ?: false
    private var dictationResult: MethodChannel.Result? = null
    private val dictationHandler = Handler(Looper.getMainLooper())
    private val dictationTimeout = Runnable {
        dictationResult?.error("cancelled", null, null)
        dictationResult = null
    }
    private val launchArguments = mapOf("route" to "/widgets/$mode", "widgetId" to widgetId)

    fun handle(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getLaunchArguments" -> result.success(launch + launchArguments + mapOf("style" to style.read()) +
                projectArguments())
            "saveStyle" -> if (mode != "configure") result.error("wrongRoute", null, null)
                else style.save(call.arguments, result)
            "saveProject" -> if (mode != "project") result.error("wrongRoute", null, null)
                else style.saveProject(call.arguments, result)
            "dictate" -> dictate(call.arguments, result)
            "submitAction" -> submit(call.arguments, result)
            "close" -> { result.success(null); activity.finish() }
            else -> result.notImplemented()
        }
    }

    /** The Conversations widget's Edit: what it can list, and what it lists now. */
    private fun projectArguments(): Map<String, Any?> =
        if (mode == "project") mapOf("projects" to style.projects(), "project" to style.project()) else emptyMap()

    private fun currentScope(): Boolean {
        val snapshot = WidgetV2Bridge.snapshot(activity) ?: return false
        return !(launch["profileID"] as? String).isNullOrBlank() &&
            snapshot.optString("profileID") == launch["profileID"] &&
            snapshot.optString("scopeToken") == launch["scopeToken"]
    }

    private fun dictate(arguments: Any?, result: MethodChannel.Result) {
        if (mode !in setOf("steer", "voice") || !currentScope()) {
            result.error("unavailable", null, null)
        } else if (dictationResult != null || submitted) {
            result.error("busy", null, null)
        } else {
            val language = (arguments as? Map<*, *>)?.get("languageTag") as? String
            val speech = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                if (language != null && language.matches(Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8}){0,3}$"))) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                }
            }
            startDictation(speech, result)
        }
    }

    private fun startDictation(speech: Intent, result: MethodChannel.Result) {
        dictationResult = result
        try {
            // The system speech activity owns microphone permission.
            // This app neither records nor stores the spoken draft.
            activity.startActivityForResult(speech, DICTATION_REQUEST)
            dictationHandler.postDelayed(dictationTimeout, DICTATION_TIMEOUT_MS)
        } catch (_: RuntimeException) {
            dictationResult = null
            result.error("unavailable", null, null)
        }
    }

    private fun submit(arguments: Any?, result: MethodChannel.Result) {
        if (mode !in setOf("steer", "voice")) {
            result.error("wrongRoute", null, null)
            return
        }
        val text = (arguments as? Map<*, *>)?.get("text") as? String ?: ""
        val payload = WidgetActionBridge.overlayPayload(launch, mode, text) + mapOf("id" to submissionId)
        if (submitted) {
            result.success(WidgetActionBridge.result(payload, "rejected", "busy"))
        } else {
            submitted = true
            WidgetActionBridge.submit(activity, payload) {
                if (it["status"] == "rejected") submitted = false
                result.success(it)
            }
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != DICTATION_REQUEST) return
        val result = dictationResult ?: return
        dictationResult = null
        dictationHandler.removeCallbacks(dictationTimeout)
        if (!currentScope()) {
            result.error("unavailable", null, null)
        } else {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty().trim()
            if (resultCode != Activity.RESULT_OK || text.isBlank()) {
                result.error("cancelled", null, null)
            } else {
                result.success(mapOf("text" to text.take(WidgetActionPolicy.MAX_TEXT_LENGTH)))
            }
        }
    }

    fun destroy() {
        dictationHandler.removeCallbacks(dictationTimeout)
        dictationResult?.error("cancelled", null, null)
        dictationResult = null
    }

    fun save(outState: Bundle) {
        outState.putString("submissionId", submissionId)
        outState.putBoolean("submitted", submitted)
    }

    private companion object {
        const val DICTATION_REQUEST = 7183
        const val DICTATION_TIMEOUT_MS = 60_000L
    }
}
