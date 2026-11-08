package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.ui.noticesForReading
import kotlin.test.Test
import kotlin.test.assertEquals

/** Audit C07 (2026-11-09): the notices page showed its Markdown source; it now hands the Markdown part clean blocks. */
class NoticesReadingTest {
    @Test
    fun `C07 the page title is not repeated, wrapped lines join and links become their words`() {
        val source =
            listOf(
                "## Bundled components",
                "",
                "### Phone project engine",
                "",
                "The engine is built from `engine/phone/` and its",
                "committed `Cargo.lock`. See [`LICENSES/Rust.txt`](LICENSES/Rust.txt) and",
                "[the guide](https://example.org/guide).",
                "",
                "- first item that",
                "  wraps",
                "- second item",
            ).joinToString("\n")
        assertEquals(
            listOf(
                "### Phone project engine",
                "",
                "The engine is built from `engine/phone/` and its committed `Cargo.lock`. See `LICENSES/Rust.txt` and " +
                    "the guide (https://example.org/guide).",
                "",
                "- first item that wraps",
                "- second item",
            ).joinToString("\n"),
            noticesForReading(source),
        )
    }
}
