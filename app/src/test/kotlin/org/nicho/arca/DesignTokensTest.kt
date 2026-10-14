package org.nicho.arca

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nicho.arca.designsystem.ArcaDarkColors
import org.nicho.arca.designsystem.ArcaLightColors
import org.nicho.arca.designsystem.ArcaMotionTokens
import org.nicho.arca.domain.seeding.DefaultCategoryProvider

// Plain JVM: Compose's Color and Easing need no device.
class DesignTokensTest {

    // The database stores light-theme hexes (DefaultCategoryProvider), so every one of them must
    // resolve to the matching hue in both themes.
    @Test
    fun everyStoredCategoryHex_mapsToTheSameHueInBothThemes() {
        DefaultCategoryProvider.palette.forEachIndexed { i, hex ->
            assertEquals(hex, ArcaLightColors.categoryHues[i], ArcaLightColors.category(hex))
            assertEquals(hex, ArcaDarkColors.categoryHues[i], ArcaDarkColors.category(hex))
        }
    }

    @Test
    fun aHexOutsideThePalette_isDrawnAsStored_andGarbageFallsBackToInk() {
        assertEquals(Color(0xFF123456), ArcaDarkColors.category("#123456"))
        assertEquals(ArcaDarkColors.inkSecondary, ArcaDarkColors.category("not a colour"))
    }

    @Test
    fun rollingNumberEasing_runsFrom0To1WithoutGoingBack() {
        val easing = ArcaMotionTokens.rollingNumberEasing
        assertEquals(0f, easing.transform(0f), 0f)
        assertEquals(1f, easing.transform(1f), 0f)
        val samples = (0..100).map { easing.transform(it / 100f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a })
    }

    // The window background (res/values*/colors.xml) is drawn before Compose; it must stay the
    // same colour as the Compose surface or launch flashes.
    @Test
    fun windowColours_matchTheTokens() {
        assertEquals(ArcaLightColors.surface.toArgb(), xmlColor("values", "arca_surface"))
        assertEquals(ArcaLightColors.ink.toArgb(), xmlColor("values", "arca_ink"))
        assertEquals(ArcaDarkColors.surface.toArgb(), xmlColor("values-night", "arca_surface"))
        assertEquals(ArcaDarkColors.ink.toArgb(), xmlColor("values-night", "arca_ink"))
    }

    private fun xmlColor(folder: String, name: String): Int {
        val xml = File("src/main/res/$folder/colors.xml").readText()
        val hex = Regex("""<color name="$name">#([0-9A-Fa-f]{6})</color>""").find(xml)!!.groupValues[1]
        return (0xFF000000 or hex.toLong(16)).toInt()
    }
}
