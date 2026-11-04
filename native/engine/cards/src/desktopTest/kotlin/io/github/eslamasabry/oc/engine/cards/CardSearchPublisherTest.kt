package io.github.eslamasabry.oc.engine.cards

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Flutter `gen_ui_search_publish_test.dart` (B-CHAT-046) over the real file system. */
class CardSearchPublisherTest {
    private val support: File = Files.createTempDirectory("search-publish-").toFile()
    private val publisher = CardSearchPublisher(NioCardsRootFs(support))

    @AfterTest
    fun clean() {
        support.deleteRecursively()
    }

    private fun managed(relative: String, mode: String = "rwx------"): File =
        File(support, "linux/ubuntu/$relative").apply {
            mkdirs()
            Files.setPosixFilePermissions(toPath(), PosixFilePermissions.fromString(mode))
        }

    private suspend fun publish(
        profile: String = "profile",
        agent: CardsHelperAgent = CardsHelperAgent.Claude,
        endpoint: String = "http://127.0.0.1:4321/find-connectors",
        bearer: String = "a".repeat(64),
    ) = publisher.publish(profile, agent, endpoint, bearer)

    @Test
    fun `B-CHAT-046 publishes a private descriptor only in the existing app-owned root`() = runTest {
        val directory = managed("home/oc/.oc-profiles/profile/.oc-genui")
        assertTrue(publish())
        val file = File(directory, "enabled.search.json")
        val json = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals("http://127.0.0.1:4321/find-connectors", json["endpoint"]!!.jsonPrimitive.content)
        assertEquals("a".repeat(64), json["bearer"]!!.jsonPrimitive.content)
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())))
        assertTrue(publish(bearer = "b".repeat(64)))
        assertEquals("b".repeat(64), Json.parseToJsonElement(file.readText()).jsonObject["bearer"]!!.jsonPrimitive.content)
        assertEquals(1, directory.list()!!.size, "no temporary file is left behind")
        val root = managed("root/.oc-genui/openCode2")
        assertTrue(publish(agent = CardsHelperAgent.OpenCode2))
        assertTrue(File(root, "enabled.search.json").isFile)
    }

    @Test
    fun `B-CHAT-046 refuses bad input, a missing root, symlinks and a public folder`() = runTest {
        assertFalse(publish(), "no root yet")
        val directory = managed("home/oc/.oc-profiles/profile/.oc-genui")
        for (url in listOf(
            "https://example.com/find-connectors",
            "http://127.0.0.1:4321/other",
            "http://user@127.0.0.1:4321/find-connectors",
            "http://127.0.0.1:4321/find-connectors?x=1",
            "http://127.0.0.1:99999/find-connectors",
        )) {
            assertFalse(publish(endpoint = url), url)
        }
        assertFalse(publish(profile = "../escape"))
        assertFalse(publish(bearer = "invalid"))
        assertFalse(publish(agent = CardsHelperAgent.Claude.copy(configured = false)))
        val outside = File(support, "outside").apply { writeText("unchanged") }
        val link = File(directory, "enabled.search.json").toPath()
        Files.createSymbolicLink(link, outside.toPath())
        assertFalse(publish())
        assertEquals("unchanged", outside.readText())
        Files.delete(link)
        Files.setPosixFilePermissions(directory.toPath(), PosixFilePermissions.fromString("rwxrwxrwx"))
        assertFalse(publish())
        assertFalse(Files.exists(link, LinkOption.NOFOLLOW_LINKS))
    }

    @Test
    fun `B-CHAT-046 the published descriptor is what the card tool server reads`() = runTest {
        val directory = managed("home/oc/.oc-profiles/profile/.oc-genui")
        assertTrue(publish())
        assertEquals(
            File(directory, "enabled.search.json").path,
            CardToolServer.searchBridgePath(File(directory, "enabled").path),
        )
    }
}
