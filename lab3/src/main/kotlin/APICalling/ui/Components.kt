package APICalling.ui

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.Timer
import javax.swing.plaf.basic.BasicScrollBarUI
import javax.swing.text.JTextComponent
import kotlin.math.max

internal fun Graphics2D.smooth() {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
}

internal fun Color.alpha(a: Int): Color = Color(red, green, blue, a)

internal fun <T : JComponent> T.left(): T = apply { alignmentX = Component.LEFT_ALIGNMENT }

internal fun vgap(px: Int): JComponent =
    (Box.createVerticalStrut(px) as JComponent).left()

internal fun rowOf(gap: Int, vararg parts: JComponent): JPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    isOpaque = false
    alignmentX = Component.LEFT_ALIGNMENT
    parts.forEachIndexed { index, part ->
        if (index > 0) add(Box.createHorizontalStrut(gap))
        part.alignmentY = Component.CENTER_ALIGNMENT
        add(part)
    }
    add(Box.createHorizontalGlue())
}

open class RoundedPanel(
    private val arc: Int = 22,
    var fill: Color = Theme.card,
    var stroke: Color? = Theme.border,
) : JPanel() {
    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.smooth()
        g2.color = fill
        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
        val s = stroke
        if (s != null) {
            g2.color = s
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
        }
        g2.dispose()
        super.paintComponent(g)
    }
}


open class GradientPanel : JPanel() {
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.smooth()
        g2.paint = GradientPaint(0f, 0f, Theme.bgTop, width.toFloat(), height.toFloat(), Theme.bg)
        g2.fillRect(0, 0, width, height)
        glow(g2, width * 0.12f, -30f, 420f, Theme.accent, 80)
        glow(g2, width * 0.95f, height * 0.9f, 460f, Theme.accent2, 46)
        g2.dispose()
    }

    private fun glow(g2: Graphics2D, cx: Float, cy: Float, r: Float, color: Color, alpha: Int) {
        g2.paint = RadialGradientPaint(
            cx, cy, r,
            floatArrayOf(0f, 1f),
            arrayOf(color.alpha(alpha), color.alpha(0)),
        )
        g2.fill(Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2))
    }
}

class AccentButton(text: String) : JButton(text) {
    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        foreground = Color.WHITE
        font = Theme.font(14f, true)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = BorderFactory.createEmptyBorder(11, 22, 11, 22)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.smooth()
        val pressed = model.isPressed
        val a = if (!isEnabled) Theme.border else if (pressed) Theme.accent.darker() else Theme.accent
        val b = if (!isEnabled) Theme.border else if (pressed) Theme.accent2.darker() else Theme.accent2
        g2.paint = GradientPaint(0f, 0f, a, width.toFloat(), height.toFloat(), b)
        g2.fillRoundRect(0, 0, width, height, 18, 18)
        if (model.isRollover && isEnabled) {
            g2.color = Color(255, 255, 255, 30)
            g2.fillRoundRect(0, 0, width, height, 18, 18)
        }
        g2.dispose()
        super.paintComponent(g)
    }
}

private fun JTextComponent.stylize() {
    isOpaque = false
    foreground = Theme.text
    caretColor = Theme.accent2
    selectionColor = Theme.accent
    selectedTextColor = Color.WHITE
    font = Theme.font(14f)
    border = BorderFactory.createEmptyBorder(11, 16, 11, 16)
    addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent) = repaint()
        override fun focusLost(e: FocusEvent) = repaint()
    })
}

private fun paintFieldChrome(g: Graphics, c: JComponent) {
    val g2 = g.create() as Graphics2D
    g2.smooth()
    g2.color = Theme.field
    g2.fillRoundRect(0, 0, c.width - 1, c.height - 1, 18, 18)
    g2.color = if (c.hasFocus()) Theme.accent else Theme.border
    g2.stroke = BasicStroke(if (c.hasFocus()) 1.6f else 1f)
    g2.drawRoundRect(0, 0, c.width - 1, c.height - 1, 18, 18)
    g2.dispose()
}

private fun paintPlaceholder(g: Graphics, c: JTextComponent, hint: String) {
    if (c.document.length > 0) return
    val g2 = g.create() as Graphics2D
    g2.smooth()
    g2.font = c.font
    g2.color = Theme.hint
    val fm = g2.fontMetrics
    g2.drawString(hint, c.insets.left, (c.height - fm.height) / 2 + fm.ascent)
    g2.dispose()
}

