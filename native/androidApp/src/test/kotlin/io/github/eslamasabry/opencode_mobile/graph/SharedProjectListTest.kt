package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * W3: the folders the phone's Linux binds come from one saved list per profile, written by New conversation (a folder
 * chosen in the phone's storage), the Files page (a grant) and a project opened from storage. One function writes it,
 * comparing and setting, so writers at the same moment never drop each other's folder.
 */
class SharedProjectListTest {
    private val phone = ProfileId("phone")
    private val chosen = "/storage/emulated/0/Documents/docsdemo"
    private val granted = "/storage/emulated/0/Download/notes"

    @Test
    fun `W3-STORAGE two writers adding a folder at the same moment both keep theirs`() = runTest {
        val kv =
            PrefsKeyValueStore(
                InMemoryPrefsBackend(),
                FlutterPrefsCodec(JavaListDecoder.Unsupported),
                StateMigrationGate.alwaysOpen(),
                StandardTestDispatcher(testScheduler),
            )
        val lanes = DefaultWriterLanes()
        val first = SharedProjectList.add(kv, lanes.forProfile(phone), phone, "/storage/emulated/0/Old")
        assertIs<Outcome.Ok<Boolean>>(first)
        // Both read the list before either writes: New conversation's check and the Files page's grant.
        val results =
            listOf(chosen, granted)
                .map { root -> async { SharedProjectList.add(kv, lanes.forProfile(phone), phone, root) } }
                .awaitAll()
        results.forEach { assertEquals(Outcome.Ok(true), it) }
        assertEquals(listOf(granted, chosen, "/storage/emulated/0/Old").sorted(), SharedProjectList.read(kv, phone))
        // The restart flag of a project opened from storage goes in the same commit, also when the folder was known.
        val pending = "oc.sharedProjectsPending.${phone.raw}"
        assertEquals(
            Outcome.Ok(false),
            SharedProjectList.add(kv, lanes.forProfile(phone), phone, chosen) { putBool(pending, true) },
        )
        assertEquals(PrefValue.Bool(true), kv.get(pending))
        // Removing goes through the same function; the key goes with the last folder.
        val removals =
            listOf(chosen, granted, "/storage/emulated/0/Old")
                .map { root -> async { SharedProjectList.remove(kv, lanes.forProfile(phone), phone, root) } }
                .awaitAll()
        removals.forEach { assertEquals(Outcome.Ok(true), it) }
        assertEquals(emptyMap(), SharedProjectList.all(kv))
    }

    @Test
    fun `W3-STORAGE nothing but SharedProjectList spells the key of the bound-folder list`() {
        // The module dir is the working directory of these tests; the shared code of the app sits beside it.
        val native = File("..").canonicalFile
        val roots =
            listOf(File("src/main")) +
                listOf("feature", "engine", "core").flatMap { group ->
                    File(native, group).listFiles().orEmpty().map { File(it, "src") }
                }
        val sources =
            roots
                .filter { it.isDirectory }
                .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
                .filterNot { file ->
                    file.invariantSeparatorsPath.split("/").any { it.endsWith("Test") || it == "test" || it == "build" }
                }
        val owner = sources.filter { it.name == "SharedProjectList.kt" }
        assertEquals(1, owner.size, "the scan reaches the one writer")
        val spelled =
            sources
                .filter { it.name != "SharedProjectList.kt" }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        val code = line.trim()
                        val comment = code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")
                        if (!comment && "oc.sharedProjects." in code) "${file.name}:${index + 1}" else null
                    }
                }
        assertTrue(
            spelled.isEmpty(),
            "write the bound-folder list only through SharedProjectList.add/remove (compare and set): $spelled",
        )
    }
}
