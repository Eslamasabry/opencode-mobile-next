package io.github.eslamasabry.oc.ui.kit.platform

public class DesktopKitPlatform : KitPlatform {
    override val name: String = "desktop"

    override fun haptic(kind: KitHaptic): Unit = Unit // desktops have no haptics
}
