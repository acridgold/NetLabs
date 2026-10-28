package APICalling.ui

import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment

object Theme {
    val bg = Color(0x0B0D1E)
    val bgTop = Color(0x1A1238)
    val card = Color(0x15182F)
    val cardHover = Color(0x1D2145)
    val cardSelected = Color(0x252A5C)
    val field = Color(0x0F1126)
    val border = Color(0x2A2F5C)
    val text = Color(0xF3F4FF)
    val textDim = Color(0x9CA3D0)
    val hint = Color(0x6B72A0)
    val accent = Color(0x7C5CFF)
    val accent2 = Color(0x22D3EE)
    val danger = Color(0xFF6B81)
    val ok = Color(0x4ADE80)

    private val family: String = run {
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        listOf("Inter", "SF Pro Text", "Segoe UI", "Noto Sans", "Ubuntu", "Cantarell", "DejaVu Sans")
            .firstOrNull { it in available } ?: Font.SANS_SERIF
    }

    fun font(size: Float, bold: Boolean = false): Font =
        Font(family, if (bold) Font.BOLD else Font.PLAIN, 12).deriveFont(size)
}
