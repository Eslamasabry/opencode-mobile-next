package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import android.content.Intent
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.platform.ForegroundKind
import io.github.eslamasabry.oc.core.domain.platform.ForegroundTicket
import io.github.eslamasabry.oc.core.domain.platform.ForegroundWork
import io.github.eslamasabry.oc.engine.attention.WidgetActionError
import io.github.eslamasabry.oc.engine.attention.WidgetActionIngress
import io.github.eslamasabry.oc.engine.attention.WidgetActionRequest
import io.github.eslamasabry.oc.engine.attention.WidgetActionResult
import io.github.eslamasabry.oc.engine.attention.WidgetActionStatus
import io.github.eslamasabry.opencode_mobile.SessionsWidgetProvider
import io.github.eslamasabry.opencode_mobile.WidgetActionBridge
import io.github.eslamasabry.opencode_mobile.WidgetActionLease
import io.github.eslamasabry.opencode_mobile.WidgetStatusScheduler
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app service shell reads frozen extras and passes this common request to the background body.
 */
public object WidgetActionExtras {
    public fun read(intent: Intent): WidgetActionRequest? {
        if (intent.action != BackgroundConnectionService.ACTION_WIDGET_ACTION) return null
        val p =
            WidgetV2Bridge.parsePayload(intent.getStringExtra(WidgetV2Bridge.EXTRA_PAYLOAD))
                ?: return null
        fun field(name: String) = p[name] as? String ?: ""
        return WidgetActionRequest(
            field("id"),
            field("action"),
            field("profileID"),
            field("scopeToken"),
            field("sessionID"),
            field("requestID"),
            field("runID"),
            field("gateID"),
            field("lastProjectID"),
            p["expectedStartedAt"] as? Long ?: 0L,
        )
    }
}

/**
 * One process lease, no service instance or sibling-module dependency. The FGS host owns promotion
 * and teardown.
 */
public class WidgetActionAdmission(
    context: Context,
    private val foreground: ForegroundWork,
) : WidgetActionIngress {
    private val app = context.applicationContext
    private val lease = Mutex()

    override suspend fun run(request: WidgetActionRequest): Outcome<WidgetActionResult> {
        if (!lease.tryLock())
            return Outcome.Ok(
                WidgetActionResult(WidgetActionStatus.Rejected, WidgetActionError.Busy)
            )
        var ticket: ForegroundTicket? = null
        val payload = request.payload()
        try {
            // A service body that already owns WidgetAction or a live connection is never demoted.
            val response =
                withTimeoutOrNull(WidgetActionLease.TIMEOUT_MS) {
                    if (
                        foreground.active.value.none {
                            it in
                                setOf(
                                    ForegroundKind.WidgetAction,
                                    ForegroundKind.LiveMode,
                                    ForegroundKind.ReplyWatch,
                                )
                        }
                    ) {
                        when (val begun = foreground.begin(ForegroundKind.WidgetAction)) {
                            is Outcome.Failed -> {
                                WidgetActionBridge.publishResult(
                                    app,
                                    payload,
                                    WidgetActionBridge.result(payload, "rejected", "unavailable"),
                                )
                                return@withTimeoutOrNull begun
                            }
                            is Outcome.Ok -> ticket = begun.value
                        }
                    }
                    Outcome.Ok(execute(payload))
                }
            if (response != null) return response
            val result =
                WidgetActionResult(
                    WidgetActionStatus.Uncertain,
                    WidgetActionError.UncertainDelivery,
                )
            WidgetActionBridge.publishResult(
                app,
                payload,
                WidgetActionBridge.result(payload, "uncertain", "uncertainDelivery"),
            )
            return Outcome.Ok(result)
        } finally {
            try {
                withContext(NonCancellable) {
                    WidgetFeed.onWidgetActionPending(false)
                    ticket?.let { foreground.end(it) }
                    SessionsWidgetProvider.refreshAll(app)
                }
            } finally {
                lease.unlock()
            }
        }
    }

    private suspend fun execute(payload: Map<String, Any?>): WidgetActionResult {
        val error = WidgetActionBridge.admissionError(app, payload)
        if (error != null)
            return settle(payload, WidgetActionBridge.result(payload, "rejected", error))
        if (WidgetFeed.connectionActive && WidgetFeed.liveDispatcher != null) {
            val result =
                kotlinx.coroutines.suspendCancellableCoroutine<Map<String, Any?>> { continuation ->
                    WidgetActionBridge.submit(app, payload) {
                        continuation.resumeWith(Result.success(it))
                    }
                }
            return typedResult(result)
        }
        val storage =
            WidgetFeed.persistence
                ?: return WidgetActionResult(
                    WidgetActionStatus.Rejected,
                    WidgetActionError.Unavailable,
                )
        val snapshot = WidgetV2Bridge.snapshot(app)
        val rows = snapshot?.optJSONArray("sessions")
        val row = rows?.let {
            (0 until it.length()).mapNotNull(it::optJSONObject).firstOrNull {
                it.optString("id") == payload["sessionID"]
            }
        }
        val actions = row?.optJSONArray("onDemandActions")
        val supported =
            !snapshot?.optJSONObject("onDemand")?.optString("fingerprint").isNullOrBlank() &&
                actions != null &&
                (0 until actions.length()).any { actions.optString(it) == payload["action"] }
        if (!supported)
            return settle(payload, WidgetActionBridge.result(payload, "rejected", "unsupported"))
        val claimed = WidgetActionBridge.claim(app, payload)
        if (claimed != null)
            return settle(
                payload,
                WidgetActionBridge.result(
                    payload,
                    if (claimed == "busy") "uncertain" else "rejected",
                    if (claimed == "busy") "uncertainDelivery" else claimed,
                ),
            )
        WidgetFeed.onWidgetActionPending(true)
        val response = awaitWidgetRun(app, payload, snapshot, storage, WidgetActionLease.TIMEOUT_MS)
        val safe = WidgetActionLease.safeResult(payload, response)
        if (safe["status"] == "rejected") WidgetActionBridge.releaseClaim(app, payload)
        if (
            safe["status"] == "accepted" &&
                WidgetActionBridge.applyActionSnapshot(
                    app,
                    payload,
                    response?.get("snapshot") as? Map<*, *>,
                )
        ) {
            WidgetStatusScheduler.afterAction(app, payload)
        }
        return settle(payload, safe)
    }

    private suspend fun settle(
        payload: Map<String, Any?>,
        value: Map<String, Any?>,
    ): WidgetActionResult {
        WidgetActionBridge.publishResult(app, payload, value)
        return typedResult(value)
    }
}

private fun WidgetActionRequest.payload(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "action" to action,
        "profileID" to profileId,
        "scopeToken" to scopeToken,
        "sessionID" to sessionId,
        "requestID" to requestId,
        "runID" to runId,
        "gateID" to gateId,
        "lastProjectID" to lastProjectId,
        "expectedStartedAt" to expectedStartedAt,
    )

private fun typedResult(value: Map<String, Any?>): WidgetActionResult =
    WidgetActionResult(
        when (value["status"]) {
            "accepted" -> WidgetActionStatus.Accepted
            "rejected" -> WidgetActionStatus.Rejected
            else -> WidgetActionStatus.Uncertain
        },
        WidgetActionError.entries.firstOrNull {
            it.name.replaceFirstChar(Char::lowercaseChar) == value["error"]
        },
    )
