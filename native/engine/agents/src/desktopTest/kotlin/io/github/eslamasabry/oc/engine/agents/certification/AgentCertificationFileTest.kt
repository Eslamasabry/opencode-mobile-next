package io.github.eslamasabry.oc.engine.agents.certification

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Plan 17 §1.9: the bundled certification snapshot is a verbatim copy of the reviewed matrix. */
class AgentCertificationFileTest {
    private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "docs/verification/agent-certification-matrix.json").isFile }

    @Test
    fun `B-AGENT-009 the bundled snapshot equals the pins file and the reviewed matrix byte for byte`() {
        val reviewed = File(repo, "docs/verification/agent-certification-matrix.json").readText()
        assertEquals(reviewed, File(repo, "native/engine/agents/pins/agent-certification-matrix.json").readText())
        assertEquals(reviewed, AGENT_CERTIFICATION_JSON)
        val dart = File(repo, "lib/domain/agent_tools/agent_certification_snapshot.dart").readText()
        assertEquals(reviewed, dart.substring(dart.indexOf("r'''") + 4, dart.lastIndexOf("'''")))
    }

    @Test
    fun `B-AGENT-009 the tested Paseo versions equal the Dart set`() {
        val dart = File(repo, "lib/paseo/tested_versions.dart").readText()
        val set = Regex("paseoTestedVersions = \\{([^}]*)\\}").find(dart)!!.groupValues[1]
        assertEquals(
            Regex("'([^']+)'").findAll(set).map { it.groupValues[1] }.toList(),
            AgentCertification.TestedVersions.PASEO.toList(),
        )
    }
}
