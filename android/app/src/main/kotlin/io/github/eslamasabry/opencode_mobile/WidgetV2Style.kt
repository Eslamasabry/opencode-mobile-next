package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import org.json.JSONObject

/** Per-widget presentation only. State colours deliberately never use the error/red role. */
object WidgetV2Style {
    private const val RUNNING_DARK = -9185633
    private const val RUNNING_LIGHT = -15242421
    private const val ATTENTION_DARK = -1524381
    private const val ATTENTION_LIGHT = -7642624
    private const val ACCENT_GREEN = -12014481
    private const val ACCENT_AMBER = -4553696
    private const val ACCENT_BLUE = -12482611
    private const val ACCENT_TEAL = -14317693
    private const val LIGHT_TEXT_THRESHOLD = 128
    private const val BACKGROUND_TINT_FRACTION = 0.16f
    private const val HSV_COMPONENT_COUNT = 3

    data class Palette(val background: Int, val text: Int, val muted: Int, val accent: Int) {
        val running: Int = if (Color.red(text) > LIGHT_TEXT_THRESHOLD) RUNNING_DARK else RUNNING_LIGHT
        val attention: Int = if (Color.red(text) > LIGHT_TEXT_THRESHOLD) ATTENTION_DARK else ATTENTION_LIGHT
        val neutral: Int get() = muted
    }

    fun palette(context: Context, widgetId: Int, snapshot: JSONObject?): Palette {
        val style = savedStyle(context, widgetId, snapshot?.optString("profileID").orEmpty())
        val theme = snapshot?.optJSONObject("appTheme")
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val appBackground = themeColor(context, theme, "background", R.color.widgetBackground)
        val appText = themeColor(context, theme, "text", R.color.widgetText)
        val appMuted = themeColor(context, theme, "muted", R.color.widgetHint)
        val appAccent = themeColor(context, theme, "accent", R.color.widgetAccent)
        val wallpaper = style.optString("accent", "wallpaper") == "wallpaper" &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val accent = accent(context, style, dark, wallpaper, appAccent)
        val base = if (wallpaper) context.getColor(
            if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50
        ) else appBackground
        val background = when (style.optString("background", "solid")) {
            "transparent" -> Color.TRANSPARENT
            "tinted" -> blend(base, accent, BACKGROUND_TINT_FRACTION)
            else -> base
        }
        return Palette(background, if (wallpaper) context.getColor(
            if (dark) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900
        ) else appText, if (wallpaper) context.getColor(
            if (dark) android.R.color.system_neutral1_300 else android.R.color.system_neutral1_700
        ) else appMuted, accent)
    }

    /** The saved background choice: solid, tinted or transparent. */
    fun backgroundMode(context: Context, widgetId: Int, snapshot: JSONObject?): String =
        savedStyle(context, widgetId, snapshot?.optString("profileID").orEmpty()).optString("background", "solid")

    /** The project a Conversations widget lists, "" for all projects (its Edit). */
    fun project(context: Context, widgetId: Int, snapshot: JSONObject?): String =
        savedStyle(context, widgetId, snapshot?.optString("profileID").orEmpty()).optString("project")

    private fun themeColor(context: Context, theme: JSONObject?, key: String, fallback: Int): Int =
        theme?.optLong(key)?.takeIf { it != 0L }?.toInt() ?: context.getColor(fallback)

    private fun savedStyle(context: Context, widgetId: Int, profile: String): JSONObject {
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        fun saved(key: String): JSONObject? = try {
            JSONObject(prefs.getString("flutter.oc.widgetStyle.$key", "{}") ?: "{}")
                .optJSONObject(widgetId.toString())
        } catch (_: Exception) { null }
        // A widget styled before the app published its first snapshot keeps
        // that style under the device key until its profile saves one.
        return (if (profile.isNotBlank()) saved(profile) else null)
            ?: saved(WidgetV2Bridge.DEVICE_STYLE_KEY) ?: JSONObject()
    }

    private fun accent(
        context: Context,
        style: JSONObject,
        dark: Boolean,
        wallpaper: Boolean,
        appAccent: Int
    ): Int {
        return when (style.optString("accent", "wallpaper")) {
            "green" -> ACCENT_GREEN
            "amber" -> ACCENT_AMBER
            "blue" -> ACCENT_BLUE
            "teal" -> ACCENT_TEAL
            "wallpaper" -> if (wallpaper) context.getColor(
                if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_700
            ) else appAccent
            else -> appAccent
        }.let { normalAccent(it, dark) }
    }

    fun apply(context: Context, views: RemoteViews, widgetId: Int, snapshot: JSONObject? = null) {
        val palette = palette(context, widgetId, snapshot)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(
                android.R.id.background, "setBackgroundTintList", ColorStateList.valueOf(palette.background)
            )
        } else {
            views.setInt(android.R.id.background, "setBackgroundColor", palette.background)
        }
    }

    private fun normalAccent(color: Int, dark: Boolean): Int {
        val hsv = FloatArray(HSV_COMPONENT_COUNT)
        Color.colorToHSV(color, hsv)
        // Wallpaper and saved accents retain their style, while semantic red
        // and amber colours are mapped to the readable running role.
        return if (WidgetUsageStyle.reservedStatusHue(hsv[0], hsv[1]))
            if (dark) RUNNING_DARK else RUNNING_LIGHT else color
    }

    private fun blend(a: Int, b: Int, fraction: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * fraction).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * fraction).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * fraction).toInt()
    )
}
