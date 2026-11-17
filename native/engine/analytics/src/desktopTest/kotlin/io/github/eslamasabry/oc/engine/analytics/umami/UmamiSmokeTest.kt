package io.github.eslamasabry.oc.engine.analytics.umami

import io.github.eslamasabry.oc.engine.analytics.DeviceClass
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Failure
import java.io.File
import java.util.Properties
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals

/** Opt-in only. Sends exactly one short desktop session through the production sender, without sign-in. */
class UmamiSmokeTest {
    @Test
    fun `B-180 explicit desktop smoke sends one tagged session through our sender`() {
        if (System.getProperty("oc.umamiSmoke") != "true") return
        var root = File(System.getProperty("user.dir")).absoluteFile
        while (!File(root, "native/version.properties").isFile) root = checkNotNull(root.parentFile)
        fun properties(path: String) = Properties().apply { File(root, path).inputStream().use(::load) }
        val version = properties("native/version.properties")
        val config = properties("native/androidApp/analytics.properties")
        val build = UmamiBuild(version.getProperty("versionName"), version.getProperty("versionCode"), "test-${version.getProperty("versionCode")}")
        val agent = "Mozilla/5.0 (Linux; Android 16; Android Phone) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"
        val device = UmamiDevice("en-US", agent, 16, DeviceClass.Phone)
        val tasks = ArrayDeque<Runnable>()
        var at = System.currentTimeMillis() - 3000
        val store = object : UmamiStore {
            var value: String? = null
            override suspend fun read(): Outcome<String?> = Outcome.Ok(value)
            override suspend fun replace(value: String?): Outcome<Unit> { this.value = value; return Outcome.Ok(Unit) }
        }
        val sender = UmamiSender(config.getProperty("OC_UMAMI_WEBSITE"), build, { device }, { at }, store,
            UmamiHttp(config.getProperty("OC_UMAMI_URL")) { device.userAgent }, executor = Executor { tasks.add(it) })
        sender.setUsage(true)
        sender.event("app_open", mapOf("kind" to "cold")); at += 1000
        sender.event("screen_view", mapOf("screen" to "conversations")); at += 1000
        sender.event("screen_view", mapOf("screen" to "new_chat")); at += 1000
        sender.event("memory_pressure", mapOf("level" to "low"))
        while (tasks.isNotEmpty()) tasks.removeFirst().run()
        assertEquals(0, sender.queued, "server must acknowledge the four events; this test never retries by itself")
        println("UMAMI_SMOKE tag=${build.tag} version=${build.version} build=${build.build}: app_open /home -> page /home -> page /new-conversation -> memory_pressure /new-conversation")
    }
}
