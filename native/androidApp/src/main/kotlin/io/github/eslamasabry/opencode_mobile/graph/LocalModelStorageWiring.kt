package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.text.format.Formatter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.feature.settings.presentation.LocalModelStorage
import io.github.eslamasabry.oc.feature.settings.presentation.LocalModelStorageFacts
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** S02-3: deleting retained Gemma data stays available while the runtime is deferred. */
@SingleIn(AppScope::class)
@Inject
class AndroidLocalModelStorage(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
) : LocalModelStorage {
    private val state = MutableStateFlow(LocalModelStorageFacts())
    override val facts = state.asStateFlow()
    private val lane = Mutex()
    private val root: Path get() = app.filesDir.toPath().resolve("local_llm_models")

    override suspend fun refresh(): Outcome<Unit> = lane.withLock {
        when (val ready = stores.warm()) {
            is Outcome.Failed -> {
                state.value = state.value.copy(loading = false, failed = true)
                return@withLock ready
            }
            is Outcome.Ok -> Unit
        }
        state.value = state.value.copy(loading = true, failed = false)
        read()
    }

    override suspend fun delete(): Outcome<Unit> = lane.withLock {
        if (!state.value.present || state.value.loading || state.value.deleting) {
            return@withLock Outcome.Failed(Failure.StorageRefused())
        }
        state.value = state.value.copy(deleting = true, failed = false)
        val removed = withContext(dispatchers.io) {
            attempt({ Failure.StorageRefused() }) {
                checkRoot()
                if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            // No FOLLOW_LINKS: a nested link itself is removed, never its destination.
                            Files.delete(file)
                            return FileVisitResult.CONTINUE
                        }

                        override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                            if (error != null) throw error
                            Files.delete(dir)
                            return FileVisitResult.CONTINUE
                        }
                    })
                }
            }
        }
        if (removed is Outcome.Failed) {
            state.value = state.value.copy(deleting = false, failed = true)
            return@withLock removed
        }
        read()
    }

    private suspend fun read(): Outcome<Unit> {
        val measured = withContext(dispatchers.io) {
            attempt({ Failure.StorageRefused() }) {
                checkRoot()
                val present = Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                var bytes = 0L
                if (present) {
                    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            if (attrs.isRegularFile) {
                                val size = attrs.size().coerceAtLeast(0)
                                bytes = if (bytes > Long.MAX_VALUE - size) Long.MAX_VALUE else bytes + size
                            }
                            return FileVisitResult.CONTINUE
                        }
                    })
                }
                LocalModelStorageFacts(
                    loading = false,
                    present = present,
                    bytes = bytes,
                    size = Formatter.formatFileSize(AppLocale.wrap(app), bytes),
                )
            }
        }
        return when (measured) {
            is Outcome.Ok -> {
                state.value = measured.value
                Outcome.Ok(Unit)
            }
            is Outcome.Failed -> {
                state.value = state.value.copy(loading = false, deleting = false, failed = true)
                measured
            }
        }
    }

    private fun checkRoot() {
        if (Files.isSymbolicLink(root) ||
            (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
        ) throw IOException("storage unavailable")
    }
}
