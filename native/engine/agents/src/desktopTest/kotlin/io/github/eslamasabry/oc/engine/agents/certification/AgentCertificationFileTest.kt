package io.github.eslamasabry.oc.engine.agents.certification

import io.github.eslamasabry.oc.engine.agents.scripts.freezeTipSource
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
        // The freeze tip's set (lib/ now holds the released 2.1.5 app, without 0.12.0-0.14.0), plus 0.15.3: the phone
        // pin that moved after the freeze (93fcf4049, the same addition AgentPinsParityTest records).
        val dart = freezeTipSource("lib/paseo/tested_versions.dart")
        val set = Regex("paseoTestedVersions = \\{([^}]*)\\}").find(dart)!!.groupValues[1]
        assertEquals(
            Regex("'([^']+)'").findAll(set).map { it.groupValues[1] }.toList() + "0.15.3",
            AgentCertification.TestedVersions.PASEO.toList(),
        )
    }
}
