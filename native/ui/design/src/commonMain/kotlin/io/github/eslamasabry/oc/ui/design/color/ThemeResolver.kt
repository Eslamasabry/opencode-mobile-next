package io.github.eslamasabry.oc.ui.design.color

/**
 * The roles a screen paints with, from the stored choices (plan 06 §5.1 step 2; Dart `main.dart` + `AppTheme.fromPalette`):
 * the pack's palette for the brightness, then the Accent swatch as the partner (`withPartner`), then Saver. Saver is
 * always the pack's dark palette on true black, with the swatch's dark colour. Styles other than Solid
 * (`adjustRoles`) join in slice 4.
 */
public object ThemeResolver {
    public fun roles(pack: PackId, accentId: String?, dark: Boolean, saver: Boolean, dynamic: DynamicSource?): Roles {
        val isDark = dark || saver
        var roles = Packs.of(pack, dynamic).palette(isDark).roles
        Accents.byId(accentId)?.let { roles = roles.withPartner(it.of(isDark)) }
        return if (saver) roles.saver else roles
    }
}
