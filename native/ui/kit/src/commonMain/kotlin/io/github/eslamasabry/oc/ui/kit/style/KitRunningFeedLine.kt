package io.github.eslamasabry.oc.ui.kit.style

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

/** The Signal feed's segment time and one command; wall time is supplied by its engine. */
@Composable
public fun KitRunningFeedLine(
    sinceMs: Long?,
    nowMs: Long,
    command: String?,
    runningLabel: String,
    minutesLabel: (String) -> String,
    secondsLabel: ((String) -> String)? = null,
) {
    val started = remember(sinceMs, nowMs) { TimeSource.Monotonic.markNow() }
    var elapsed by
        remember(sinceMs, nowMs) {
            mutableLongStateOf((nowMs - (sinceMs ?: nowMs)).coerceAtLeast(0))
        }
    LaunchedEffect(sinceMs, nowMs) {
        if (sinceMs == null) return@LaunchedEffect
        while (true) {
            elapsed = (nowMs - sinceMs).coerceAtLeast(0) + started.elapsedNow().inWholeMilliseconds
            delay(1_000)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        if (sinceMs != null)
            KitText(
                listOfNotNull(
                        minutesLabel(Numbers.format((elapsed / 60_000).toInt())),
                        secondsLabel?.invoke(Numbers.format(((elapsed / 1_000) % 60).toInt())),
                    )
                    .joinToString(" "),
                Modifier.testTag("kit-feed-timer"),
                role = TypeRole.Mono,
                tone = TextTone.Accent,
            )
        KitText(
            listOfNotNull(runningLabel, command).joinToString(" · "),
            role = TypeRole.Secondary,
            tone = TextTone.Accent,
            maxLines = 1,
        )
    }
}
