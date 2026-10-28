package APICalling.ui

import APICalling.Config
import APICalling.model.Location
import APICalling.model.Report
import APICalling.service.LocationService
import APICalling.service.ReportService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.concurrent.CompletionException
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

class MainPanel(
    private val locationService: LocationService,
    private val reportService: ReportService,
) : GradientPanel() {

    private val searchField = RoundedField("Например: Красная площадь")
    private val keyField = RoundedPasswordField("Ключ GraphHopper")
    private val searchButton = AccentButton("Найти")
    internal val results = LocationList { loadReport(it) }
    val reportView = ReportView()
    private val status = JLabel(" ")

    private var radiusM = Settings.radiusM
    private var selected: Location? = null
    private var searchSeq = 0
    private var reportSeq = 0

    init {
        layout = BorderLayout(0, 16)
        border = BorderFactory.createEmptyBorder(22, 26, 14, 26)

        val saved = Settings.graphHopperKey.ifBlank { Config.graphHopperKey }
        Config.graphHopperKey = saved
        keyField.text = saved

        add(buildHeader(), BorderLayout.NORTH)

        val left = JPanel(BorderLayout(0, 14)).apply {
            isOpaque = false
            preferredSize = Dimension(380, 10)
            add(buildSearchCard(), BorderLayout.NORTH)
            add(results, BorderLayout.CENTER)
        }
        val body = JPanel(BorderLayout(22, 0)).apply {
            isOpaque = false
            add(left, BorderLayout.WEST)
            add(reportView, BorderLayout.CENTER)
        }
        add(body, BorderLayout.CENTER)

        status.font = Theme.font(12f)
        add(status, BorderLayout.SOUTH)
        setStatus("Введите название места и нажмите «Найти»", Theme.textDim)

        results.showMessage("Здесь появятся найденные места.")
        searchField.addActionListener { startSearch() }
        searchButton.addActionListener { startSearch() }
    }

    private fun buildHeader(): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        val column = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        column.add(JLabel("<html><span style='color:#F3F4FF'>Geo</span><span style='color:#7C5CFF'>Brief</span></html>").left().apply {
            font = Theme.font(30f, true)
        })
        column.add(JLabel("погода и интересные места рядом").left().apply {
            font = Theme.font(13f)
            foreground = Theme.textDim
        })
        add(column, BorderLayout.WEST)
    }

    private fun gb(x: Int, y: Int, width: Int = 1, weightX: Double = 1.0, top: Int = 0, left: Int = 0) =
        GridBagConstraints().apply {
            gridx = x
            gridy = y
            gridwidth = width
            weightx = weightX
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = Insets(top, left, 0, 0)
        }

    private fun caption(text: String) = JLabel(text).apply {
        font = Theme.font(12f, true)
        foreground = Theme.textDim
    }

    private fun buildSearchCard(): RoundedPanel {
        val card = RoundedPanel(24, Theme.card).apply {
            layout = GridBagLayout()
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        }
        card.add(searchField, gb(0, 0))
        card.add(searchButton, gb(1, 0, weightX = 0.0, left = 10))

        card.add(caption("Радиус поиска мест"), gb(0, 1, width = 2, top = 16))
        val group = ButtonGroup()
        val toggles = listOf(500, 1000, 1500, 5000, 10000).map { value ->
            ChipToggle(Fmt.radius(value)).also { toggle ->
                toggle.isSelected = value == radiusM
                toggle.addActionListener { onRadiusChanged(value) }
                group.add(toggle)
            }
        }
        card.add(rowOf(6, *toggles.toTypedArray()), gb(0, 2, width = 2, top = 8))

        card.add(caption("Ключ GraphHopper"), gb(0, 3, width = 2, top = 16))
        card.add(keyField, gb(0, 4, width = 2, top = 8))
        return card
    }

    // ---------- действия ----------

    private fun startSearch() {
        val query = searchField.text.trim()
        if (query.isEmpty()) return

        Config.graphHopperKey = String(keyField.password).trim()
        Settings.graphHopperKey = Config.graphHopperKey

        val seq = ++searchSeq
        selected = null
        reportSeq++
        reportView.showEmpty()
        results.showMessage("Ищу «$query»…")
        setStatus("Поиск локаций…", Theme.accent2)
        searchButton.isEnabled = false

        locationService.searchLocations(query).whenComplete { list, error ->
            SwingUtilities.invokeLater {
                if (seq != searchSeq) return@invokeLater
                searchButton.isEnabled = true
                when {
                    error != null -> {
                        val message = messageOf(error)
                        results.showMessage(message, Theme.danger)
                        setStatus(message, Theme.danger)
                    }
                    list.isNullOrEmpty() -> {
                        results.showMessage("Ничего не найдено. Попробуйте изменить запрос.")
                        setStatus("Ничего не найдено", Theme.textDim)
                    }
                    else -> {
                        results.show(list)
                        setStatus("Найдено вариантов: ${list.size}. Выберите нужный.", Theme.ok)
                    }
                }
            }
        }
    }

    private fun loadReport(location: Location) {
        selected = location
        val seq = ++reportSeq
        reportView.showLoading(location)
        setStatus("Загружаю погоду и места…", Theme.accent2)

        reportService.loadReport(location, radiusM).whenComplete { report, error ->
            SwingUtilities.invokeLater {
                if (seq != reportSeq) return@invokeLater
                if (error != null) {
                    val message = messageOf(error)
                    reportView.showError(message)
                    setStatus(message, Theme.danger)
                } else {
                    reportView.showReport(report)
                    summarize(report)
                }
            }
        }
    }

    private fun onRadiusChanged(value: Int) {
        radiusM = value
        Settings.radiusM = value
        selected?.let { loadReport(it) }
    }

    private fun summarize(report: Report) {
        val problems = listOfNotNull(
            report.weather.error?.let { "погода: $it" },
            report.places.error?.let { "места: $it" },
        )
        if (problems.isEmpty()) {
            setStatus("Готово: ${report.location.name}", Theme.ok)
        } else {
            setStatus("Часть данных недоступна — ${problems.joinToString("; ")}", Theme.danger)
        }
    }

    private fun setStatus(text: String, color: Color) {
        status.text = "●  $text"
        status.foreground = color
    }

    private fun messageOf(error: Throwable): String {
        val e = if (error is CompletionException) error.cause ?: error else error
        return e.message ?: e.javaClass.simpleName
    }
}
