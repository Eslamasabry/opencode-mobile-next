package io.github.eslamasabry.oc.android.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.platform.DocumentTarget
import io.github.eslamasabry.oc.core.domain.platform.PhotoTarget
import io.github.eslamasabry.oc.core.domain.platform.TreeTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * `AndroidActivityResults` (03 §10.4, plan 10 §10.4): fixed-key launchers registered on MainActivity, one owner per
 * result, and results that arrive after process death go to the recovery sink only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidActivityResultsTest {
    private class Launch(val requestCode: Int, val contract: ActivityResultContract<*, *>, val input: Any?)

    /** The real registry with the system call replaced: records launches; results are dispatched by the test. */
    private class FakeRegistry : ActivityResultRegistry() {
        val launched = mutableListOf<Launch>()
        var throwOnLaunch: RuntimeException? = null

        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            throwOnLaunch?.let { throw it }
            launched += Launch(requestCode, contract, input)
        }

        fun registeredKeys(): List<String> =
            Bundle().also(::onSaveInstanceState).getStringArrayList("KEY_COMPONENT_ACTIVITY_REGISTERED_KEYS").orEmpty()
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private class TestDispatchers(d: CoroutineDispatcher) : AppDispatchers {
        override val main = d
        override val default = d
        override val io = d

        override fun serial(name: String) = main
    }

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private class Rig(scope: TestScope, registry: FakeRegistry = FakeRegistry(), val orphans: MutableList<LauncherOutcome> = mutableListOf()) {
        val registry = registry
        val owner = Owner()
        val results = AndroidActivityResults(TestDispatchers(UnconfinedTestDispatcher(scope.testScheduler))) { orphans += it }
    }

    private fun TestScope.attached(registry: FakeRegistry = FakeRegistry()): Rig =
        Rig(this, registry).also {
            it.results.attach(activity, it.registry, it.owner)
            it.owner.registry.currentState = Lifecycle.State.RESUMED
        }

    private val doc = Uri.parse("content://com.example.docs/document/42")

    @Test
    fun `S2-PLATFORM attach registers the fixed-key launchers on MainActivity and exposes it as the current activity`() =
        runTest {
            val rig = attached()
            assertEquals(AndroidActivityResults.KEYS.toSet(), rig.registry.registeredKeys().toSet())
            assertEquals(
                setOf("oc.pickPhoto", "oc.pickPhotos", "oc.takePhoto", "oc.openDocument", "oc.createDocument", "oc.openDocumentTree"),
                AndroidActivityResults.KEYS.toSet(),
            )
            assertSame(activity, rig.results.get())

            rig.owner.registry.currentState = Lifecycle.State.DESTROYED
            assertNull(rig.results.get()) // never a leaked or destroyed activity
            assertTrue(rig.registry.registeredKeys().isEmpty())
        }

    @Test
    fun `S2-PLATFORM without an attached activity every request fails plainly and nothing launches`() =
        runTest {
            val results = AndroidActivityResults(TestDispatchers(UnconfinedTestDispatcher(testScheduler))) {}
            assertIs<Failure.Device>((results.openDocument(listOf("*/*")) as Outcome.Failed).failure)
            assertIs<Outcome.Failed>(results.pickImages(3))
        }

    @Test
    fun `S2-PLATFORM openDocument resumes the waiting caller with the picked file and cancel answers null`() =
        runTest {
            val rig = attached()
            val call = async { rig.results.openDocument(listOf("text/plain", "application/pdf")) }
            runCurrent()
            val launch = rig.registry.launched.single()
            assertIs<ActivityResultContracts.OpenDocument>(launch.contract)
            assertTrue((launch.input as Array<*>).contentEquals(arrayOf("text/plain", "application/pdf")))
            rig.registry.dispatchResult(launch.requestCode, doc)
            assertEquals(doc.toString(), (call.await() as Outcome.Ok).value?.uri)

            val cancelled = async { rig.results.openDocument(listOf("*/*")) }
            runCurrent()
            rig.registry.dispatchResult(rig.registry.launched.last().requestCode, null)
            assertEquals(Outcome.Ok(null), cancelled.await())
        }

    @Test
    fun `B-MEDIA-007 the gallery allows several up to the room left, one slot left uses the single picker, cancel leaves nothing`() =
        runTest {
            val rig = attached()
            val several = async { rig.results.pickImages(limit = 3) }
            runCurrent()
            val multi = rig.registry.launched.last()
            assertIs<AndroidActivityResults.PickImages>(multi.contract)
            assertEquals(3, multi.input)
            val intent = (multi.contract as AndroidActivityResults.PickImages).createIntent(activity, 3)
            assertEquals(3, intent.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, -1))
            val picked = (1..5).map { Uri.parse("content://media/picker/0/$it") }
            rig.registry.dispatchResult(multi.requestCode, picked)
            assertEquals(3, (several.await() as Outcome.Ok).value.size) // never more than the room left

            val one = async { rig.results.pickImages(limit = 1) }
            runCurrent()
            val single = rig.registry.launched.last()
            assertIs<ActivityResultContracts.PickVisualMedia>(single.contract)
            rig.registry.dispatchResult(single.requestCode, null)
            assertEquals(0, (one.await() as Outcome.Ok).value.size)

            assertEquals(0, (rig.results.pickImages(limit = 0) as Outcome.Ok).value.size)
            assertEquals(2, rig.registry.launched.size) // no room, no picker
        }

    @Test
    fun `B-MEDIA-008 a pending request blocks another picker - one owner per result`() =
        runTest {
            val rig = attached()
            val first = async { rig.results.takePhoto(PhotoTarget("content://${activity.packageName}.localfiles/camera/1.jpg")) }
            runCurrent()
            assertIs<Outcome.Failed>(rig.results.takePhoto(PhotoTarget("content://${activity.packageName}.localfiles/camera/2.jpg")))
            assertEquals(1, rig.registry.launched.size)
            rig.registry.dispatchResult(rig.registry.launched.single().requestCode, false)
            assertEquals(Outcome.Ok(null), first.await()) // camera cancelled
        }

    @Test
    fun `B-MEDIA-008 the camera writes only to an app FileProvider target and a captured photo resumes the caller`() =
        runTest {
            val rig = attached()
            assertIs<Outcome.Failed>(rig.results.takePhoto(PhotoTarget("file:///sdcard/x.jpg")))
            assertTrue(rig.registry.launched.isEmpty())

            val target = "content://${activity.packageName}.localfiles/camera/7.jpg"
            val call = async { rig.results.takePhoto(PhotoTarget(target)) }
            runCurrent()
            val launch = rig.registry.launched.single()
            assertIs<ActivityResultContracts.TakePicture>(launch.contract)
            assertEquals(Uri.parse(target), launch.input)
            rig.registry.dispatchResult(launch.requestCode, true)
            assertEquals(target, (call.await() as Outcome.Ok).value?.uri)
        }

    @Test
    fun `B-MEDIA-008 a result that arrives after process death only reports the outcome to recovery`() =
        runTest {
            // Process 1: the camera opens, then the process dies; the registry state is saved with the activity.
            val before = attached()
            val dying = async { before.results.takePhoto(PhotoTarget("content://${activity.packageName}.localfiles/camera/9.jpg")) }
            runCurrent()
            val code = before.registry.launched.single().requestCode
            val saved = Bundle().also(before.registry::onSaveInstanceState)

            // Process 2: a new graph (nobody waiting) attaches to the restored registry; the camera result arrives.
            val restored = FakeRegistry().also { it.onRestoreInstanceState(saved) }
            val after = Rig(this, restored)
            after.results.attach(activity, restored, after.owner)
            after.owner.registry.currentState = Lifecycle.State.RESUMED
            restored.dispatchResult(code, true)
            assertEquals(listOf<LauncherOutcome>(LauncherOutcome.Captured), after.orphans)
            dying.cancel() // process 1 is gone
        }

    @Test
    fun `S2-PLATFORM createDocument names the file and type, openDocumentTree returns the tree`() =
        runTest {
            val rig = attached()
            val create = async { rig.results.createDocument("session.json", "application/json") }
            runCurrent()
            val launch = rig.registry.launched.last()
            val intent = (launch.contract as AndroidActivityResults.CreateDocument).createIntent(activity, launch.input as AndroidActivityResults.CreateRequest)
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
            assertEquals("application/json", intent.type)
            assertEquals("session.json", intent.getStringExtra(Intent.EXTRA_TITLE))
            assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
            rig.registry.dispatchResult(launch.requestCode, doc)
            assertEquals(Outcome.Ok(DocumentTarget(doc.toString(), "session.json")), create.await())

            val tree = async { rig.results.openDocumentTree() }
            runCurrent()
            val treeUri = Uri.parse("content://com.example.docs/tree/primary%3AProjects")
            rig.registry.dispatchResult(rig.registry.launched.last().requestCode, treeUri)
            assertEquals(Outcome.Ok(TreeTarget(treeUri.toString(), null)), tree.await())
        }

    @Test
    fun `S2-PLATFORM a launch the system refuses fails plainly and frees the slot`() =
        runTest {
            val registry = FakeRegistry().apply { throwOnLaunch = android.content.ActivityNotFoundException() }
            val rig = attached(registry)
            assertIs<Failure.Device>((rig.results.openDocument(listOf("*/*")) as Outcome.Failed).failure)
            registry.throwOnLaunch = null
            val again = async { rig.results.openDocument(listOf("*/*")) }
            runCurrent()
            rig.registry.dispatchResult(rig.registry.launched.single().requestCode, null)
            assertEquals(Outcome.Ok(null), again.await())
        }
}
