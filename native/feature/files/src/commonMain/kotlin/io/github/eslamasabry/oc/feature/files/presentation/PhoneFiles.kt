package io.github.eslamasabry.oc.feature.files.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef

/**
 * Only the saved built-in phone owner can turn a server path into this phone's shared-storage file.
 */
public interface PhoneFiles {
    public suspend fun available(ref: ConversationRef, path: String): Boolean

    public suspend fun open(ref: ConversationRef, path: String, install: Boolean): Outcome<Unit>
}

/** Permission checks happen only for a project the person opened in shared storage. */
public interface ProjectStorageAccess {
    public suspend fun blocked(ref: ConversationRef): Boolean

    public suspend fun request(ref: ConversationRef): Outcome<Unit>
}

@ContributesTo(AppScope::class)
public interface PhoneFilesBindings {
    @Multibinds(allowEmpty = true) public val phoneFiles: Set<PhoneFiles>
    @Multibinds(allowEmpty = true) public val projectStorageAccess: Set<ProjectStorageAccess>
}