class RoundedField(private val hint: String) : JTextField() {
    init {
        stylize()
    }

    override fun paintComponent(g: Graphics) {
        paintFieldChrome(g, this)
        super.paintComponent(g)
        paintPlaceholder(g, this, hint)
    }
}

class RoundedPasswordField(private val hint: String) : JPasswordField() {
    init {
        stylize()
    }

    override fun paintComponent(g: Graphics) {
        paintFieldChrome(g, this)
        super.paintComponent(g)
        paintPlaceholder(g, this, hint)
    }
}

class Chip(text: String, private val tint: Color = Theme.accent2) : JLabel(text) {
    init {
        foreground = tint
        font = Theme.font(11f, true)
        border = BorderFactory.createEmptyBorder(3, 10, 3, 10)
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.smooth()
        g2.color = tint.alpha(40)
        g2.fillRoundRect(0, 0, width, height, height, height)
        g2.dispose()
        super.paintComponent(g)
    }
}

class ChipToggle(text: String) : JToggleButton(text) {
    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        font = Theme.font(12f, true)
        border = BorderFactory.createEmptyBorder(6, 13, 6, 13)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        foreground = Theme.textDim
        addItemListener { foreground = if (isSelected) Color.WHITE else Theme.textDim }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.smooth()
        if (isSelected) {
            g2.paint = GradientPaint(0f, 0f, Theme.accent, width.toFloat(), height.toFloat(), Theme.accent2)
            g2.fillRoundRect(0, 0, width, height, height, height)
        } else {
            g2.color = if (model.isRollover) Theme.cardHover else Theme.field
            g2.fillRoundRect(0, 0, width - 1, height - 1, height, height)
            g2.color = Theme.border
            g2.drawRoundRect(0, 0, width - 1, height - 1, height, height)
        }
        g2.dispose()
        foreground = if (isSelected) Color.WHITE else Theme.textDim
        super.paintComponent(g)
    }
}

class Spinner(private val size: Int = 48) : JComponent() {
    private var angle = 0
    private val timer = Timer(28) {
        angle = (angle + 12) % 360
        repaint()
    }

    init {
        preferredSize = Dimension(size, size)
        isOpaque = false
    }

    override fun addNotify() {
        super.addNotify()
        timer.start()
    }

    override fun removeNotify() {
        timer.stop()
        super.removeNotify()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.smooth()
        g2.stroke = BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.color = Theme.border
        g2.drawOval(5, 5, size - 10, size - 10)
        g2.color = Theme.accent2
        g2.drawArc(5, 5, size - 10, size - 10, -angle, 100)
        g2.color = Theme.accent
        g2.drawArc(5, 5, size - 10, size - 10, -angle + 180, 70)
        g2.dispose()
    }
}

class Orb : JComponent() {
    init {
        preferredSize = Dimension(150, 150)
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.smooth()
        val cx = width / 2f
        val cy = height / 2f
        g2.paint = RadialGradientPaint(
            cx, cy, 70f,
            floatArrayOf(0f, 1f),
            arrayOf(Theme.accent.alpha(150), Theme.accent.alpha(0)),
        )
        g2.fill(Ellipse2D.Float(cx - 70, cy - 70, 140f, 140f))
        g2.stroke = BasicStroke(1.4f)
        g2.color = Theme.accent2.alpha(120)
        g2.drawOval((cx - 52).toInt(), (cy - 52).toInt(), 104, 104)
        g2.color = Theme.accent.alpha(140)
        g2.drawOval((cx - 34).toInt(), (cy - 34).toInt(), 68, 68)
        g2.paint = GradientPaint(cx - 14, cy - 14, Theme.accent, cx + 14, cy + 14, Theme.accent2)
        g2.fillOval((cx - 12).toInt(), (cy - 12).toInt(), 24, 24)
        g2.dispose()
    }
}

