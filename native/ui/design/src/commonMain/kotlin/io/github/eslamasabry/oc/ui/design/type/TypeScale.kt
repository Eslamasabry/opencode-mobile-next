package io.github.eslamasabry.oc.ui.design.type

import io.github.eslamasabry.oc.ui.design.generated.FlutterKitType

/** One role's spec. Sizes in sp, integers only (LOOK-13). */
public data class TypeSpec(
    val sizeSp: Int,
    val lineHeightSp: Int,
    val weight: Int,
    val trackingSp: Float,
    val family: TypeFamily = TypeFamily.Sans,
)

/**
 * The Solid type scale: Dart `KitText.styleFor` (`lib/ui/kit/kit_text.dart`), read from the generated [FlutterKitType]
 * (native/tool/design/gen_tokens.py). Other styles' firm weights come with the Style resolver.
 */
public object TypeScale {
    public fun of(role: TypeRole): TypeSpec =
        when (role) {
            TypeRole.LargeTitle -> FlutterKitType.LargeTitle
            TypeRole.Title -> FlutterKitType.Title
            TypeRole.Headline -> FlutterKitType.Headline
            TypeRole.Body -> FlutterKitType.Body
            TypeRole.RowTitle -> FlutterKitType.RowTitle
            TypeRole.Secondary -> FlutterKitType.Secondary
            TypeRole.Label -> FlutterKitType.Label
            TypeRole.Caption -> FlutterKitType.Caption
            TypeRole.Button -> FlutterKitType.Button
            TypeRole.Mono -> FlutterKitType.Mono
        }

    /** Arabic (Dart `AppTheme.forLocale`): the system sans, tracking 0 on every role. Mono stays Geist Mono. */
    public fun forLocale(role: TypeRole, arabic: Boolean): TypeSpec =
        if (arabic) of(role).copy(trackingSp = 0f) else of(role)

    /** Weights the variable fonts are instantiated at (plan 06 §4.4 `rememberKitFonts`). */
    public val sansWeights: IntArray = intArrayOf(400, 500, 600, 650, 700, 750, 800)
    public val monoWeights: IntArray = intArrayOf(400, 500, 600)
}
