package com.example.ddlreminder.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ddlreminder.BuildConfig
import com.example.ddlreminder.R
import com.example.ddlreminder.data.AppPreferences
import com.example.ddlreminder.data.RenderScheme
import com.example.ddlreminder.util.RenderCompat
import kotlin.math.roundToInt

/** Pages reachable from the main settings screen. */
private enum class SettingsPage { Main, Background, GlassCards, RenderScheme, Trash, More }

/** Which text colour the colour picker is editing. */
private enum class ColorTarget { Table, Settings }

/**
 * Colours derived from the user's "settings text colour" preference. The default
 * is pure white, so the screen keeps its previous look until the user changes it.
 */
private data class SettingsColors(
    val primary: Color,
    val secondary: Color,
    val muted: Color,
    val section: Color,
    val icon: Color,
    val divider: Color
)

private fun settingsColors(argb: Int): SettingsColors {
    val base = if (argb != AppPreferences.NO_COLOR) Color(argb) else Color.White
    return SettingsColors(
        primary = base,
        secondary = base.copy(alpha = 0.72f),
        muted = base.copy(alpha = 0.70f),
        section = base.copy(alpha = 0.75f),
        icon = base.copy(alpha = 0.85f),
        divider = base.copy(alpha = 0.16f)
    )
}

