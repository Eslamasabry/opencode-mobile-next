package io.github.eslamasabry.oc.feature.setup.rb.o40

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.runtime.setup.*
import io.github.eslamasabry.oc.feature.setup.presentation.*
import io.github.eslamasabry.oc.feature.setup.ui.SetupScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.setup.*
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

class SetupGateTest {
    private object Actions : SetupActions {
        override fun start() {}
        override fun pause() {}
        override fun resume() {}
        override fun openAppSettings() {}
        override fun back() {}
    }
    // setup_progress_running / setup_progress_failed render the live PhoneSetupRoute.Setup screen in
    // :feature:phonesetup (SetupJourneyGateTest). SetupRoute.Phone here is opened by no route, and two modules writing
    // the same golden's rows let whichever ran last decide the measurement.
    @Test fun phone_drawing() {
        FlutterGoldenGate.check("kit_setup_fresh") { KitSetupPhone(modifier = Modifier.width(240.dp)) }
    }
    @Test fun ready_drawing() {
        FlutterGoldenGate.check("kit_setup_phone_ready") { KitSetupPhone(KitSetupPhoneMood.Ready, Modifier.width(240.dp)) }
    }
    @Test fun stopped_drawing() {
        FlutterGoldenGate.check("kit_setup_stopped") { KitSetupPhone(KitSetupPhoneMood.Stopped, Modifier.width(240.dp)) }
    }
    @Test fun starting_drawing() {
        FlutterGoldenGate.check("kit_setup_starting") { KitSetupPhone(KitSetupPhoneMood.Starting, Modifier.width(240.dp)) }
    }
    @Test fun finished_drawing() {
        FlutterGoldenGate.check("kit_setup_ready") { KitSetupPhone(KitSetupPhoneMood.Celebration, Modifier.width(240.dp)) }
    }
}
