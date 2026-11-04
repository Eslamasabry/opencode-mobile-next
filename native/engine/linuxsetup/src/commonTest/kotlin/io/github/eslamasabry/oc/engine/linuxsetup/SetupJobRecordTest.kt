package io.github.eslamasabry.oc.engine.linuxsetup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** setup.json as the emulator wrote it on 2026-09-24 after a force-stop while Node downloaded. */
internal const val RECORDED_INTERRUPTED: String = """
{"jobId":"setup-1790196784299510","state":"interrupted","current":"node",
"order":["linux","essentials","python","node","opencode","start"],
"components":{
"linux":{"state":"done","weight":25,"stage":"Unpacking Linux base","done":29564928,"total":30028293,"percent":null,"version":"24.04.5","error":null,"startedAt":1790196784379,"endedAt":1790196792830},
"essentials":{"state":"done","weight":60,"stage":"Installing packages","done":null,"total":null,"percent":99,"version":"2.43.0","error":null,"startedAt":1790196792830,"endedAt":1790196821793},
"python":{"state":"done","weight":60,"stage":"Installing packages","done":null,"total":null,"percent":98,"version":"3.12.3","error":null,"startedAt":1790196821793,"endedAt":1790196838476},
"node":{"state":"pending","weight":30,"stage":"Downloading Node.js 24.21.0","done":23134208,"total":58088022,"percent":null,"version":null,"error":null,"startedAt":1790196838476,"endedAt":null},
"opencode":{"state":"pending","weight":240,"stage":null,"done":null,"total":null,"percent":null,"version":null,"error":null,"startedAt":null,"endedAt":null},
"start":{"state":"pending","weight":20,"stage":null,"done":null,"total":null,"percent":null,"version":null,"error":null,"startedAt":null,"endedAt":null,"data":{"openCodeChanged":"true","runtime":"opencode1"}}},
"overall":0.33,"startedAt":1790196784379,"updatedAt":1790196840162,"error":null,
"logTail":"Setting up python3-pip (24.0+dfsg-1ubuntu1.3) ...\nProcessing triggers for libc-bin (2.39-0ubuntu8.8) ...\n==> Downloading Node.js 24.21.0\n",
"params":{}}
"""

class SetupJobRecordTest {
    @Test
    fun `B-LINUX-202 the recorded interrupted job parses completely`() {
        val record = assertNotNull(SetupJobRecord.parse(RECORDED_INTERRUPTED))
        assertEquals("interrupted", record.state)
        assertTrue(record.canContinue)
        assertEquals("node", record.current)
        assertEquals(listOf("linux", "essentials", "python", "node", "opencode", "start"), record.components.map { it.id })
        val linux = assertNotNull(record.component("linux"))
        assertEquals(ComponentState.Done, linux.componentState)
        assertEquals(29564928L, linux.done)
        assertEquals(25.0, linux.weight)
        assertEquals(8451L, linux.endedAt!! - linux.startedAt!!)
        assertEquals(99.0, record.component("essentials")!!.percent)
        assertEquals(mapOf("openCodeChanged" to "true", "runtime" to "opencode1"), record.component("start")!!.data)
    }

    @Test
    fun `B-LINUX-202 keys keep the written order, unknown fields and states are tolerated, garbage is no job`() {
        val record =
            assertNotNull(
                SetupJobRecord.parse(
                    """{"jobId":"j","state":"something-new","surprise":[1,2],
                    "components":{"b":{"state":"running","done":1.0,"extra":true},"a":{"state":"weird"}}}""",
                ),
            )
        assertEquals(listOf("b", "a"), record.components.map { it.id })
        assertEquals(1L, record.components.first().done)
        assertEquals(ComponentState.Pending, record.components.last().componentState)
        val progress = progressFromRecord(record, emptyList(), Instant.fromEpochMilliseconds(1))
        assertEquals(SetupState.Interrupted, progress.state)
        assertNull(SetupJobRecord.parse(null))
        assertNull(SetupJobRecord.parse(""))
        assertNull(SetupJobRecord.parse("{not json"))
        assertNull(SetupJobRecord.parse("[]"))
        assertNull(SetupJobRecord.parse("""{"state":"running"}"""))
    }

    @Test
    fun `B-LINUX-208 a native persistence failure maps to a typed failure with plain words`() {
        val record =
            assertNotNull(
                SetupJobRecord.parse(
                    """{"jobId":"job","state":"failed","errorCode":"setup_persistence",
                    "error":"/private/storage: raw exception details","components":{}}""",
                ),
            )
        assertEquals(SetupFailureKind.Persistence, record.failureKind)
        val progress = progressFromRecord(record, emptyList(), Instant.fromEpochMilliseconds(1))
        assertEquals(SetupState.Failed, progress.state)
        assertEquals(SetupProblem.Install("OpenCode"), progress.error)
        val words = SetupProblemWordsEn.of(progress.error!!)
        assertEquals("Could not install OpenCode", words)
        assertFalse(words.contains("/private/"))
        assertFalse(words.contains("setup_persistence"))
    }
}
