package com.example.ddlreminder.data

import android.content.Context
import com.example.ddlreminder.util.RenderCompat

/**
 * Small persistent store for settings that do not belong in Room.
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_USER_NAME
        set(value) {
            prefs.edit().putString(KEY_USER_NAME, value.trim()).apply()
        }

    var lastListId: Long
        get() = prefs.getLong(KEY_LAST_LIST_ID, NO_LIST)
        set(value) {
            prefs.edit().putLong(KEY_LAST_LIST_ID, value).apply()
        }

    /**
     * Persisted content:// uri of the user selected background picture.
     * `null` means the default gradient background is used.
     */
    var backgroundUri: String?
        get() = prefs.getString(KEY_BACKGROUND_URI, null)
        set(value) {
            prefs.edit().putString(KEY_BACKGROUND_URI, value).apply()
        }

    /**
     * Opacity (0..1) of the tint that fills the glass cards. A smaller value
     * leaves more of the blurred backdrop visible, i.e. a more transparent glass.
     */
    var glassFillAlpha: Float
        get() = prefs.getFloat(KEY_GLASS_FILL_ALPHA, DEFAULT_GLASS_FILL_ALPHA)
            .coerceIn(MIN_GLASS_FILL_ALPHA, MAX_GLASS_FILL_ALPHA)
        set(value) {
            prefs.edit()
                .putFloat(
                    KEY_GLASS_FILL_ALPHA,
                    value.coerceIn(MIN_GLASS_FILL_ALPHA, MAX_GLASS_FILL_ALPHA)
                )
                .apply()
        }

    /**
     * Rendering scheme of the rounded task cards: a plain solid fill, the
     * original backdrop sampling glass, or the WinUI style glass. The initial
     * value follows the HarmonyOS fallback detection.
     */
    var renderScheme: RenderScheme
        get() = RenderScheme.fromStorage(
            prefs.getInt(KEY_RENDER_SCHEME, defaultRenderScheme().storageValue)
        )
        set(value) {
            prefs.edit().putInt(KEY_RENDER_SCHEME, value.storageValue).apply()
        }

    /**
     * Legacy two state view of [renderScheme], kept for older call sites:
     * solid is the "compatible" rendering, liquid the sampled glass.
     */
    var glassCompatMode: Boolean
        get() = renderScheme == RenderScheme.Solid
        set(value) {
            renderScheme = if (value) RenderScheme.Solid else RenderScheme.Liquid
        }

    private fun defaultRenderScheme(): RenderScheme =
        if (RenderCompat.shouldStartWithCompatRendering()) {
            RenderScheme.Solid
        } else {
            RenderScheme.Liquid
        }

    /**
     * Relative font scale (0.6..1.0) of the "xxdayxxhourxxmin后将从表中移除"
     * marker on overdue task cards. 1.0 == the normal label size.
     */
    var overdueTextScale: Float
        get() = prefs.getFloat(KEY_OVERDUE_TEXT_SCALE, DEFAULT_OVERDUE_TEXT_SCALE)
            .coerceIn(MIN_OVERDUE_TEXT_SCALE, MAX_OVERDUE_TEXT_SCALE)
        set(value) {
            prefs.edit()
                .putFloat(
                    KEY_OVERDUE_TEXT_SCALE,
                    value.coerceIn(MIN_OVERDUE_TEXT_SCALE, MAX_OVERDUE_TEXT_SCALE)
                )
                .apply()
        }

    /**
     * `true` keeps the tasks that the automatic 24h/48h cleanup removes in the
     * recycle bin instead of deleting them right away. Off by default.
     */
    var keepAutoDeletedInTrash: Boolean
        get() = prefs.getBoolean(KEY_KEEP_AUTO_DELETED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_KEEP_AUTO_DELETED, value).apply()
        }

    /**
     * Days a deleted task stays in the recycle bin, counted from the moment it
     * was deleted. Defaults to one week; the UI caps it at 30 days.
     */
    var trashRetentionDays: Int
        get() = prefs.getInt(KEY_TRASH_RETENTION_DAYS, DEFAULT_TRASH_RETENTION_DAYS)
            .coerceIn(MIN_TRASH_RETENTION_DAYS, MAX_TRASH_RETENTION_DAYS)
        set(value) {
            prefs.edit()
                .putInt(
                    KEY_TRASH_RETENTION_DAYS,
                    value.coerceIn(MIN_TRASH_RETENTION_DAYS, MAX_TRASH_RETENTION_DAYS)
                )
                .apply()
        }

    /**
     * Hours a task that was already more than 48h overdue and is then restored
     * from the recycle bin stays in the list. Applies only to those tasks.
     */
    var trashRestoreRetentionHours: Int
        get() = prefs.getInt(
            KEY_TRASH_RESTORE_RETENTION_HOURS,
            DEFAULT_TRASH_RESTORE_RETENTION_HOURS
        ).coerceIn(MIN_TRASH_RESTORE_RETENTION_HOURS, MAX_TRASH_RESTORE_RETENTION_HOURS)
        set(value) {
            prefs.edit()
                .putInt(
                    KEY_TRASH_RESTORE_RETENTION_HOURS,
                    value.coerceIn(
                        MIN_TRASH_RESTORE_RETENTION_HOURS,
                        MAX_TRASH_RESTORE_RETENTION_HOURS
                    )
                )
                .apply()
        }

    /**
     * ARGB color of the text inside the task cards, stored as an Int because
     * SharedPreferences has no nullable Int. [NO_COLOR] means "use the theme
     * default", which is what the user gets with "restore default color".
     */
    var tableTextColor: Int
        get() = prefs.getInt(KEY_TABLE_TEXT_COLOR, NO_COLOR)
        set(value) {
            prefs.edit().putInt(KEY_TABLE_TEXT_COLOR, value).apply()
        }

    /** Same idea as [tableTextColor], for the text inside the settings cards. */
    var settingsTextColor: Int
        get() = prefs.getInt(KEY_SETTINGS_TEXT_COLOR, NO_COLOR)
        set(value) {
            prefs.edit().putInt(KEY_SETTINGS_TEXT_COLOR, value).apply()
        }

    /**
     * Opacity (0..1) of the solid card fill. Independent from the glass fill
     * alpha, so switching the rendering scheme keeps both settings intact.
     */
    var solidFillAlpha: Float
        get() = prefs.getFloat(KEY_SOLID_FILL_ALPHA, DEFAULT_SOLID_FILL_ALPHA)
            .coerceIn(MIN_SOLID_FILL_ALPHA, MAX_SOLID_FILL_ALPHA)
        set(value) {
            prefs.edit()
                .putFloat(
                    KEY_SOLID_FILL_ALPHA,
                    value.coerceIn(MIN_SOLID_FILL_ALPHA, MAX_SOLID_FILL_ALPHA)
                )
                .apply()
        }

    /** ARGB fill color of the solid card scheme. Black by default. */
    var solidFillColor: Int
        get() = prefs.getInt(KEY_SOLID_FILL_COLOR, DEFAULT_SOLID_FILL_COLOR)
        set(value) {
            prefs.edit().putInt(KEY_SOLID_FILL_COLOR, value).apply()
        }

    /** Highlight stroke width (dp) of the solid card scheme. 0 hides it. */
    var solidStrokeWidth: Float
        get() = prefs.getFloat(KEY_SOLID_STROKE_WIDTH, DEFAULT_SOLID_STROKE_WIDTH)
            .coerceIn(MIN_SOLID_STROKE_WIDTH, MAX_SOLID_STROKE_WIDTH)
        set(value) {
            prefs.edit()
                .putFloat(
                    KEY_SOLID_STROKE_WIDTH,
                    value.coerceIn(MIN_SOLID_STROKE_WIDTH, MAX_SOLID_STROKE_WIDTH)
                )
                .apply()
        }

    /** Opacity (0..1) of the tint layer of the WinUI glass cards. */
    var winuiFillAlpha: Float
        get() = prefs.getFloat(KEY_WINUI_FILL_ALPHA, DEFAULT_WINUI_FILL_ALPHA)
            .coerceIn(MIN_WINUI_FILL_ALPHA, MAX_WINUI_FILL_ALPHA)
        set(value) {
            prefs.edit()
                .putFloat(
                    KEY_WINUI_FILL_ALPHA,
                    value.coerceIn(MIN_WINUI_FILL_ALPHA, MAX_WINUI_FILL_ALPHA)
                )
                .apply()
        }

    /** Blur radius (dp) of the WinUI glass backdrop. 0 keeps the backdrop sharp. */
    var winuiBlur: Float
        get() = prefs.getFloat(KEY_WINUI_BLUR, DEFAULT_WINUI_BLUR)
            .coerceIn(MIN_WINUI_BLUR, MAX_WINUI_BLUR)
        set(value) {
            prefs.edit()
                .putFloat(KEY_WINUI_BLUR, value.coerceIn(MIN_WINUI_BLUR, MAX_WINUI_BLUR))
                .apply()
        }

    /** Strength (0..1) of the refraction shift around the card rim. */
    var winuiRefraction: Float
        get() = prefs.getFloat(KEY_WINUI_REFRACTION, DEFAULT_WINUI_REFRACTION)
            .coerceIn(MIN_WINUI_UNIT, MAX_WINUI_UNIT)
        set(value) {
            prefs.edit()
                .putFloat(
                    KEY_WINUI_REFRACTION,
                    value.coerceIn(MIN_WINUI_UNIT, MAX_WINUI_UNIT)
                )
                .apply()
        }

    /** Strength (0..1) of the chromatic dispersion near the rim. */
    var winuiChromatic: Float
        get() = prefs.getFloat(KEY_WINUI_CHROMATIC, DEFAULT_WINUI_CHROMATIC)
            .coerceIn(MIN_WINUI_UNIT, MAX_WINUI_UNIT)
        set(value) {
            prefs.edit()
                .putFloat(
                    KEY_WINUI_CHROMATIC,
                    value.coerceIn(MIN_WINUI_UNIT, MAX_WINUI_UNIT)
                )
                .apply()
        }

    /** Strength (0..1) of the soft specular glare bands on the glass surface. */
    var winuiGlare: Float
        get() = prefs.getFloat(KEY_WINUI_GLARE, DEFAULT_WINUI_GLARE)
            .coerceIn(MIN_WINUI_UNIT, MAX_WINUI_UNIT)
        set(value) {
            prefs.edit()
                .putFloat(KEY_WINUI_GLARE, value.coerceIn(MIN_WINUI_UNIT, MAX_WINUI_UNIT))
                .apply()
        }

    /** Skips the WinUI rim warp on weak GPUs (plain blurred fill instead). */
    var winuiPerformanceMode: Boolean
        get() = prefs.getBoolean(KEY_WINUI_PERFORMANCE, false)
        set(value) = prefs.edit().putBoolean(KEY_WINUI_PERFORMANCE, value).apply()

    companion object {
        private const val PREFS_NAME = "ddl_preferences"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_LAST_LIST_ID = "last_list_id"
        private const val KEY_BACKGROUND_URI = "background_uri"
        private const val KEY_GLASS_FILL_ALPHA = "glass_fill_alpha"
        private const val KEY_GLASS_COMPAT_MODE = "glass_compat_mode"
        private const val KEY_OVERDUE_TEXT_SCALE = "overdue_text_scale"
        private const val KEY_KEEP_AUTO_DELETED = "keep_auto_deleted_in_trash"
        private const val KEY_TRASH_RETENTION_DAYS = "trash_retention_days"
        private const val KEY_TRASH_RESTORE_RETENTION_HOURS = "trash_restore_retention_hours"
        private const val KEY_TABLE_TEXT_COLOR = "table_text_color"
        private const val KEY_SETTINGS_TEXT_COLOR = "settings_text_color"
        private const val KEY_SOLID_FILL_ALPHA = "solid_fill_alpha"
        private const val KEY_SOLID_FILL_COLOR = "solid_fill_color"
        private const val KEY_SOLID_STROKE_WIDTH = "solid_stroke_width"
        private const val KEY_RENDER_SCHEME = "render_scheme"
        private const val KEY_WINUI_FILL_ALPHA = "winui2_fill_alpha"
        private const val KEY_WINUI_BLUR = "winui2_blur"
        private const val KEY_WINUI_REFRACTION = "winui2_refraction"
        private const val KEY_WINUI_CHROMATIC = "winui2_chromatic"
        private const val KEY_WINUI_GLARE = "winui2_glare"
        private const val KEY_WINUI_PERFORMANCE = "winui_performance_mode"

        const val DEFAULT_USER_NAME = "用户"
        const val NO_LIST = -1L

        /** 0.16f = 84% transparency: clearly see-through glass by default. */
        const val DEFAULT_GLASS_FILL_ALPHA = 0.16f
        const val MIN_GLASS_FILL_ALPHA = 0f
        const val MAX_GLASS_FILL_ALPHA = 1f

        /** 1.0 == the unscaled label size, so it never grows past the original. */
        const val DEFAULT_OVERDUE_TEXT_SCALE = 0.9f
        const val MIN_OVERDUE_TEXT_SCALE = 0.6f
        const val MAX_OVERDUE_TEXT_SCALE = 1f

        const val DEFAULT_TRASH_RETENTION_DAYS = 7
        const val MIN_TRASH_RETENTION_DAYS = 1
        const val MAX_TRASH_RETENTION_DAYS = 30

        const val DEFAULT_TRASH_RESTORE_RETENTION_HOURS = 12
        const val MIN_TRASH_RESTORE_RETENTION_HOURS = 1
        const val MAX_TRASH_RESTORE_RETENTION_HOURS = 48

        /** Sentinel for "no custom color, fall back to the theme default". */
        const val NO_COLOR = 0

        /** Solid scheme defaults: mostly opaque black card with a 1.5dp highlight. */
        const val DEFAULT_SOLID_FILL_ALPHA = 0.85f
        const val MIN_SOLID_FILL_ALPHA = 0f
        const val MAX_SOLID_FILL_ALPHA = 1f
        const val DEFAULT_SOLID_FILL_COLOR = 0xFF000000.toInt()
        const val DEFAULT_SOLID_STROKE_WIDTH = 1.5f
        const val MIN_SOLID_STROKE_WIDTH = 0f
        const val MAX_SOLID_STROKE_WIDTH = 2.25f

        /** WinUI glass defaults: light tint, small blur, full refraction. */
        const val DEFAULT_WINUI_FILL_ALPHA = 0.10f
        const val MIN_WINUI_FILL_ALPHA = 0f
        const val MAX_WINUI_FILL_ALPHA = 1f
        const val DEFAULT_WINUI_BLUR = 2f
        const val MIN_WINUI_BLUR = 0f
        const val MAX_WINUI_BLUR = 24f
        const val DEFAULT_WINUI_REFRACTION = 1.0f
        const val DEFAULT_WINUI_CHROMATIC = 0.25f
        const val DEFAULT_WINUI_GLARE = 0.30f
        const val MIN_WINUI_UNIT = 0f
        const val MAX_WINUI_UNIT = 1f
    }
}
