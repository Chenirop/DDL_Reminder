package com.example.ddlreminder.data

/**
 * How the rounded task cards are painted.
 *
 * [Solid] is a plain user coloured fill, [Liquid] is the original backdrop
 * sampling glass and [LiquidWinUI] is the WinUI inspired glass (blurred backdrop
 * with rim refraction, dispersion, Fresnel edge light and soft glare).
 */
enum class RenderScheme(val storageValue: Int) {
    Solid(0),
    Liquid(1),
    LiquidWinUI(2);

    companion object {
        fun fromStorage(value: Int): RenderScheme =
            entries.firstOrNull { it.storageValue == value } ?: Liquid
    }
}
