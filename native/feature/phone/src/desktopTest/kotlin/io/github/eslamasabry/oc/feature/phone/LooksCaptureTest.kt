package io.github.eslamasabry.oc.feature.phone

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerHeadline
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerLine
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerTone
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveStepKind
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveStepUi
import io.github.eslamasabry.oc.feature.phone.presentation.KeepRunningActions
import io.github.eslamasabry.oc.feature.phone.presentation.KeepRunningUi
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneAct
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneMaker
import io.github.eslamasabry.oc.feature.phone.presentation.RunningActions
import io.github.eslamasabry.oc.feature.phone.presentation.RunningRowUi
import io.github.eslamasabry.oc.feature.phone.presentation.ServerRow
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneActions
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneAnswer
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneUi
import io.github.eslamasabry.oc.feature.phone.presentation.UpdateRow
import io.github.eslamasabry.oc.feature.phone.presentation.VersionRowUi
import io.github.eslamasabry.oc.feature.phone.ui.KeepRunningScreen
import io.github.eslamasabry.oc.feature.phone.ui.ThisPhoneScreen
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LooksCaptureTest {
    private class Calls : ThisPhoneActions, RunningActions, KeepRunningActions {
        override fun act(act: PhoneAct) {}
        override fun stopServer() {}
        override fun askRestart() {}
        override fun askUpdate() {}
        override fun confirm() {}
        override fun dismiss() {}
        override fun openRunning() {}
        override fun openKeepRunning() {}
        override fun back() {}
        override fun shown(visible: Boolean) {}
        override fun retry() {}
        override fun details(pid: Int) {}
        override fun askStop(pid: Int) {}
        override fun confirmStop() {}
        override fun openChildLimitSettings() {}
        override fun hideChildLimit() {}
        override fun resumed() {}
        override fun dismissRecovery() {}
        override fun refreshStorage() {}
        override fun measureStorage() {}
        override fun open(step: KeepAliveStepKind) {}
    }

    @Test
    fun keepRunning() {
        val ui = KeepRunningUi(
            loaded = true,
            maker = PhoneMaker.Samsung,
            steps = persistentListOf(
                KeepAliveStepUi(KeepAliveStepKind.Battery, done = true, opens = null),
                KeepAliveStepUi(KeepAliveStepKind.Background, done = false, opens = io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveSetting.AppDetails),
                KeepAliveStepUi(KeepAliveStepKind.LockInRecents, done = false, opens = null),
            ),
            swipeWarning = true,
        )
        Looks.shoot("native-08-keep-running") { KeepRunningScreen(ui, Calls()) }
    }

    @Test
    fun thisPhone() {
        val ui = ThisPhoneUi(
            answer = ThisPhoneAnswer(AnswerTone.Ok, AnswerHeadline.Running, AnswerLine.Storage(410L * 1024 * 1024)),
            serverRows = persistentListOf(ServerRow.Stop, ServerRow.Restart),
            version = VersionRowUi("2.0.28", UpdateRow.Available("2.1.1")),
            running = RunningRowUi(count = 7),
        )
        Looks.shoot("native-08b-this-phone") { ThisPhoneScreen(ui, Calls()) }
    }
}
