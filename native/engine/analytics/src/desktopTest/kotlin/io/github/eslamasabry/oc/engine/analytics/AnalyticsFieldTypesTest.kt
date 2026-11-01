package io.github.eslamasabry.oc.engine.analytics

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * K-SEC-11 (plan 16 §5.4) on the JVM: every event class declares instance fields only of enum types, `boolean`,
 * [CountBucket], [DurationBucket] or [VersionCode]. No String, number, collection or JSON field can exist, so no
 * content can be put into an event at all.
 */
class AnalyticsFieldTypesTest {
    private val allowed = setOf(java.lang.Boolean.TYPE, CountBucket::class.java, DurationBucket::class.java, VersionCode::class.java)

    @Test
    fun `B-SEC-191 event classes hold only enums, booleans, buckets and a versionCode`() {
        val classes = EventSamples.all.map { it::class.java }
        classes.forEach { type ->
            val fields = type.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
            assertTrue(fields.isNotEmpty(), "${type.simpleName} declares no fields")
            fields.forEach { field ->
                val ok = field.type.isEnum || field.type in allowed
                assertTrue(ok, "${type.simpleName}.${field.name} is ${field.type.name}")
            }
        }
    }

    @Test
    fun `B-ANLY-004 the samples cover every subtype the compiler permits`() {
        val permitted = AnalyticsEvent::class.java.permittedSubclasses
        if (permitted != null) {
            assertEquals(permitted.map { it.name }.toSet(), EventSamples.all.map { it::class.java.name }.toSet())
        }
        // Either way, EventSamples.specOf is an exhaustive `when`: a new subtype fails compilation there.
    }
}
