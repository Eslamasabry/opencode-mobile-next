package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Channel
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.model.NetworkStatus
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.okhttp.OkHttpEngineFactory
import io.github.eslamasabry.oc.core.net.okhttp.hardenedBaseClient
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.SecretEdit
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.protocol.oc2.live.Oc2GatewayFactory
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * B-617: ReadOnlyMonitorGateway must set observeEvents = false so periodic monitor polls never
 * open unused /api/event SSE streams.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MonitorRuntimeObserveEventsTest {
    private class Recorder {
        val requestedPaths = CopyOnWriteArrayList<String>()
        val specs = CopyOnWriteArrayList<GatewaySpec>()
        val firstConnect = CompletableDeferred<Unit>()
    }

    private class Fixture(
        val job: kotlinx.coroutines.CompletableJob,
        val stores: PreviewStores,
        val runtime: MonitorRuntime,
        val recorder: Recorder,
    )

    @Test
    fun `B-617 ReadOnlyMonitorGateway passes observeEvents false and opens no event stream`() =
        runBlocking {
            val fixture = createFixture()
            try {
                assertIs<Outcome.Ok<Unit>>(fixture.stores.warm())
                val profileId = ProfileId("mon-1")
                assertIs<Outcome.Ok<*>>(
                    fixture.stores.profiles.upsert(
                        StoredProfile(
                            id = profileId,
                            name = "Work",
                            baseUrl = "http://127.0.0.1:4096",
                            backend = BackendKind.OpenCode2,
                            username = "opencode",
                        ),
                        SecretEdit.Set("pw"),
                    )
                )
                fixture.runtime.start()
                Robolectric.buildActivity(Activity::class.java).create().start()
                assertIs<Outcome.Ok<Unit>>(fixture.runtime.setEnabled(profileId.raw, true))
                withTimeout(10_000) { fixture.recorder.firstConnect.await() }
                // Give any wrongly-started SSE loop a brief chance to hit /api/event.
                delay(200)
                assertTrue(fixture.recorder.specs.isNotEmpty(), "monitor never connected to the server")
                assertFalse(
                    fixture.recorder.specs.first().observeEvents,
                    "ReadOnlyMonitorGateway must pass observeEvents = false",
                )
                assertTrue(
                    fixture.recorder.requestedPaths.none { it == "/api/event" },
                    "monitor poll opened unexpected /api/event SSE stream: ${fixture.recorder.requestedPaths}",
                )
            } finally {
                fixture.job.cancel()
            }
        }

    private fun createFixture(): Fixture {
        val app = RuntimeEnvironment.getApplication()
        val dispatchers = testAppDispatchers()
        val redactor = Redactor()
        val clock = TestClock()
        val job = SupervisorJob()
        val scope = AppCoroutineScope(job + dispatchers.default)
        val build = AppBuildInfo("3.0.2", 10130, Channel.Preview, debuggable = true)
        val migration = MigrationWiring(app, build, redactor, scope, dispatchers, clock)
        val stores =
            PreviewStores(app, dispatchers, redactor, StateMigrationGate.alwaysOpen(), migration, build)
        val recorder = Recorder()
        val factory = recordingOc2Factory(dispatchers, clock, redactor, recorder)
        val registry =
            ConnectionRegistry(
                emptySet(),
                ConnectionGraph.Factory { _, _, _ -> error("unused") },
                scope,
                dispatchers,
            )
        val network =
            object : NetworkStatus {
                override val state: StateFlow<NetworkState> =
                    MutableStateFlow(NetworkState(NetworkState.Online.Yes, onWifi = true))
            }
        val runtime =
            MonitorRuntime(
                app,
                stores,
                AppAutomationPolicies(stores, redactor, scope, dispatchers),
                registry,
                setOf(factory),
                setOf(ConnectionCredentialSource { Outcome.Ok(ConnectionCredentials("opencode", "pw")) }),
                network,
                BackgroundHub(app, scope, dispatchers, clock, stores, ResumedActivities(app)),
                AppPreferences(stores, scope),
                redactor,
                clock,
                scope,
                dispatchers,
            )
        return Fixture(job, stores, runtime, recorder)
    }

    private fun testAppDispatchers(): AppDispatchers =
        object : AppDispatchers {
            override val main = Dispatchers.Default
            override val default = Dispatchers.Default
            override val io = Dispatchers.IO

            override fun serial(name: String) = Dispatchers.Default.limitedParallelism(1, name)
        }

    private fun recordingOc2Factory(
        dispatchers: AppDispatchers,
        clock: TestClock,
        redactor: Redactor,
        recorder: Recorder,
    ): GatewayFactory {
        val engines =
            OkHttpEngineFactory(
                base = { spec ->
                    hardenedBaseClient(spec)
                        .newBuilder()
                        .addInterceptor { chain -> syntheticAnswer(chain, recorder.requestedPaths) }
                        .build()
                }
            )
        val realOc2 = Oc2GatewayFactory(HttpClientFactory(engines), dispatchers, clock, redactor, Random(1))
        return object : GatewayFactory by realOc2 {
            override suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair> {
                recorder.specs += spec
                recorder.firstConnect.complete(Unit)
                return realOc2.connect(spec)
            }
        }
    }

    private fun syntheticAnswer(
        chain: okhttp3.Interceptor.Chain,
        requestedPaths: CopyOnWriteArrayList<String>,
    ): Response {
        val path = chain.request().url.encodedPath
        requestedPaths += path
        val (type, body) =
            when (path) {
                "/api/health" ->
                    "application/json" to """{"healthy":true,"version":"0.0.0-beta-19242"}"""
                "/api/event" -> "text/event-stream" to ": ping\n\n"
                "/api/session/status" -> "application/json" to """{"data":{}}"""
                else -> "application/json" to """{"data":[]}"""
            }
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Type", type)
            .body(body.toResponseBody(type.toMediaType()))
            .build()
    }
}
