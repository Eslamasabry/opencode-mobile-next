package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.DeletionStep
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.ui.kit.image.KitFileImageDisk
import io.github.eslamasabry.oc.ui.kit.image.KitImages
import kotlinx.coroutines.withContext

/**
 * Forgetting a server removes its picture thumbnails (`<cache>/oc-images/oc.imageCache.<profileId>/`) and empties the
 * in-memory picture cache, which holds no profile tag and could still show the forgotten server's pictures.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
class ImageCacheCleaner(private val application: Application, private val dispatchers: AppDispatchers) :
    ProfileResourceCleaner {
    override val owner: String = ":ui:kit (picture thumbnails)"
    override val order: Int = DeletionStep.DEVICE_SURFACES

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> {
        KitImages.clearMemory()
        val folder = KitFileImageDisk.folder(application.cacheDir, profileId.raw)
        val had = folder.exists()
        val gone = withContext(dispatchers.io) { KitFileImageDisk.clear(application.cacheDir, profileId.raw) }
        return if (gone) Outcome.Ok(if (had) 1 else 0) else Outcome.Failed(Failure.StorageRefused())
    }
}