class PlaceImage(private val title: String, private val image: BufferedImage?, private val side: Int = 132) : JComponent() {
    init {
        val d = Dimension(side, side)
        preferredSize = d
        minimumSize = d
        maximumSize = d
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.smooth()
        val shape = RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), 22f, 22f)
        g2.clip = shape
        val img = image
        if (img != null) {
            val scale = max(width / img.width.toDouble(), height / img.height.toDouble())
            val w = (img.width * scale).toInt()
            val h = (img.height * scale).toInt()
            g2.drawImage(img, (width - w) / 2, (height - h) / 2, w, h, null)
        } else {
            g2.paint = GradientPaint(0f, 0f, Theme.accent.alpha(200), width.toFloat(), height.toFloat(), Theme.accent2.alpha(200))
            g2.fillRect(0, 0, width, height)
            val letter = title.firstOrNull { it.isLetter() }?.uppercase() ?: "•"
            g2.font = Theme.font(48f, true)
            g2.color = Color(255, 255, 255, 220)
            val fm = g2.fontMetrics
            g2.drawString(letter, (width - fm.stringWidth(letter)) / 2, (height - fm.height) / 2 + fm.ascent)
        }
        g2.dispose()
    }
}

class ThinScrollBarUI : BasicScrollBarUI() {
    override fun configureScrollBarColors() {
        thumbColor = Theme.border
        trackColor = Theme.bg
    }

    override fun createDecreaseButton(orientation: Int): JButton = zeroButton()

    override fun createIncreaseButton(orientation: Int): JButton = zeroButton()

    private fun zeroButton(): JButton = JButton().apply {
        val d = Dimension(0, 0)
        preferredSize = d
        minimumSize = d
        maximumSize = d
    }

    override fun paintTrack(g: Graphics, c: JComponent, trackBounds: Rectangle) {}

    override fun paintThumb(g: Graphics, c: JComponent, thumbBounds: Rectangle) {
        if (thumbBounds.isEmpty || !scrollbar.isEnabled) return
        val g2 = g.create() as Graphics2D
        g2.smooth()
        g2.color = Theme.accent.alpha(150)
        g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2, thumbBounds.width - 4, thumbBounds.height - 4, 8, 8)
        g2.dispose()
    }
}

fun styledScroll(view: Component): JScrollPane = JScrollPane(view).apply {
    border = BorderFactory.createEmptyBorder()
    isOpaque = false
    viewport.isOpaque = false
    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    verticalScrollBar.setUI(ThinScrollBarUI())
    verticalScrollBar.preferredSize = Dimension(10, 0)
    verticalScrollBar.unitIncrement = 18
    verticalScrollBar.isOpaque = false
}

class ColumnPanel : JPanel(), Scrollable {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = 18
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.VERTICAL) visibleRect.height - 40 else visibleRect.width
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false
}


class WrapText(
    private var content: String,
    textFont: Font,
    color: Color,
    private val prefWidth: Int = 200,
    private val centered: Boolean = false,
) : JComponent() {
    private var knownWidth = 0
    private var cachedWidth = -1
    private var cachedLines: List<String> = emptyList()

    init {
        font = textFont
        foreground = color
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    fun setContent(text: String) {
        content = text
        cachedWidth = -1
        revalidate()
        repaint()
    }

    private fun lines(width: Int): List<String> {
        if (width == cachedWidth) return cachedLines
        val fm = getFontMetrics(font)
        val result = mutableListOf<String>()
        for (paragraph in content.split('\n')) {
            var line = StringBuilder()
            for (word in paragraph.split(' ')) {
                if (line.isEmpty()) {
                    line.append(word)
                } else if (fm.stringWidth("$line $word") <= width) {
                    line.append(' ').append(word)
                } else {
                    result += line.toString()
                    line = StringBuilder(word)
                }
            }
            result += line.toString()
        }
        cachedWidth = width
        cachedLines = result
        return result
    }

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        val w = if (knownWidth > 0) knownWidth else prefWidth
        return Dimension(prefWidth, lines(w).size * fm.height + 2)
    }

    override fun getMinimumSize(): Dimension = Dimension(10, preferredSize.height)

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun setBounds(x: Int, y: Int, w: Int, h: Int) {
        super.setBounds(x, y, w, h)
        if (w > 0 && w != knownWidth) {
            knownWidth = w
            if (preferredSize.height != h) revalidate()
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.smooth()
        g2.font = font
        g2.color = foreground
        val fm = g2.fontMetrics
        var y = fm.ascent
        for (line in lines(maxOf(width, 1))) {
            val x = if (centered) (width - fm.stringWidth(line)) / 2 else 0
            g2.drawString(line, x, y)
            y += fm.height
        }
        g2.dispose()
    }
}
