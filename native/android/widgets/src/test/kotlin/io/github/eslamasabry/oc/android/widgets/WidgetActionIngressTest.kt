package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import android.content.Intent
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.domain.platform.ForegroundKind
import io.github.eslamasabry.oc.core.domain.platform.ForegroundTicket
import io.github.eslamasabry.oc.core.domain.platform.ForegroundWork
import io.github.eslamasabry.oc.engine.attention.*
import io.github.eslamasabry.opencode_mobile.WidgetActionPolicy
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetActionIngressTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val order = mutableListOf<String>()
    private val foreground =
        object : ForegroundWork {
            override val active = MutableStateFlow<ImmutableSet<ForegroundKind>>(persistentSetOf())

            override suspend fun begin(kind: ForegroundKind): Outcome<ForegroundTicket> {
                order.add("begin")
                return Outcome.Ok(ForegroundTicket(kind, 1))
            }

            override suspend fun end(ticket: ForegroundTicket) {
                order.add("end")
            }
        }

    @After
    fun reset() {
        WidgetFeed.reset()
    }

    private fun request(): WidgetActionRequest {
        val row =
            JSONObject()
                .put("id", "a")
                .put("requestID", "q")
                .put("busy", true)
                .put("onDemandActions", JSONArray().put("allowOnce"))
        val snapshot =
            JSONObject()
                .put("version", 2)
                .put("profileID", "p")
                .put("scopeToken", "s")
                .put("sessions", JSONArray().put(row))
                .put("onDemand", JSONObject().put("fingerprint", "opaque"))
        context
            .getSharedPreferences(WidgetV2Bridge.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString(WidgetV2Bridge.SNAPSHOT_KEY, snapshot.toString())
            .commit()
        bindWidgetTestPersistence(context)
        val payload =
            WidgetV2Bridge.identity(snapshot, row).toMutableMap().apply {
                put("action", "allowOnce")
                put("id", WidgetActionPolicy.token(this))
            }
        val intent =
            Intent(BackgroundConnectionService.ACTION_WIDGET_ACTION)
                .putExtra(WidgetV2Bridge.EXTRA_PAYLOAD, JSONObject(payload).toString())
        return WidgetActionExtras.read(intent)!!
    }

    @Test
    fun `widgets-MAJOR-6 typed ingress promotes before cold admission and settles its own ticket`() =
        runTest {
            val request = request()
            WidgetFeed.backgroundRunner =
                object : WidgetBackgroundRunner {
                    override fun start(
                        context: Context,
                        payload: Map<String, Any?>,
                        snapshotJson: String,
                        deadlineEpochMs: Long,
                        operation: String,
                        complete: (Map<*, *>?) -> Unit,
                    ): WidgetBackgroundRunner.Handle {
                        order.add("runner")
                        complete(
                            mapOf(
                                "id" to payload["id"],
                                "action" to payload["action"],
                                "status" to "accepted",
                            )
                        )
                        return WidgetBackgroundRunner.Handle { order.add("cancel") }
                    }
                }
            val ingress: WidgetActionIngress = WidgetActionAdmission(context, foreground)
            assertEquals(
                Outcome.Ok(WidgetActionResult(WidgetActionStatus.Accepted)),
                ingress.run(request),
            )
            assertEquals(listOf("begin", "runner", "cancel", "end"), order)
            assertFalse(WidgetFeed.widgetActionPending)
            assertEquals(
                Outcome.Ok(
                    WidgetActionResult(
                        WidgetActionStatus.Uncertain,
                        WidgetActionError.UncertainDelivery,
                    )
                ),
                ingress.run(request),
            )
            assertEquals(1, order.count { it == "runner" })
        }

    @Test
    fun `widgets-MAJOR-6 a refused foreground ticket cannot dispatch or claim`() = runTest {
        val request = request()
        val refused =
            object : ForegroundWork by foreground {
                override suspend fun begin(kind: ForegroundKind): Outcome<ForegroundTicket> =
                    Outcome.Failed(Failure.Refused(RefusalReason.BackgroundLimit))
            }
        assertTrue(WidgetActionAdmission(context, refused).run(request) is Outcome.Failed)
        assertNull(WidgetFeed.persistence!!.string("oc.widgetActions.p"))
        assertTrue(order.isEmpty())
    }

    @Test
    fun `widgets-MAJOR-6 wrong intent action never enters typed ingress`() {
        assertNull(
            WidgetActionExtras.read(Intent("other").putExtra(WidgetV2Bridge.EXTRA_PAYLOAD, "{}"))
        )
    }
}
