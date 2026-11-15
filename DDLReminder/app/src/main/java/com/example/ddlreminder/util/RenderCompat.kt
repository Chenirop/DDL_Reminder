package com.example.ddlreminder.util

import android.os.Build

/**
 * HarmonyOS (and Huawei's Android builds) can mis-render the backdrop sampling
 * used by the liquid glass cards: that sampling is painted in absolute window
 * coordinates with clip paths and transforms, and on those devices it can end up
 * drawing nothing, which leaves the cards looking like flat panels.
 *
 * Instead of guessing at runtime whether the effect actually appeared, the app
 * starts in a universally supported "compat" rendering mode on these devices.
 * The user can switch between the full effect and the compat rendering from the
 * Settings screen, so the choice is never locked in by a wrong detection.
 */
object RenderCompat {

    /** True when the device runs HarmonyOS, detected through its system properties. */
    val isHarmonyOs: Boolean by lazy {
        systemProperty("ro.build.version.harmonyos").isNotBlank() ||
            systemProperty("ro.build.version.harmonyos.version").isNotBlank() ||
            systemProperty("hw_sc.build.platform.version").isNotBlank()
    }

    val isHuawei: Boolean by lazy {
        Build.MANUFACTURER.equals("HUAWEI", true) || Build.BRAND.equals("HUAWEI", true)
    }

    /**
     * Devices that start with the simple glass rendering. HarmonyOS is the known
     * problem case; every other device keeps the full backdrop effect.
     */
    fun shouldStartWithCompatRendering(): Boolean = isHarmonyOs || isHuawei

    /** Short human readable OS + device line shown in Settings. */
    fun deviceLabel(): String {
        val os = if (isHarmonyOs) "HarmonyOS" else "Android ${Build.VERSION.RELEASE}"
        val device = listOf(Build.MANUFACTURER, Build.MODEL)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        val api = "API ${Build.VERSION.SDK_INT}"
        return if (device.isEmpty()) "$os ($api)" else "$os ($api) · $device"
    }

    @Suppress("PrivateApi")
    private fun systemProperty(key: String): String = runCatching {
        val properties = Class.forName("android.os.SystemProperties")
        val get = properties.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String).orEmpty()
    }.getOrDefault("")
}
