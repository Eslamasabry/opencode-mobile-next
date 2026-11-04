package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.runtime.messages.SetupMessage
import io.github.eslamasabry.oc.engine.runtime.messages.SetupMessages
import io.github.eslamasabry.oc.engine.runtime.messages.SetupSegment
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.allStringResources
import org.jetbrains.compose.resources.stringResource

/** App-authored setup facts in the current language; unknown script detail stays verbatim. */
@Composable
internal fun setupMessageWords(text: String): String =
    setupMessageWords(SetupMessages.classify(text))

@Composable
internal fun setupMessageWords(message: SetupMessage): String =
    when (message) {
        is SetupMessage.Known ->
            stringResource(
                Res.allStringResources.getValue(message.key),
                *message.args.toTypedArray(),
            )
        is SetupMessage.Mixed -> {
            val parts = mutableListOf<String>()
            for (segment in message.segments) {
                parts +=
                    when (segment) {
                        is SetupSegment.Text -> segment.text
                        is SetupSegment.Word ->
                            stringResource(Res.allStringResources.getValue(segment.key))
                    }
            }
            parts.joinToString("")
        }
    }
