package APICalling.ui

import APICalling.model.Location
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

class LocationList(private val onChoose: (Location) -> Unit) : JPanel(BorderLayout()) {
    private val box = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val cards = mutableListOf<LocationCard>()

    init {
        isOpaque = false
        val top = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(box, BorderLayout.NORTH)
        }
        add(styledScroll(top), BorderLayout.CENTER)
    }

    fun showMessage(text: String, color: Color = Theme.textDim) {
        cards.clear()
        box.removeAll()
        box.add(JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            border = BorderFactory.createEmptyBorder(8, 6, 0, 6)
            add(WrapText(text, Theme.font(13f), color, 40, guessWidth = 330), BorderLayout.CENTER)
        })
        box.revalidate()
        box.repaint()
    }

    fun show(locations: List<Location>) {
        cards.clear()
        box.removeAll()
        for ((index, location) in locations.withIndex()) {
            val card = LocationCard(index + 1, location) { chosen ->
                cards.forEach { it.selected = it.item === chosen }
                onChoose(chosen)
            }
            cards += card
            box.add(card)
            box.add(vgap(8))
        }
        box.revalidate()
        box.repaint()
    }

    fun clearSelection() = cards.forEach { it.selected = false }
}

private class LocationCard(number: Int, val item: Location, private val onClick: (Location) -> Unit) :
    RoundedPanel(20, Theme.card) {

    private var hover = false

    var selected: Boolean = false
        set(value) {
            field = value
            refresh()
        }

    init {
        layout = BorderLayout(12, 0)
        alignmentX = LEFT_ALIGNMENT
        border = BorderFactory.createEmptyBorder(12, 14, 12, 14)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        val col = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        col.add(WrapText(item.name, Theme.font(15f, true), Theme.text, 40, guessWidth = 300))
        val address = item.address
        if (address.isNotEmpty()) {
            col.add(vgap(3))
            col.add(WrapText(address, Theme.font(12f), Theme.textDim, 40, guessWidth = 300))
        }
        col.add(Box.createVerticalStrut(8))
        col.add(
            rowOf(
                6, *listOfNotNull(
                    item.osmKey?.let { Chip(OsmTags.key(it), Theme.textDim) },
                    item.osmValue?.let { Chip(OsmTags.value(it), Theme.accent2) },
                ).toTypedArray()
            )
        )
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(NumberBadge(number), BorderLayout.NORTH)
        }, BorderLayout.WEST)
        add(col, BorderLayout.CENTER)

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                hover = true
                refresh()
            }

            override fun mouseExited(e: MouseEvent) {
                hover = false
                refresh()
            }

            override fun mouseClicked(e: MouseEvent) = onClick(item)
        })
        refresh()
    }

    private fun refresh() {
        fill = when {
            selected -> Theme.cardSelected
            hover -> Theme.cardHover
            else -> Theme.card
        }
        stroke = if (selected) Theme.accent else Theme.border
        repaint()
    }
}
