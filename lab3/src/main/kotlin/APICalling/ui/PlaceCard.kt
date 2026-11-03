package APICalling.ui

import APICalling.model.Place
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

class PlaceCard(
    private val number: Int,
    private val place: Place,
    private val image: BufferedImage?,
) : RoundedPanel(24, Theme.card) {

    private var expanded = false
    private var hover = false

    init {
        alignmentX = LEFT_ALIGNMENT
        border = BorderFactory.createEmptyBorder(14, 14, 14, 18)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                hover = true
                refreshLook()
            }

            override fun mouseExited(e: MouseEvent) {
                hover = false
                refreshLook()
            }

            override fun mouseClicked(e: MouseEvent) {
                if (!expanded) open()
            }
        })
        build()
    }

    fun open() = setExpanded(true)

    fun close() = setExpanded(false)

    private fun setExpanded(value: Boolean) {
        expanded = value
        hover = false
        build()
        // раскрытая карточка должна быть целиком видна
        SwingUtilities.invokeLater { scrollRectToVisible(Rectangle(0, 0, width, height)) }
    }

    private fun refreshLook() {
        fill = if (!expanded && hover) Theme.cardHover else Theme.card
        repaint()
    }

    private fun build() {
        removeAll()
        layout = BorderLayout(18, 0)
        cursor = if (expanded) Cursor.getDefaultCursor() else Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        stroke = if (expanded) Theme.accent else Theme.border
        fill = Theme.card
        if (expanded) buildExpanded() else buildCollapsed()
        revalidate()
        repaint()
    }

    private fun titleRow(): JComponent = rowOf(
        10,
        NumberBadge(number, 26),
        JLabel(place.title).apply {
            font = Theme.font(18f, true)
            foreground = Theme.text
        },
        Chip(Fmt.distance(place.distanceM), Theme.accent2),
    )

    private fun buildCollapsed() {
        add(PlaceImage(place.title, image, 132), BorderLayout.WEST)

        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        body.add(titleRow())
        body.add(vgap(8))
        val text = place.description?.let { Fmt.excerpt(it) }
        body.add(
            WrapText(
                text ?: "Описание в Википедии отсутствует.",
                Theme.font(13f),
                if (text == null) Theme.hint else Theme.textDim,
                460,
            )
        )
        body.add(vgap(8))
        body.add(JLabel("Подробнее  ›").left().apply {
            font = Theme.font(12f, true)
            foreground = Theme.accent
        })
        add(top(body), BorderLayout.CENTER)
    }

    private fun buildExpanded() {
        add(top(PlaceImage(place.title, image, 200)), BorderLayout.WEST)

        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        body.add(JPanel(BorderLayout(10, 0)).left().apply {
            isOpaque = false
            add(titleRow(), BorderLayout.CENTER)
            add(CloseButton { close() }, BorderLayout.EAST)
        })
        body.add(vgap(12))
        body.add(
            WrapText(
                place.description ?: "Описание в Википедии отсутствует.",
                Theme.font(14f),
                if (place.description == null) Theme.hint else Theme.text,
                440,
            )
        )
        body.add(vgap(16))
        body.add(rowOf(0, AccentButton("Открыть в Википедии", compact = true).apply {
            addActionListener { openArticle() }
        }))
        add(top(body), BorderLayout.CENTER)
    }

    private fun top(c: JComponent): JComponent = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(c, BorderLayout.NORTH)
    }

    private fun openArticle() {
        val uri = URI("https://ru.wikipedia.org/?curid=${place.pageId}")
        Thread {
            try {
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().browse(uri)
            } catch (e: Exception) {
            }
        }.start()
    }
}
