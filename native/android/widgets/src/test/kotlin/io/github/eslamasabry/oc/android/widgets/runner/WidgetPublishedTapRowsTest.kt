package io.github.eslamasabry.oc.android.widgets.runner

import android.content.Context
import android.content.Intent
import io.github.eslamasabry.oc.android.widgets.AndroidWidgetSurface
import io.github.eslamasabry.oc.android.widgets.BackgroundConnectionService
import io.github.eslamasabry.oc.android.widgets.WidgetActionAdmission
import io.github.eslamasabry.oc.android.widgets.WidgetActionExtras
import io.github.eslamasabry.oc.android.widgets.WidgetFeed
import io.github.eslamasabry.oc.android.widgets.bindWidgetTestPersistence
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetRowDetails
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSession
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSnapshotBuilder
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSnapshotInput
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSnapshotWords
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.platform.ForegroundKind
import io.github.eslamasabry.oc.core.domain.platform.ForegroundTicket
import io.github.eslamasabry.oc.core.domain.platform.ForegroundWork
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.attention.WidgetActionError
import io.github.eslamasabry.oc.engine.attention.WidgetActionResult
import io.github.eslamasabry.oc.engine.attention.WidgetActionStatus
import io.github.eslamasabry.oc.engine.profiles.StoredProfileJson
import io.github.eslamasabry.opencode_mobile.WidgetActionPolicy
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A snapshot published the way the app publishes it (builder + on-demand identity + private target) admits a real
 * widget tap: the row's own button payload goes through `WidgetActionAdmission` and the bound runner to the exact
 * pending permission. Without the on-demand identity the same tap reads "unsupported".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetPublishedTapRowsTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val profiles = """[{"id":"p","name":"Server","baseUrl":"http://localhost:4096","backend":"openCode","flavor":"v2"}]"""
    private val fingerprint = widgetProfileFingerprint(StoredProfileJson.decodeList(profiles).single())
    private val foreground =
        object : ForegroundWork {
            override val active = MutableStateFlow(persistentSetOf(ForegroundKind.WidgetAction))

            override suspend fun begin(kind: ForegroundKind): Outcome<ForegroundTicket> =
                Outcome.Ok(ForegroundTicket(kind, 1))

            override suspend fun end(ticket: ForegroundTicket) = Unit
        }

    @org.junit.Before
    fun work() {
        // An accepted tap schedules the read-only follow-up check through WorkManager.
        try {
            androidx.work.WorkManager.initialize(
                context,
                androidx.work.Configuration.Builder().setExecutor { it.run() }.build(),
            )
        } catch (_: IllegalStateException) {}
    }

    @After
    fun reset() {
        WidgetFeed.reset()
    }

    private suspend fun publish(onDemand: Boolean): JSONObject {
        val persistence = bindWidgetTestPersistence(context)
        assertTrue(persistence.cas("p", "oc.profiles", persistence.string("oc.profiles"), profiles))
        assertTrue(persistence.cas("p", "oc.activeProfile", persistence.string("oc.activeProfile"), "p"))
        val token = WidgetActionScope { ByteArray(it) { 7 } }.token(listOf("p", 1, "/private/project", null))
        val details =
            WidgetRowDetails(
                requestId = "r1",
                startedAt = 1000,
                runMarker = if (onDemand) "user-1" else null,
                steerMode = "steer",
                onDemandActions = WidgetActionScope.actions(onDemand, permission = true, busy = true, runMarker = "user-1"),
            )
        val snapshot =
            WidgetSnapshotBuilder(Redactor(), WidgetSnapshotWords())
                .build(
                    WidgetSnapshotInput(
                        sessions = listOf(WidgetSession("s1", "Fix the build", 1)),
                        busy = setOf("s1"),
                        connected = true,
                        profileId = "p",
                        scopeToken = if (onDemand) token else "",
                        pendingCount = 1,
                        details = mapOf("s1" to details),
                        onDemandFingerprint = if (onDemand) fingerprint else null,
                    )
                )
        val target =
            if (onDemand) WidgetActionScope.target("p", token, fingerprint, "/private/project", null) else null
        AndroidWidgetSurface(context, persistence) {}.publish(snapshot.toString(), "p", target)
        return JSONObject(persistence.string("oc.widgetSessions")!!)
    }

    /** The payload the row's Allow once button carries (WidgetV2Bridge.actionIntent), read back like the service. */
    private fun tap(published: JSONObject) =
        WidgetActionExtras.read(
            Intent(BackgroundConnectionService.ACTION_WIDGET_ACTION)
                .putExtra(
                    WidgetV2Bridge.EXTRA_PAYLOAD,
                    JSONObject(
                            WidgetV2Bridge.identity(published, published.getJSONArray("sessions").getJSONObject(0))
                                .toMutableMap()
                                .apply {
                                    put("action", "allowOnce")
                                    put("id", WidgetActionPolicy.token(this))
                                }
                        )
                        .toString(),
                )
        )!!

    @Test
    fun `B-SEC-180 a published permission row admits its allow once tap and answers only that request`() = runTest {
        val published = publish(onDemand = true)
        assertFalse(published.toString().contains("/private/project"))
        val transport =
            FakeTransport().apply {
                pending = mutableListOf(WidgetPendingAsk("r1", "s1"), WidgetPendingAsk("r2", "s2"))
            }
        var opened: Pair<String?, String?>? = null
        WidgetFeed.backgroundRunner =
            WidgetRunnerHost(
                backgroundScope,
                WidgetFeed.persistence!!,
                profile = { WidgetRunProfile(it, fingerprint) },
                opener = { _, directory, workspace ->
                    opened = directory to workspace
                    WidgetOpen.Opened(transport)
                },
                preview = { "" },
                now = { 1000L },
            )
        val result = WidgetActionAdmission(context, foreground).run(tap(published))
        assertEquals(Outcome.Ok(WidgetActionResult(WidgetActionStatus.Accepted)), result)
        assertEquals(listOf("r1:once:s1"), transport.mutations)
        assertEquals("/private/project" to null, opened)
        assertTrue(transport.closed)
        // The answered request left the published row; the snapshot never held the private directory.
        val row = JSONObject(WidgetFeed.persistence!!.string("oc.widgetSessions")!!).getJSONArray("sessions").getJSONObject(0)
        assertFalse(row.has("requestID"))
    }

    @Test
    fun `B-ANDR-121 the same row without on-demand identity is refused and never connects`() = runTest {
        val published = publish(onDemand = false)
        var opened = false
        WidgetFeed.backgroundRunner =
            WidgetRunnerHost(
                backgroundScope,
                WidgetFeed.persistence!!,
                profile = { WidgetRunProfile(it, fingerprint) },
                opener = { _, _, _ ->
                    opened = true
                    WidgetOpen.Opened(FakeTransport())
                },
                preview = { "" },
                now = { 1000L },
            )
        val result = WidgetActionAdmission(context, foreground).run(tap(published))
        // No scope token: the tap's identity cannot match (stale); no fingerprint would read unsupported.
        val status = (result as Outcome.Ok).value
        assertEquals(WidgetActionStatus.Rejected, status.status)
        assertTrue(status.error in setOf(WidgetActionError.StaleRequest, WidgetActionError.Unsupported))
        assertFalse(opened)
    }
}