/** Preset colours offered by the text colour picker. */
private val textColorOptions: List<Pair<String, Int>> = listOf(
    "白色" to 0xFFFFFFFF.toInt(),
    "浅灰" to 0xFFE8EAED.toInt(),
    "蓝灰" to 0xFFB0BEC5.toInt(),
    "天蓝" to 0xFF90CAF9.toInt(),
    "暖橙" to 0xFFFFCC80.toInt(),
    "浅绿" to 0xFFA5D6A7.toInt(),
    "粉色" to 0xFFF48FB1.toInt(),
    "淡紫" to 0xFFCE93D8.toInt(),
    "黑色" to 0xFF000000.toInt()
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    userName: String,
    stats: AppStats,
    notificationsEnabled: Boolean,
    exactAlarmsEnabled: Boolean,
    suggestedFileName: String,
    backgroundActive: Boolean,
    glassFillAlpha: Float,
    renderScheme: RenderScheme,
    winuiFillAlpha: Float,
    winuiBlur: Float,
    winuiRefraction: Float,
    winuiChromatic: Float,
    winuiGlare: Float,
    winuiPerformanceMode: Boolean,
    overdueTextScale: Float,
    keepAutoDeletedInTrash: Boolean,
    trashRetentionDays: Int,
    trashRestoreRetentionHours: Int,
    tableTextColor: Int,
    settingsTextColor: Int,
    solidFillAlpha: Float,
    solidFillColor: Int,
    solidStrokeWidth: Float,
    onRenameUser: () -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onBackgroundPicked: (Uri) -> Unit,
    onResetBackground: () -> Unit,
    onGlassFillAlphaChange: (Float) -> Unit,
    onRenderSchemeChange: (RenderScheme) -> Unit,
    onWinuiFillAlphaChange: (Float) -> Unit,
    onWinuiBlurChange: (Float) -> Unit,
    onWinuiRefractionChange: (Float) -> Unit,
    onWinuiChromaticChange: (Float) -> Unit,
    onWinuiGlareChange: (Float) -> Unit,
    onWinuiPerformanceModeChange: (Boolean) -> Unit,
    onOverdueTextScaleChange: (Float) -> Unit,
    onKeepAutoDeletedChange: (Boolean) -> Unit,
    onTrashRetentionChange: (Int) -> Unit,
    onTrashRestoreRetentionChange: (Int) -> Unit,
    onTableTextColorChange: (Int) -> Unit,
    onSettingsTextColorChange: (Int) -> Unit,
    onSolidFillAlphaChange: (Float) -> Unit,
    onSolidFillColorChange: (Int) -> Unit,
    onSolidStrokeWidthChange: (Float) -> Unit,
    onResetAllSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit
) {
    var pageOrdinal by rememberSaveable { mutableIntStateOf(SettingsPage.Main.ordinal) }
    val page = SettingsPage.entries[pageOrdinal]
    var showChangelog by rememberSaveable { mutableStateOf(false) }
    var showSolidColorPicker by rememberSaveable { mutableStateOf(false) }
    var showResetAllSettings by rememberSaveable { mutableStateOf(false) }
    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }

    val colors = remember(settingsTextColor) { settingsColors(settingsTextColor) }

    // Settings cards pick their look from LocalRenderScheme, exactly like the
    // list cards, so a scheme switch updates both surfaces at once.

    BackHandler(enabled = page != SettingsPage.Main) {
        // The render scheme page is one level deeper than the card page.
        pageOrdinal = if (page == SettingsPage.RenderScheme) {
            SettingsPage.GlassCards.ordinal
        } else {
            SettingsPage.Main.ordinal
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExport) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImport) }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onBackgroundPicked) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        when (page) {
            SettingsPage.Main -> {
                Spacer(Modifier.height(4.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = GlassTint.Dark,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(colors.primary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userName.take(1).ifBlank { "?" },
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.primary
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.primary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(
                                    R.string.settings_user_stats,
                                    stats.listCount,
                                    stats.taskCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondary
                            )
                        }
                        TextButton(
                            onClick = onRenameUser,
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)
                        ) {
                            Text(stringResource(R.string.change_user_name))
                        }
                    }
                }

                SettingsSectionTitle(stringResource(R.string.settings_appearance), colors)
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = GlassTint.Dark,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)
                ) {
                    SettingsActionRow(
                        icon = Icons.Default.Refresh,
                        title = stringResource(R.string.settings_background),
                        subtitle = stringResource(R.string.settings_background_desc),
                        colors = colors,
                        onClick = { pageOrdinal = SettingsPage.Background.ordinal }
                    )
                    SettingsDivider(colors)
                    SettingsActionRow(
                        icon = null,
                        title = stringResource(R.string.settings_glass_cards),
                        subtitle = stringResource(R.string.settings_glass_cards_desc),
                        colors = colors,
                        onClick = { pageOrdinal = SettingsPage.GlassCards.ordinal },
                        customIcon = { RoundedRectIcon(colors.icon) }
                    )
                    SettingsDivider(colors)
                    SettingsActionRow(
                        icon = Icons.Default.Settings,
                        title = stringResource(R.string.more_settings),
                        subtitle = stringResource(R.string.more_settings_empty),
                        colors = colors,
                        onClick = { pageOrdinal = SettingsPage.More.ordinal }
                    )
                }

                SettingsSectionTitle(stringResource(R.string.settings_data), colors)
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = GlassTint.Dark,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)
                ) {
                    SettingsActionRow(
                        icon = Icons.Default.Share,
                        title = stringResource(R.string.export_data),
                        subtitle = stringResource(R.string.export_data_desc),
                        colors = colors,
                        onClick = { exportLauncher.launch(suggestedFileName) }
                    )
                    SettingsDivider(colors)
                    SettingsActionRow(
                        icon = Icons.Default.Create,
                        title = stringResource(R.string.import_data),
                        subtitle = stringResource(R.string.import_data_desc),
                        colors = colors,
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                    )
                    SettingsDivider(colors)
                    SettingsActionRow(
                        icon = Icons.Default.Delete,
                        title = stringResource(R.string.settings_trash),
                        subtitle = stringResource(R.string.settings_trash_desc),
                        colors = colors,
                        onClick = { pageOrdinal = SettingsPage.Trash.ordinal }
                    )
                }

                SettingsSectionTitle(stringResource(R.string.settings_reminders), colors)
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = GlassTint.Dark,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)
                ) {
                    SettingsStatusRow(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.notifications_disabled),
                        enabled = notificationsEnabled,
                        colors = colors,
                        onClick = onOpenNotificationSettings
                    )
                    SettingsDivider(colors)
                    SettingsStatusRow(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.exact_alarms_disabled),
                        enabled = exactAlarmsEnabled,
                        colors = colors,
                        onClick = onOpenExactAlarmSettings
                    )
                }

                SettingsSectionTitle(stringResource(R.string.settings_about), colors)
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = GlassTint.Dark,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChangelog = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.icon,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.version_label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.muted
                        )
                    }
                    SettingsDivider(colors)
                    SettingsActionRow(
                        icon = Icons.Default.Phone,
                        title = stringResource(R.string.device_info),
                        subtitle = RenderCompat.deviceLabel(),
                        colors = colors,
                        onClick = null
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.data_local_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            SettingsPage.Background -> {
                SettingsPageHeader(stringResource(R.string.settings_background), colors) {
                    pageOrdinal = SettingsPage.Main.ordinal
                }
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = GlassTint.Dark,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)
                ) {
                    SettingsActionRow(
                        icon = Icons.Default.Refresh,
                        title = stringResource(R.string.change_background),
                        subtitle = stringResource(
                            if (backgroundActive) R.string.background_custom
                            else R.string.background_default
                        ),
                        colors = colors,
                        onClick = { backgroundLauncher.launch(arrayOf("image/*")) }
                    )
                    SettingsDivider(colors)
                    SettingsActionRow(
                        icon = Icons.Default.Clear,
                        title = stringResource(R.string.reset_background),
                        subtitle = stringResource(R.string.reset_background_desc),
                        colors = colors,
                        onClick = onResetBackground
                    )
                }
            }

            SettingsPage.GlassCards -> {
                SettingsPageHeader(stringResource(R.string.settings_glass_cards), colors) {
                    pageOrdinal = SettingsPage.Main.ordinal
                }
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = GlassTint.Dark,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)
                ) {
                    SettingsActionRow(
                        icon = Icons.Default.MoreVert,
                        title = stringResource(R.string.glass_compat_title),
                        subtitle = stringResource(
                            when (renderScheme) {
                                RenderScheme.Solid -> R.string.render_scheme_solid
                                RenderScheme.Liquid -> R.string.render_scheme_liquid
                                RenderScheme.LiquidWinUI -> R.string.render_scheme_winui
                            }
                        ),
                        colors = colors,
                        onClick = { pageOrdinal = SettingsPage.RenderScheme.ordinal }
                    )
                    SettingsDivider(colors)
                    ColorSettingRow(
                        icon = Icons.Default.Edit,
                        title = stringResource(R.string.table_text_color_title),
                        subtitle = stringResource(R.string.table_text_color_desc),
                        currentColor = tableTextColor,
                        colors = colors,
                        onClick = { colorTarget = ColorTarget.Table }
                    )
                    SettingsDivider(colors)
                    ColorSettingRow(
                        icon = Icons.Default.Settings,
                        title = stringResource(R.string.settings_text_color_title),
                        subtitle = stringResource(R.string.settings_text_color_desc),
                        currentColor = settingsTextColor,
                        colors = colors,
                        onClick = { colorTarget = ColorTarget.Settings }
                    )
                    SettingsDivider(colors)
                    // Moved here from "more settings": it only affects the list
                    // cards, so it belongs with the rest of the card options.
                    SettingsSliderSection(
                        title = stringResource(R.string.overdue_text_scale_title),
                        description = stringResource(R.string.overdue_text_scale_desc),
                        valueLabel = stringResource(
                            R.string.overdue_text_scale_value,
                            (overdueTextScale * 100f).roundToInt()
                        ),
                        value = ((overdueTextScale - 0.6f) / 0.4f).coerceIn(0f, 1f),
                        colors = colors,
                        onValueChange = { onOverdueTextScaleChange(0.6f + it * 0.4f) }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.color_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            SettingsPage.RenderScheme -> {
                SettingsPageHeader(stringResource(R.string.glass_compat_title), colors) {
                    pageOrdinal = SettingsPage.GlassCards.ordinal
                }
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = GlassTint.Dark,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)
                ) {
                    RenderSchemeRow(
                        label = stringResource(R.string.render_scheme_solid),
                        selected = renderScheme == RenderScheme.Solid,
                        colors = colors,
                        onClick = { onRenderSchemeChange(RenderScheme.Solid) }
                    )
                    AnimatedVisibility(
                        visible = renderScheme == RenderScheme.Solid,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            SettingsDivider(colors)
                            SettingsSliderSection(
                                title = stringResource(R.string.solid_fill_alpha_title),
                                description = stringResource(R.string.solid_fill_alpha_desc),
                                valueLabel = stringResource(
                                    R.string.solid_fill_alpha_value,
                                    ((1f - solidFillAlpha) * 100f).roundToInt()
                                ),
                                value = (1f - solidFillAlpha).coerceIn(0f, 1f),
                                colors = colors,
                                onValueChange = { onSolidFillAlphaChange(1f - it) }
                            )
                            SettingsDivider(colors)
                            ColorSettingRow(
                                icon = Icons.Default.Edit,
                                title = stringResource(R.string.solid_fill_color_title),
                                subtitle = stringResource(R.string.solid_fill_color_desc),
                                currentColor = solidFillColor,
                                colors = colors,
                                onClick = { showSolidColorPicker = true }
                            )
                            SettingsDivider(colors)
                            SettingsSliderSection(
                                title = stringResource(R.string.solid_stroke_title),
                                description = stringResource(R.string.solid_stroke_desc),
                                valueLabel = stringResource(
                                    R.string.solid_stroke_value,
                                    solidStrokeWidth
                                ),
                                value = (solidStrokeWidth /
                                    AppPreferences.MAX_SOLID_STROKE_WIDTH).coerceIn(0f, 1f),
                                colors = colors,
                                onValueChange = {
                                    onSolidStrokeWidthChange(
                                        it * AppPreferences.MAX_SOLID_STROKE_WIDTH
                                    )
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        onSolidStrokeWidthChange(
                                            AppPreferences.DEFAULT_SOLID_STROKE_WIDTH
                                        )
                                    }
                                ) {
                                    Text(
                                        text = stringResource(R.string.solid_stroke_reset),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.secondary
                                    )
                                }
                            }
                        }
                    }
                    SettingsDivider(colors)
                    RenderSchemeRow(
                        label = stringResource(R.string.render_scheme_liquid),
                        selected = renderScheme == RenderScheme.Liquid,
                        colors = colors,
                        onClick = { onRenderSchemeChange(RenderScheme.Liquid) }
                    )
                    AnimatedVisibility(
                        visible = renderScheme == RenderScheme.Liquid,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            SettingsDivider(colors)
                            SettingsSliderSection(
                                title = stringResource(R.string.liquid_fill_alpha_title),
                                description = stringResource(R.string.liquid_fill_alpha_desc),
                                valueLabel = stringResource(
                                    R.string.solid_fill_alpha_value,
                                    ((1f - glassFillAlpha) * 100f).roundToInt()
                                ),
                                value = (1f - glassFillAlpha).coerceIn(0f, 1f),
                                colors = colors,
                                onValueChange = { onGlassFillAlphaChange(1f - it) }
                            )
                        }
                    }
                    SettingsDivider(colors)
                    RenderSchemeRow(
                        label = stringResource(R.string.render_scheme_winui),
                        selected = renderScheme == RenderScheme.LiquidWinUI,
                        colors = colors,
                        onClick = { onRenderSchemeChange(RenderScheme.LiquidWinUI) }
                    )
                    AnimatedVisibility(
                        visible = renderScheme == RenderScheme.LiquidWinUI,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            SettingsDivider(colors)
                            SettingsSliderSection(
                                title = stringResource(R.string.winui_blur_title),
                                description = stringResource(R.string.winui_blur_desc),
                                valueLabel = stringResource(
                                    R.string.winui_blur_value,
                                    winuiBlur.roundToInt()
                                ),
                                value = (winuiBlur / AppPreferences.MAX_WINUI_BLUR).coerceIn(0f, 1f),
                                colors = colors,
                                onValueChange = {
                                    onWinuiBlurChange(it * AppPreferences.MAX_WINUI_BLUR)
                                }
                            )
                            SettingsDivider(colors)
                            SettingsSwitchRow(
                                icon = Icons.Default.Build,
                                title = stringResource(R.string.winui_perf_title),
                                subtitle = stringResource(R.string.winui_perf_desc),
                                checked = winuiPerformanceMode,
                                onCheckedChange = onWinuiPerformanceModeChange,
                                colors = colors
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.render_scheme_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            SettingsPage.Trash -> {
                SettingsPageHeader(stringResource(R.string.settings_trash), colors) {
                    pageOrdinal = SettingsPage.Main.ordinal
                }
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = GlassTint.Dark,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)
                ) {
                    SettingsSwitchRow(
                        icon = Icons.Default.Delete,
                        title = stringResource(R.string.trash_keep_auto_title),
                        subtitle = stringResource(R.string.trash_keep_auto_desc),
                        checked = keepAutoDeletedInTrash,
                        colors = colors,
                        onCheckedChange = onKeepAutoDeletedChange
                    )
                    SettingsDivider(colors)
                    SettingsSliderSection(
                        title = stringResource(R.string.trash_retention_title),
                        description = stringResource(R.string.trash_retention_desc),
                        valueLabel = stringResource(R.string.trash_retention_value, trashRetentionDays),
                        value = ((trashRetentionDays - 1) / 29f).coerceIn(0f, 1f),
                        colors = colors,
                        onValueChange = {
                            onTrashRetentionChange((it * 29f).roundToInt().coerceIn(0, 29) + 1)
                        }
                    )
                    SettingsDivider(colors)
                    SettingsSliderSection(
                        title = stringResource(R.string.trash_restore_retention_title),
                        description = stringResource(R.string.trash_restore_retention_desc),
                        valueLabel = stringResource(
                            R.string.trash_restore_retention_value,
                            trashRestoreRetentionHours
                        ),
                        value = ((trashRestoreRetentionHours - 1) / 47f).coerceIn(0f, 1f),
                        colors = colors,
                        onValueChange = {
                            onTrashRestoreRetentionChange((it * 47f).roundToInt().coerceIn(0, 47) + 1)
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.trash_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            SettingsPage.More -> {
                SettingsPageHeader(stringResource(R.string.more_settings), colors) {
                    pageOrdinal = SettingsPage.Main.ordinal
                }
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = GlassTint.Dark,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)
                ) {
                    SettingsActionRow(
                        icon = Icons.Default.Refresh,
                        title = stringResource(R.string.reset_all_settings),
                        subtitle = stringResource(R.string.reset_all_settings_desc),
                        colors = colors,
                        onClick = { showResetAllSettings = true }
                    )
                }
            }
        }
    }

    if (showChangelog) {
        ChangelogDialog(colors = colors, onDismiss = { showChangelog = false })
    }

    if (showResetAllSettings) {
        AlertDialog(
            onDismissRequest = { showResetAllSettings = false },
            title = {
                Text(
                    text = stringResource(R.string.reset_all_settings_confirm_title),
                    color = colors.primary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.reset_all_settings_confirm_message),
                    color = colors.secondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetAllSettings = false
                        onResetAllSettings()
                    }
                ) {
                    Text(stringResource(R.string.reset_all_settings_confirm), color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllSettings = false }) {
                    Text(stringResource(R.string.cancel), color = colors.secondary)
                }
            }
        )
    }

    val target = colorTarget
    if (target != null) {
        val selected = if (target == ColorTarget.Table) tableTextColor else settingsTextColor
        ColorPickerDialog(
            title = stringResource(
                if (target == ColorTarget.Table) R.string.table_text_color_title
                else R.string.settings_text_color_title
            ),
            selected = selected,
            colors = colors,
            onPick = { picked ->
                if (target == ColorTarget.Table) {
                    onTableTextColorChange(picked)
                } else {
                    onSettingsTextColorChange(picked)
                }
                colorTarget = null
            },
            onReset = {
                if (target == ColorTarget.Table) {
                    onTableTextColorChange(AppPreferences.NO_COLOR)
                } else {
                    onSettingsTextColorChange(AppPreferences.NO_COLOR)
                }
                colorTarget = null
            },
            onDismiss = { colorTarget = null }
        )
    }

    if (showSolidColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.solid_fill_color_title),
            selected = solidFillColor,
            colors = colors,
            onPick = { picked ->
                onSolidFillColorChange(picked)
                showSolidColorPicker = false
            },
            onReset = {
                onSolidFillColorChange(AppPreferences.DEFAULT_SOLID_FILL_COLOR)
                showSolidColorPicker = false
            },
            onDismiss = { showSolidColorPicker = false }
        )
    }
}

