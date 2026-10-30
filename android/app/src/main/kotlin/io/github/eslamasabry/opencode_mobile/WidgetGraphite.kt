package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import org.json.JSONObject

/** The conversations widget's colours: the app's Graphite roles (lib/ui/theme_roles.dart), dark
 * or light. Mint is the one main action and a working turn, amber is needs-you; red ([bad]) is used
 * only by the Status widget, for a server that is offline or a plan that is used up.
 * The drawables carry the hairline edges a tint would flatten. */
internal data class WidgetGraphite(
    val dark: Boolean,
    val card: Int,
    val pill: Int,
    val text: Int,
    val muted: Int,
    val subtle: Int,
    val lead: Int,
    val green: Int,
    val amber: Int,
    val idle: Int,
    val divider: Int,
    val primary: Int,
    val onPrimary: Int,
    val onGreen: Int,
    val cardBg: Int,
    val tileBg: Int,
    val mintBg: Int,
    val outlineBg: Int,
    val fieldBg: Int,
    /** Something is wrong (a server offline, a plan used up): the Status widget only. */
    val bad: Int = 0,
    val amberBg: Int = 0,
    val onAmber: Int = 0,
    val pillBg: Int = 0,
    val codeBg: Int = 0,
) {
    /** The card's ground for the widget's saved style: the Graphite card with its hairline, a
     * faint mint tint, or nothing (the wallpaper shows through). */
    fun paintGround(views: RemoteViews, mode: String) {
        when (mode) {
            "transparent" -> views.setInt(android.R.id.background, "setBackgroundResource", 0)
            "tinted" -> {
                views.setInt(android.R.id.background, "setBackgroundResource", cardBg)
                tint(views, android.R.id.background, Color.rgb(
                    mix(Color.red(card), Color.red(green)), mix(Color.green(card), Color.green(green)),
                    mix(Color.blue(card), Color.blue(green))))
            }
            else -> views.setInt(android.R.id.background, "setBackgroundResource", cardBg)
        }
    }

    private fun mix(a: Int, b: Int): Int = (a + (b - a) * TINT_BLEND).toInt()

    companion object {
        private const val TINT_BLEND = 0.08f

        // surface1, surface3, text1, text3, text2, text2, accent, attention, text3, hairline,
        // accent (Allow once), onAccent, onAccent.
        private val DARK = WidgetGraphite(true, 0xFF141518.toInt(), 0xFF26282D.toInt(), 0xFFF3F3F1.toInt(),
            0xFF8C8F96.toInt(), 0xFFA3A5AB.toInt(), 0xFFA3A5AB.toInt(), 0xFF3DDC8A.toInt(), 0xFFFFB547.toInt(),
            0xFF8C8F96.toInt(), 0x1AFFFFFF, 0xFF3DDC8A.toInt(), 0xFF03140B.toInt(), 0xFF03140B.toInt(),
            R.drawable.widget_card_dark, R.drawable.widget_tile_dark, R.drawable.widget_btn_mint_dark,
            R.drawable.widget_btn_outline_dark, R.drawable.widget_field_dark,
            0xFFFF7A6B.toInt(), R.drawable.widget_btn_amber_dark, 0xFF1A1300.toInt(),
            R.drawable.widget_pill_dark, R.drawable.widget_code_dark)
        private val LIGHT = WidgetGraphite(false, 0xFFFFFFFF.toInt(), 0xFFE9E9E6.toInt(), 0xFF111214.toInt(),
            0xFF62656C.toInt(), 0xFF50535A.toInt(), 0xFF50535A.toInt(), 0xFF087F43.toInt(), 0xFF9A5700.toInt(),
            0xFF62656C.toInt(), 0x14000000, 0xFF087F43.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
            R.drawable.widget_card_light, R.drawable.widget_tile_light, R.drawable.widget_btn_mint_light,
            R.drawable.widget_btn_outline_light, R.drawable.widget_field_light,
            0xFFC0392B.toInt(), R.drawable.widget_btn_amber_light, 0xFF1A1300.toInt(),
            R.drawable.widget_pill_light, R.drawable.widget_code_light)

        /** The app's own appearance when it published one, else the system's. */
        fun of(context: Context, snapshot: JSONObject?): WidgetGraphite {
            val appBackground = snapshot?.optJSONObject("appTheme")?.optLong("background")?.takeIf { it != 0L }?.toInt()
            val dark = if (appBackground != null) isDark(appBackground) else
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            return if (dark) DARK else LIGHT
        }

        private const val LUMA_RED = 0.299
        private const val LUMA_GREEN = 0.587
        private const val LUMA_BLUE = 0.114
        private const val DARK_LUMA_BELOW = 128

        fun isDark(background: Int): Boolean {
            val luma = LUMA_RED * Color.red(background) + LUMA_GREEN * Color.green(background) +
                LUMA_BLUE * Color.blue(background)
            return luma < DARK_LUMA_BELOW
        }

        /** Tint a pill or circle background. Android 12+ tints the drawable; older Android
         * has only a flat fill, which still reads as a button. */
        fun tint(views: RemoteViews, id: Int, color: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(id, "setBackgroundTintList", ColorStateList.valueOf(color))
            } else {
                views.setInt(id, "setBackgroundColor", color)
            }
        }
    }
}
