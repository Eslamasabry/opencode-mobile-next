package io.github.eslamasabry.oc.ui.design.color

import io.github.eslamasabry.oc.ui.design.type.TextTone

/** The glyph each status tone carries, so no state is told apart by colour alone (plan 06 §4.10). */
public enum class StatusGlyph { EmptyRing, Sync, Check, Warning, Error }

/**
 * A status tone → a text tone + one glyph (plan 06 §4.10; Dart `AppStatusTone`). Failure reads in primary text with
 * a neutral glyph: nothing is red.
 */
public enum class StatusTone(public val text: TextTone, public val glyph: StatusGlyph) {
    Neutral(TextTone.Secondary, StatusGlyph.EmptyRing),
    Progress(TextTone.Accent, StatusGlyph.Sync),
    Ok(TextTone.Success, StatusGlyph.Check),
    Attention(TextTone.Attention, StatusGlyph.Warning),
    Failure(TextTone.Primary, StatusGlyph.Error),
}
