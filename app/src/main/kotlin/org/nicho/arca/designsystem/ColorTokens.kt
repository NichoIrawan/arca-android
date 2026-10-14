package org.nicho.arca.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The 20 colour tokens (DSG-ARC-001 §3). Values come from the Android Hi-Fi prototype, which
 * wins where the two disagree.
 *
 * Every token has its own dark value, chosen against the dark surface, never computed by
 * inverting the light one (§12).
 *
 * Rules the tokens can't enforce on their own:
 * - An expense amount is `ink`; `income` is the only hue an amount carries, always with "+";
 *   a transfer is `inkTertiary` and unsigned (§3.2).
 * - `negative` is for a negative balance and destructive actions only, never an ordinary
 *   expense or an over-budget figure.
 * - `warning` belongs to the mascot only (§10.10).
 * - `brand` (brass) marks favourites only (§3.5).
 * - De-emphasise with size and weight, never by fading text (§3.7).
 */
@Immutable
data class ArcaColors(
    // Surfaces
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,       // stat blocks, icon tiles, the over-budget pill
    val surfaceHero: Color,         // Account Detail's emphasised stat cell; doesn't invert (§3.4a)
    val inkOnHero: Color,           // text on surfaceHero and on the full-bleed ink hero

    // Ink
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val hairline: Color,

    // Accent: FAB, primary buttons, selected chips, the active tab. Inverts by theme (§3.4).
    val accent: Color,
    val accentOn: Color,

    // Money and state
    val income: Color,
    val negative: Color,
    val brand: Color,
    val warning: Color,

    // Category hues by identity, never by rank (§3.6): clay, blue, teal, plum, olive.
    val data1: Color,
    val data2: Color,
    val data3: Color,
    val data4: Color,
    val data5: Color,

    val isDark: Boolean,
) {
    val categoryHues: List<Color> get() = listOf(data1, data2, data3, data4, data5)

    /**
     * The colour to draw for a stored `Category.colorHex`.
     *
     * The database stores the light-theme hex (see DefaultCategoryProvider), so a dark screen maps
     * it to the matching dark hue instead of drawing the light value on a dark page. A hex outside
     * the palette is drawn as stored.
     */
    fun category(colorHex: String): Color {
        val stored = parseHex(colorHex) ?: return inkSecondary
        val index = ArcaLightColors.categoryHues.indexOf(stored)
        return if (index >= 0) categoryHues[index] else stored
    }
}

val ArcaLightColors = ArcaColors(
    surface = Color(0xFFFAF9F7),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFF1EFEB),
    surfaceHero = Color(0xFF1A1712),
    inkOnHero = Color(0xFFFFFFFF),
    ink = Color(0xFF16140F),
    inkSecondary = Color(0xFF5C564C),
    inkTertiary = Color(0xFF6E675B),
    hairline = Color(0xFFE8E4DC),
    accent = Color(0xFF1A1712),
    accentOn = Color(0xFFFFFFFF),
    income = Color(0xFF2E6F4E),
    negative = Color(0xFFA33A35),
    brand = Color(0xFFA9772E),
    warning = Color(0xFF8F6B12),
    data1 = Color(0xFFC56A3E),
    data2 = Color(0xFF3260A3),
    data3 = Color(0xFF069F89),
    data4 = Color(0xFF914A8C),
    data5 = Color(0xFF8E9337),
    isDark = false,
)

val ArcaDarkColors = ArcaColors(
    surface = Color(0xFF131210),
    surfaceRaised = Color(0xFF2A2620),
    surfaceSunken = Color(0xFF1F1C17),
    surfaceHero = Color(0xFF302C24),
    inkOnHero = Color(0xFFF2EFE9),
    ink = Color(0xFFF2EFE9),
    inkSecondary = Color(0xFFB8B1A4),
    inkTertiary = Color(0xFF8D8779),
    hairline = Color(0xFF2E2A23),
    accent = Color(0xFFF2EFE9),
    accentOn = Color(0xFF16140F),
    income = Color(0xFF6FBF95),
    negative = Color(0xFFE08B80),
    brand = Color(0xFFC79A4F),
    warning = Color(0xFFD9A94C),
    data1 = Color(0xFFCE7951),
    data2 = Color(0xFF5181C7),
    data3 = Color(0xFF2DA892),
    data4 = Color(0xFFA15E9C),
    data5 = Color(0xFF94993D),
    isDark = true,
)

/** "#c56a3e" or "c56a3e" → an opaque Color; null if it isn't six hex digits. */
private fun parseHex(hex: String): Color? {
    val digits = hex.removePrefix("#")
    if (digits.length != 6) return null
    val rgb = digits.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}
