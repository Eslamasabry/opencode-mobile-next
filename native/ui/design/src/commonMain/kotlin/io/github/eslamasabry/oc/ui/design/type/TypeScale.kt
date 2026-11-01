package io.github.eslamasabry.oc.ui.design.type

/** One role's spec. Sizes in sp, integers only (LOOK-13). */
public data class TypeSpec(
    val sizeSp: Int,
    val lineHeightSp: Int,
    val weight: Int,
    val trackingSp: Float,
    val family: TypeFamily = TypeFamily.Sans,
)

/**
 * The firm Solid type scale: the code values after the 2026-10-30 pass (`KitText.styleFor`, `kit_text.dart` lines
 * 279–345). Docs that say 32/24/17 or weight 650 are stale. Other styles' firm weights come with the Style resolver.
 */
public object TypeScale {
    public fun of(role: TypeRole): TypeSpec =
        when (role) {
            TypeRole.LargeTitle -> TypeSpec(28, 34, 500, -0.28f)
            TypeRole.Title -> TypeSpec(22, 28, 500, 0f)
            TypeRole.Headline -> TypeSpec(16, 22, 500, -0.17f)
            TypeRole.Body -> TypeSpec(16, 24, 400, 0f)
            TypeRole.RowTitle -> TypeSpec(16, 22, 500, 0f)
            TypeRole.Secondary -> TypeSpec(13, 18, 400, 0f)
            TypeRole.Label -> TypeSpec(13, 18, 500, 0f)
            TypeRole.Caption -> TypeSpec(13, 18, 500, 0f)
            TypeRole.Button -> TypeSpec(16, 20, 500, 0f)
            TypeRole.Mono -> TypeSpec(13, 19, 400, 0f, TypeFamily.Mono)
        }

    /** Arabic (Dart `AppTheme.forLocale`): the system sans, tracking 0 on every role. Mono stays Geist Mono. */
    public fun forLocale(role: TypeRole, arabic: Boolean): TypeSpec =
        if (arabic) of(role).copy(trackingSp = 0f) else of(role)

    /** Weights the variable fonts are instantiated at (plan 06 §4.4 `rememberKitFonts`). */
    public val sansWeights: IntArray = intArrayOf(400, 500, 600, 650, 700, 750, 800)
    public val monoWeights: IntArray = intArrayOf(400, 500, 600)
}
