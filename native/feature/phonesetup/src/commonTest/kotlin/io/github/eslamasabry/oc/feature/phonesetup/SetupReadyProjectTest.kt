@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.phonesetup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.FirstProject
import io.github.eslamasabry.oc.feature.phonesetup.presentation.FirstProjectName
import io.github.eslamasabry.oc.feature.phonesetup.presentation.FirstProjectNameProblem
import io.github.eslamasabry.oc.feature.phonesetup.presentation.FirstProjectStep
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneySource
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyViewModel
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupOptions
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parity audit 2026-11-08 row 459 (Flutter phone_setup_ready_screen.dart; B-AGENT-126 makes the folder with git). */
class SetupReadyProjectTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private class Source(state: SetupState = SetupState.Done) : SetupJourneySource {
        override val progress = MutableStateFlow(SetupProgress(state, emptyList(), 1.0))
        override val toolsChanged = MutableStateFlow(0)
        override val selection = MutableStateFlow(emptySet<String>())

        override fun details(progress: SetupProgress): Redacted? = null

        override suspend fun restore(): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun options(adding: Boolean): Outcome<SetupOptions> = Outcome.Failed(Failure.Busy(io.github.eslamasabry.oc.core.common.BusyKind.SessionBusy))

        override suspend fun choose(ids: Set<String>): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun run(ids: Set<String>, adding: Boolean): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun resume(): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun cancel(jobId: String): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun removeVoice(): Outcome<Unit> = Outcome.Ok(Unit)

        override fun openStorage(): Boolean = true
    }

    private class Nav : AppNavigator {
        val went = mutableListOf<Any>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            went += route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            went += ref
        }

        override fun land() {
            went += "land"
        }

        override fun replaceRoot(root: AppRoute) {
            went += "root"
        }

        override fun back() {
            went += "back"
        }
    }

    private class Project(var createFails: Failure? = null, var openFails: Failure? = null) : FirstProject {
        val made = mutableListOf<String>()
        val opened = mutableListOf<String>()

        override fun available(): Boolean = true

        override suspend fun create(name: String): Outcome<String> {
            made += name
            return createFails?.let { Outcome.Failed(it) } ?: Outcome.Ok("/root/projects/$name")
        }

        override suspend fun open(path: String): Outcome<AppRoute> {
            opened += path
            return openFails?.let { Outcome.Failed(it) } ?: Outcome.Ok(PhoneRoute.ThisPhone)
        }
    }

    @Test
    fun `AUDIT-459 the Ready step names the first project, creates it and opens it`() {
        val nav = Nav()
        val project = Project()
        val vm = SetupJourneyViewModel(PhoneSetupRoute.Setup, Source(), nav, project)
        assertTrue(vm.state.value.canMakeProject)
        assertEquals("my-app", vm.state.value.projectName)
        vm.projectName("  shop  ")
        vm.createProject()
        assertEquals(listOf("shop"), project.made)
        assertEquals(listOf("/root/projects/shop"), project.opened)
        assertEquals(listOf<Any>("back", PhoneRoute.ThisPhone), nav.went)
        assertFalse(vm.state.value.creatingProject)
    }

    @Test
    fun `AUDIT-459 a name that is not one safe folder is refused before anything is made`() {
        val project = Project()
        val vm = SetupJourneyViewModel(PhoneSetupRoute.Setup, Source(), Nav(), project)
        vm.projectName("a/b")
        vm.createProject()
        assertEquals(FirstProjectNameProblem.OneFolder, vm.state.value.nameProblem)
        vm.projectName("")
        vm.createProject()
        assertEquals(FirstProjectNameProblem.Empty, vm.state.value.nameProblem)
        vm.projectName("-x")
        vm.createProject()
        assertEquals(FirstProjectNameProblem.Invalid, vm.state.value.nameProblem)
        assertTrue(project.made.isEmpty())
        assertNull(FirstProjectName.problem(FirstProjectName.SUGGESTED))
    }

    @Test
    fun `AUDIT-459 a failure says which step failed and keeps the person on the step`() {
        val nav = Nav()
        val project = Project(openFails = Failure.Network())
        val vm = SetupJourneyViewModel(PhoneSetupRoute.Setup, Source(), nav, project)
        vm.createProject()
        assertEquals(FirstProjectStep.Open, vm.state.value.projectFailure?.first)
        assertTrue(nav.went.isEmpty())
        project.openFails = null
        project.createFails = Failure.Network()
        vm.createProject()
        assertEquals(FirstProjectStep.Create, vm.state.value.projectFailure?.first)
    }

    @Test
    fun `AUDIT-459 Open a folder instead opens the project picker, and nothing is made before setup is done`() {
        val nav = Nav()
        val project = Project()
        val running = SetupJourneyViewModel(PhoneSetupRoute.Setup, Source(SetupState.Running), nav, project)
        running.createProject()
        assertTrue(project.made.isEmpty())
        running.openFolderInstead()
        assertEquals(listOf<Any>("back", ProjectsRoute.Picker()), nav.went)
    }

    @Test
    fun `AUDIT-460 the ring reads the network and whether the app may say when it is ready`() {
        val facts =
            object : io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupRingFacts {
                override suspend fun transport() = io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupTransport.Mobile

                override fun canNotify() = true
            }
        val vm = SetupJourneyViewModel(PhoneSetupRoute.Setup, Source(SetupState.Running), Nav(), Project(), facts)
        assertEquals(io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupTransport.Mobile, vm.state.value.transport)
        assertTrue(vm.state.value.canNotify)
        val plain = SetupJourneyViewModel(PhoneSetupRoute.Setup, Source(SetupState.Running), Nav())
        assertEquals(io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupTransport.Unknown, plain.state.value.transport)
        assertFalse(plain.state.value.canNotify)
        assertFalse(plain.state.value.canMakeProject)
    }
}
