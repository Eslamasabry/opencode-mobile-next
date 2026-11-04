package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.engine.runtime.devservers.TerminalText
import io.github.eslamasabry.oc.engine.runtime.setup.setupLogForPeople
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogBuffer
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogLevel
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogLine
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogPanel
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.linuxOutputError
import io.github.eslamasabry.oc.ui.words.res.linuxOutputWarning
import io.github.eslamasabry.oc.ui.words.res.setupTerminalTitle
import org.jetbrains.compose.resources.stringResource

private val error =
    Regex(
        "^(?:(?:npm|bun)\\s+)?(?:err!?\\b|error\\b|fatal\\b|failed\\b)|^status error\\b",
        RegexOption.IGNORE_CASE,
    )
private val warning =
    Regex("^(?:(?:npm|bun)\\s+)?warn(?:ing)?\\b|\\bretrying\\b", RegexOption.IGNORE_CASE)

/**
 * The setup source has already redacted this tail; classify only after stripping terminal controls.
 */
@Composable
internal fun SetupOutput(tail: String) {
    val lines =
        remember(tail) {
            val buffer = KitLogBuffer(200)
            for (raw in TerminalText.plain(setupLogForPeople(tail)).lineSequence()) buffer.add(
                KitLogLine(raw)
            )
            buffer.lines.map { line ->
                val message = line.text.trimStart().removePrefix("[oc]").trimStart()
                line.copy(
                    level =
                        when {
                            error.containsMatchIn(message) -> KitLogLevel.Error
                            warning.containsMatchIn(message) -> KitLogLevel.Warning
                            else -> KitLogLevel.Normal
                        }
                )
            }
        }
    if (tail.isNotBlank())
        KitLogPanel(
            lines,
            stringResource(Res.string.setupTerminalTitle),
            stringResource(Res.string.linuxOutputWarning),
            stringResource(Res.string.linuxOutputError),
        )
}
