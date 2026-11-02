package io.github.eslamasabry.oc.feature.linux.ui

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobKind
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxJobNotices
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobBackingUp
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobNoticeInstall
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobNoticeRemove
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobNoticeUpdate
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobRestoring
import org.jetbrains.compose.resources.getString

/** The long job's foreground notice in the person's language (Flutter `linuxMgrJobNotice*`). */
@Inject
@ContributesBinding(AppScope::class)
public class LinuxJobNoticeWords : LinuxJobNotices {
    override suspend fun notice(kind: LinuxJobKind): String =
        getString(
            when (kind) {
                LinuxJobKind.Install -> Res.string.linuxMgrJobNoticeInstall
                LinuxJobKind.Remove -> Res.string.linuxMgrJobNoticeRemove
                LinuxJobKind.Update -> Res.string.linuxMgrJobNoticeUpdate
                LinuxJobKind.Backup -> Res.string.linuxMgrJobBackingUp
                LinuxJobKind.Restore -> Res.string.linuxMgrJobRestoring
            },
        )
}