@Composable
private fun SettingsPageHeader(title: String, colors: SettingsColors, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = colors.primary
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary
        )
    }
}

@Composable
private fun SettingsSectionTitle(text: String, colors: SettingsColors) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = colors.section,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsDivider(colors: SettingsColors) {
    HorizontalDivider(color = colors.divider, thickness = 1.dp)
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector?,
    title: String,
    subtitle: String,
    colors: SettingsColors,
    onClick: (() -> Unit)?,
    customIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            customIcon != null -> Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                customIcon()
            }
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.icon,
                modifier = Modifier.size(22.dp)
            )
            else -> Spacer(Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.muted
            )
        }
    }
}

/** Small filled rounded rectangle used as the "glass cards" icon. */
@Composable
private fun RoundedRectIcon(tint: Color) {
    Box(
        modifier = Modifier
            .size(width = 17.dp, height = 11.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(tint)
    )
}

/**
 * One option of the card render scheme page: plain text plus a check mark on the
 * selected row, no icon (matching the requested layout).
 */
@Composable
private fun RenderSchemeRow(
    label: String,
    selected: Boolean,
    colors: SettingsColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.primary,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsStatusRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    colors: SettingsColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.icon,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(
                if (enabled) R.string.status_enabled else R.string.status_disabled
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) colors.primary else colors.muted
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.muted
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    colors: SettingsColors,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.icon,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.primary.copy(alpha = 0.55f),
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.16f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.38f)
            )
        )
    }
}

