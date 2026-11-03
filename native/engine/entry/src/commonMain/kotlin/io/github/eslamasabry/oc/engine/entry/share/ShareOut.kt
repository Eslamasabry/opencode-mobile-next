package io.github.eslamasabry.oc.engine.entry.share

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt

/**
 * Sends text out through the system share sheet (Flutter `ShareOut.text`): true when the sheet opened. Empty text
 * is never sent; a missing platform ([sheet] null), a refusal or a throw all answer false (B-ANDR-077).
 */
public class ShareOut(
    private val sheet: ShareSheet?,
) {
    public suspend fun text(text: String, subject: String? = null): Boolean {
        val sheet = sheet ?: return false
        if (text.isEmpty()) return false
        val result = attempt({ Failure.Unexpected() }) { sheet.shareText(text, subject) }
        val answer = (result as? Outcome.Ok)?.value
        return (answer as? Outcome.Ok)?.value == true
    }
}
