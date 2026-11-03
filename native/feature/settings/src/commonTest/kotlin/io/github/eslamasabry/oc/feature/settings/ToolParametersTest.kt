package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.ToolParameterType
import io.github.eslamasabry.oc.feature.settings.presentation.ToolParameters
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Flutter `screen_library_3_test.dart` › tools › `toolParameters` (one part of B-NAV-047). */
class ToolParametersTest {
    @Test
    fun `B-NAV-047 tool parameters are read from a JSON schema, required first`() {
        val parameters =
            ToolParameters.of(
                Json.parseToJsonElement(
                    """{"type":"object","properties":{"a":{"type":"string"},"b":{"type":["integer","null"],"description":" How many "}},"required":["b"]}""",
                ),
            )
        assertEquals(listOf("b", "a"), parameters.map { it.name })
        assertEquals(ToolParameterType.Number, parameters.first().type)
        assertTrue(parameters.first().required)
        assertEquals("How many", parameters.first().description)
        assertEquals(ToolParameterType.Text, parameters[1].type)
        assertTrue(ToolParameters.of(null).isEmpty())
        assertTrue(ToolParameters.of(Json.parseToJsonElement("""{"type":"string"}""")).isEmpty())
    }
}
