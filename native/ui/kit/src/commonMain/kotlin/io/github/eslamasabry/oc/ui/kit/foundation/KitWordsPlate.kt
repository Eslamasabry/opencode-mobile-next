package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The words plate (Dart `KitWordsPlate.wrap`, `kit_words_plate.dart` at GLASS_REF 1bbdc3f9c): words on the bare page
 * over a picture sit on a measured plate of the ground.
 *
 * Under Glass at GLASS_REF no plate is ever drawn, so this stays the words alone: Flutter asks for plates only over a
 * vivid wash (`plate: backdrop == null ? null : ...`, `kit_live_background.dart:287-329`), and Glass has no vivid wash
 * since the aurora was toned down (`kit_style_glass.dart:38`, owner 2026-10-26). The painted aurora is a tint of the
 * page's own ground at 12-14 % of the picture's colour, so words read on it as on the plain ground with no plate and no
 * halo (`kit_live_aurora_image.dart:27-32`). `KitText.kt` may apply it to every line of words.
 */
@Composable
internal fun Modifier.kitWordsPlate(): Modifier = this
