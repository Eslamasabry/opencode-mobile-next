package io.github.eslamasabry.oc.feature.files

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.FileGateway
import io.github.eslamasabry.oc.core.model.FileContent
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.core.model.FindMatch
import io.github.eslamasabry.oc.feature.files.presentation.ComposerMentions
import io.github.eslamasabry.oc.feature.files.presentation.FilesConnections
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal val conversation = ConversationRef(ProfileId("p1"), SessionId("s1"))

internal class FakeFileGateway(
    var nodes: Map<String, List<FileNode>> = emptyMap(),
    var contents: Map<String, FileContent> = emptyMap(),
    var found: List<String> = emptyList(),
) : FileGateway {
    val listed = mutableListOf<String>()
    val found_ = mutableListOf<String>()

    override suspend fun listFiles(path: String): Outcome<ImmutableList<FileNode>> {
        listed += path
        return nodes[path]?.let { Outcome.Ok(it.toImmutableList()) } ?: Outcome.Failed(Failure.Server(500))
    }

    override suspend fun fileContent(path: String): Outcome<FileContent> =
        contents[path]?.let { Outcome.Ok(it) } ?: Outcome.Failed(Failure.NotFound())

    override suspend fun findFile(query: String): Outcome<ImmutableList<String>> {
        found_ += query
        return Outcome.Ok(found.toImmutableList())
    }

    override suspend fun findText(pattern: String): Outcome<ImmutableList<FindMatch>> =
        Outcome.Failed(Failure.Unavailable(FeatureId("textSearch")))
}

internal class FakeConnections(
    var gateway: FileGateway? = FakeFileGateway(),
    var browse: Boolean = true,
    var dir: String? = "/work/app",
) : FilesConnections {
    override fun gateway(ref: ConversationRef): FileGateway? = gateway

    override fun canBrowse(ref: ConversationRef): Boolean = browse

    override suspend fun directory(ref: ConversationRef): String? = dir
}

internal class FakeMentions(var result: Outcome<Unit> = Outcome.Ok(Unit)) : ComposerMentions {
    val calls = mutableListOf<Triple<ConversationRef, String, String?>>()

    override suspend fun mention(ref: ConversationRef, path: String, directory: String?): Outcome<Unit> {
        calls += Triple(ref, path, directory)
        return result
    }
}

/** A real back stack: open pushes, back pops (never the root). */
internal class StackNavigator(vararg initial: AppRoute) : AppNavigator {
    val stack = initial.toMutableList()
    private val topFlow = MutableStateFlow<AppRoute?>(stack.lastOrNull())
    override val top: StateFlow<AppRoute?> = topFlow

    private fun sync() {
        topFlow.value = stack.lastOrNull()
    }

    override fun open(route: AppRoute) {
        if (stack.lastOrNull() != route) stack += route
        sync()
    }

    override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
        open(ChatRoute(ref, focusComposer))
    }

    override fun land() {}

    override fun replaceRoot(root: AppRoute) {
        stack.clear()
        stack += root
        sync()
    }

    override fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        sync()
    }
}
