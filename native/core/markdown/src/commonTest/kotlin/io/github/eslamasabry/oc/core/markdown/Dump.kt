package io.github.eslamasabry.oc.core.markdown

/**
 * Fixtures compare blocks through this compact form (plan 09 §14 wanted `.md` + `.blocks.json` resources; common
 * tests have no resource loader, so input and expected dump sit side by side in the test source).
 */
internal fun dump(blocks: List<Block>): List<String> = blocks.map(::dump)

internal fun dump(block: Block): String =
    when (block) {
        is Block.Prose -> "P ${q(block.inlines.text)}"
        is Block.Heading -> "H${block.level} ${q(block.inlines.text)}"
        is Block.Quote -> "Q ${q(block.inlines.text)}"
        is Block.ListItem ->
            "LI ${if (block.ordered) "${block.number}." else "•"} d${block.depth}${if (block.firstInList) " first" else ""} " +
                q(block.inlines.text)
        is Block.Code -> "C[${block.info ?: ""}]${if (block.open) " open" else ""} ${q(block.text)}"
        is Block.Table ->
            "T ${block.header.joinToString("|") { it.text }} / ${block.aligns.joinToString(",")} / " +
                block.rows.joinToString(";") { row -> row.joinToString("|") { it.text } }
        is Block.PictureGroup -> "IMG " + block.pictures.joinToString(",") { it.identity }
        is Block.AgentBlock ->
            "A ${block.type.info} " +
                block.items.joinToString(",") { item -> (if (item.done == true) "x:" else if (item.done == false) "o:" else "") + item.text }
        is Block.Rule -> "R"
    }

private fun q(s: String) = "\"" + s.replace("\n", "\\n") + "\""

/** Every drawn word of [blocks] (what a reader sees), for "no raw marks" checks. */
internal fun drawn(blocks: List<Block>): String =
    blocks.joinToString("\n") { block ->
        when (block) {
            is Block.Prose -> block.inlines.text
            is Block.Heading -> block.inlines.text
            is Block.Quote -> block.inlines.text
            is Block.ListItem -> block.inlines.text
            is Block.Code -> block.text
            is Block.Table -> (block.header + block.rows.flatten()).joinToString("\n") { it.text }
            is Block.PictureGroup -> block.pictures.joinToString("\n") { it.alt }
            is Block.AgentBlock -> block.items.joinToString("\n") { it.text }
            is Block.Rule -> ""
        }
    }

internal fun parse(text: String, streaming: Boolean = false): List<Block> = MarkdownBlocks.parse("p", text, streaming)
