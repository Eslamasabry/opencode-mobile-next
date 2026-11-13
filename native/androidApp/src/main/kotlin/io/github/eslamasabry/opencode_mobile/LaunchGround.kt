package io.github.eslamasabry.opencode_mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.migration_opening
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** After this long without a first page the launch screen says what is happening. */
internal const val LAUNCH_LINE_AFTER_MS: Long = 2_000

/** Whether the launch screen shows its one plain line after [waitedMs] without a first page. */
internal fun launchLineShows(waitedMs: Long): Boolean = waitedMs >= LAUNCH_LINE_AFTER_MS

/**
 * What draws until the stored appearance is known: the same ground and mark as the window's launch background, so the
 * first frames never change, and after [LAUNCH_LINE_AFTER_MS] one plain line ("Opening…") under the mark.
 */
@Composable
internal fun LaunchGround() {
    var late by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LAUNCH_LINE_AFTER_MS)
        late = launchLineShows(LAUNCH_LINE_AFTER_MS)
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF101713)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(96.dp))
            if (late)
                BasicText(
                    stringResource(Res.string.migration_opening),
                    Modifier.padding(top = 8.dp),
                    style = TextStyle(color = Color(0xFF8FE5BD), fontSize = 16.sp),
                )
        }
    }
}