@Composable
private fun SettingsSliderSection(
    title: String,
    description: String,
    valueLabel: String,
    value: Float,
    colors: SettingsColors,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondary
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary.copy(alpha = 0.85f),
                inactiveTrackColor = colors.primary.copy(alpha = 0.24f)
            )
        )
    }
}

@Composable
private fun ColorSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    currentColor: Int,
    colors: SettingsColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.icon,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary
            )
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (currentColor != AppPreferences.NO_COLOR) Color(currentColor)
                    else Color.White
                )
                .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.muted
        )
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    selected: Int,
    colors: SettingsColors,
    onPick: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1B1F26))
                .padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary
            )
            Spacer(Modifier.height(12.dp))
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                textColorOptions.forEach { (name, argb) ->
                    val isSelected = selected == argb ||
                        (selected == AppPreferences.NO_COLOR && argb == 0xFFFFFFFF.toInt())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(argb) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = colors.primary
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = colors.divider)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onReset,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.secondary)
                ) {
                    Text(stringResource(R.string.color_reset))
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)
                ) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}

@Composable
private fun ChangelogDialog(colors: SettingsColors, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)
            ) {
                Text(stringResource(R.string.done))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.changelog_title),
                color = colors.primary
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.changelog_updated_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted
                )
                Spacer(Modifier.height(10.dp))
                ChangelogVersionBlock(
                    versionTitle = stringResource(R.string.changelog_161_title),
                    features = stringArrayResource(R.array.changelog_161_features).toList(),
                    fixes = stringArrayResource(R.array.changelog_161_fixes).toList(),
                    colors = colors
                )
                ChangelogVersionBlock(
                    versionTitle = stringResource(R.string.changelog_160_title),
                    features = stringArrayResource(R.array.changelog_160_features).toList(),
                    fixes = emptyList(),
                    colors = colors
                )
                ChangelogVersionBlock(
                    versionTitle = stringResource(R.string.changelog_154_title),
                    features = stringArrayResource(R.array.changelog_154_features).toList(),
                    fixes = stringArrayResource(R.array.changelog_154_fixes).toList(),
                    colors = colors
                )
                ChangelogVersionBlock(
                    versionTitle = stringResource(R.string.changelog_153_title),
                    features = stringArrayResource(R.array.changelog_153_features).toList(),
                    fixes = stringArrayResource(R.array.changelog_153_fixes).toList(),
                    colors = colors
                )
                ChangelogVersionBlock(
                    versionTitle = stringResource(R.string.changelog_152_title),
                    features = stringArrayResource(R.array.changelog_152_features).toList(),
                    fixes = stringArrayResource(R.array.changelog_152_fixes).toList(),
                    colors = colors
                )
                ChangelogVersionBlock(
                    versionTitle = stringResource(R.string.changelog_151_title),
                    features = emptyList(),
                    fixes = stringArrayResource(R.array.changelog_151_fixes).toList(),
                    colors = colors
                )
                ChangelogVersionBlock(
                    versionTitle = stringResource(R.string.changelog_150_title),
                    features = stringArrayResource(R.array.changelog_150_features).toList(),
                    fixes = emptyList(),
                    colors = colors
                )
                ChangelogVersionBlock(
                    versionTitle = stringResource(R.string.changelog_142_title),
                    features = stringArrayResource(R.array.changelog_142_features).toList(),
                    fixes = stringArrayResource(R.array.changelog_142_fixes).toList(),
                    colors = colors
                )
            }
        }
    )
}

@Composable
private fun ChangelogVersionBlock(
    versionTitle: String,
    features: List<String>,
    fixes: List<String>,
    colors: SettingsColors
) {
    Text(
        text = versionTitle,
        style = MaterialTheme.typography.labelMedium,
        color = colors.secondary
    )
    Spacer(Modifier.height(6.dp))
    if (features.isNotEmpty()) {
        ChangelogSection(stringResource(R.string.changelog_features), features, colors)
        Spacer(Modifier.height(8.dp))
    }
    if (fixes.isNotEmpty()) {
        ChangelogSection(stringResource(R.string.changelog_fixes), fixes, colors)
        Spacer(Modifier.height(8.dp))
    }
    HorizontalDivider(color = colors.divider)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ChangelogSection(title: String, items: List<String>, colors: SettingsColors) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = colors.primary
    )
    Spacer(Modifier.height(4.dp))
    items.forEach { item ->
        Row(modifier = Modifier.padding(vertical = 1.dp)) {
            Text(
                text = "· ",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondary
            )
            Text(
                text = item,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary
            )
        }
    }
}
