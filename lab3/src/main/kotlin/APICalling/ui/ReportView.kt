package APICalling.ui

import APICalling.model.Location
import APICalling.model.Outcome
import APICalling.model.Report
import APICalling.model.Weather
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridBagLayout
import java.awt.GridLayout
import javax.swing.*

class ReportView : JPanel(BorderLayout()) {
    init {
        isOpaque = false
        showEmpty()
    }

    private fun setContent(c: JComponent) {
        removeAll()
        add(c, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun centered(vararg parts: JComponent): JComponent {
        val column = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        for (p in parts) {
            p.alignmentX = CENTER_ALIGNMENT
            column.add(p)
        }
        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(column)
        }
    }

    private fun title(text: String, size: Float = 26f, color: Color = Theme.text) = JLabel(text).apply {
        font = Theme.font(size, true)
        foreground = color
    }

    private fun hint(text: String, color: Color = Theme.textDim): JComponent =
        WrapText(text, Theme.font(14f), color, 380, centered = true)

    fun showEmpty() = setContent(
        centered(
            Orb(),
            vgap(8),
            title("Найдите место"),
            vgap(8),
            hint("Введите название слева, выберите вариант из списка — покажем погоду и интересные места рядом."),
        )
    )

    fun showLoading(location: Location) = setContent(
        centered(
            Spinner(),
            vgap(14),
            title("Загружаю данные", 20f),
            vgap(6),
            hint(location.name),
        )
    )

    fun showError(message: String) = setContent(
        centered(
            title("Не получилось", 24f, Theme.danger),
            vgap(8),
            hint(message),
        )
    )

    fun showReport(report: Report) {
        val col = ColumnPanel().apply { border = BorderFactory.createEmptyBorder(0, 0, 16, 12) }
        col.add(heroCard(report.location))
        col.add(vgap(14))
        col.add(weatherCard(report.weather))
        col.add(vgap(24))
        col.add(placesHeader(report))
        col.add(vgap(12))

        val places = report.places.value
        when {
            places == null -> col.add(messageCard("Не удалось получить места: ${report.places.error}", Theme.danger))
            places.isEmpty() -> col.add(
                messageCard("В радиусе ${Fmt.radius(report.radiusM)} в Википедии ничего не нашлось. Попробуйте увеличить радиус.")
            )

            else -> places.forEachIndexed { index, place ->
                col.add(PlaceCard(index + 1, place))
                col.add(vgap(12))
            }
        }

        val scroll = styledScroll(col)
        setContent(scroll)
        SwingUtilities.invokeLater { scroll.verticalScrollBar.value = 0 }
    }

    // ---------- блоки отчёта ----------

    private fun card(arc: Int = 26) = RoundedPanel(arc, Theme.card).left().apply {
        border = BorderFactory.createEmptyBorder(20, 24, 20, 24)
    }

    private fun heroCard(l: Location): JComponent {
        val card = card().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        card.add(
            rowOf(
                6, *listOfNotNull(
                    l.osmKey?.let { Chip(OsmTags.key(it), Theme.textDim) },
                    l.osmValue?.let { Chip(OsmTags.value(it), Theme.accent2) },
                ).toTypedArray()
            )
        )
        card.add(vgap(10))
        card.add(WrapText(l.name, Theme.font(30f, true), Theme.text, 560))
        if (l.address.isNotEmpty()) {
            card.add(vgap(4))
            card.add(WrapText(l.address, Theme.font(14f), Theme.textDim, 560))
        }
        card.add(vgap(8))
        card.add(JLabel("%.5f°, %.5f°".format(java.util.Locale.US, l.lat, l.lon)).left().apply {
            font = Theme.font(12f)
            foreground = Theme.hint
        })
        return card
    }

    private fun weatherCard(outcome: Outcome<Weather>): JComponent {
        val card = card().apply { layout = BorderLayout(28, 0) }
        val w = outcome.value
        if (w == null) {
            card.add(
                WrapText("Прогноз недоступен: ${outcome.error.orEmpty()}", Theme.font(14f), Theme.danger, 520),
                BorderLayout.CENTER,
            )
            return card
        }

        val left = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        left.add(JLabel("ПРОГНОЗ НА ${Fmt.updated(w.time).uppercase()}").left().apply {
            font = Theme.font(11f, true)
            foreground = Theme.hint
        })
        left.add(JLabel(Fmt.temp(w.tempC)).left().apply {
            font = Theme.font(54f, true)
            foreground = Theme.text
        })
        Fmt.condition(w.symbol)?.let {
            left.add(JLabel(it).left().apply {
                font = Theme.font(15f)
                foreground = Theme.accent2
            })
        }
        left.add(vgap(4))
        left.add(JLabel("Ощущается как ${Fmt.temp(w.feelsLikeC)}").left().apply {
            font = Theme.font(13f)
            foreground = Theme.textDim
        })
        left.add(vgap(6))
        left.add(JLabel("Прогноз MET Norway, не измерение").left().apply {
            font = Theme.font(11f)
            foreground = Theme.hint
        })

        val tiles = JPanel(GridLayout(1, 3, 12, 0)).apply { isOpaque = false }
        tiles.add(tile("Ветер", Fmt.number(w.windMs), "м/с"))
        tiles.add(tile("Влажность", Fmt.number(w.humidity, 0), "%"))
        tiles.add(tile("Давление", w.pressureMmHg?.let { Fmt.number(it, 0) } ?: "—", "мм рт. ст."))

        card.add(left, BorderLayout.WEST)
        card.add(tiles, BorderLayout.CENTER)
        return card
    }

    private fun tile(label: String, value: String, unit: String): JComponent {
        val tile = RoundedPanel(20, Theme.field, Theme.border).apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        }
        tile.add(JLabel(label).left().apply {
            font = Theme.font(12f)
            foreground = Theme.textDim
        })
        tile.add(vgap(6))
        tile.add(JLabel(value).left().apply {
            font = Theme.font(28f, true)
            foreground = Theme.text
        })
        tile.add(JLabel(unit).left().apply {
            font = Theme.font(12f)
            foreground = Theme.accent
        })
        return tile
    }

    private fun placesHeader(report: Report): JComponent {
        val heading = JLabel("Интересные места").apply {
            font = Theme.font(22f, true)
            foreground = Theme.text
        }
        return rowOf(
            10,
            heading,
            Chip("радиус ${Fmt.radius(report.radiusM)}", Theme.accent),
            *listOfNotNull(
                report.places.value?.let { Chip("найдено ${it.size}", Theme.accent2) },
                report.places.value?.count { it.error != null || it.imageError != null }
                    ?.takeIf { it > 0 }
                    ?.let { Chip("ошибок загрузки: $it", Theme.danger) },
            ).toTypedArray(),
        )
    }

    private fun messageCard(text: String, color: Color = Theme.textDim): JComponent {
        val card = card(22).apply { layout = BorderLayout() }
        card.add(WrapText(text, Theme.font(14f), color, 520), BorderLayout.CENTER)
        return card
    }
}
