package APICalling.ui

import APICalling.Config
import APICalling.errorText
import APICalling.isCancellation
import APICalling.model.Location
import APICalling.model.Report
import APICalling.service.LocationService
import APICalling.service.ReportService
import java.awt.*
import java.util.concurrent.CompletableFuture
import javax.swing.*

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

    private var searchFuture: CompletableFuture<*>? = null
    private var reportFuture: CompletableFuture<*>? = null

    init {
        layout = BorderLayout(0, 16)
        border = BorderFactory.createEmptyBorder(22, 26, 14, 26)

        keyField.text = Config.graphHopperKey

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
        column.add(
            JLabel("<html><span style='color:#F3F4FF'>Geo</span><span style='color:#7C5CFF'>Brief</span></html>").left()
                .apply {
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

        Config.graphHopperKey = String(keyField.password).trim() // только в памяти

        val seq = ++searchSeq
        selected = null
        reportSeq++
        searchFuture?.cancel(true)
        reportFuture?.cancel(true)
        reportView.showEmpty()
        results.showMessage("Ищу «$query»…")
        setStatus("Поиск локаций…", Theme.accent2)
        searchButton.isEnabled = false

        val future = locationService.searchLocations(query)
        searchFuture = future
        future.whenComplete { list, error ->
            SwingUtilities.invokeLater {
                if (isCancellation(error) || seq != searchSeq) return@invokeLater
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
        reportFuture?.cancel(true)
        reportView.showLoading(location)
        setStatus("Загружаю прогноз и места…", Theme.accent2)

        val future = reportService.loadReport(location, radiusM)
        reportFuture = future
        future.whenComplete { report, error ->
            SwingUtilities.invokeLater {
                if (isCancellation(error) || seq != reportSeq) return@invokeLater
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
        val problems = mutableListOf<String>()
        report.weather.error?.let { problems += "прогноз: $it" }
        report.places.error?.let { problems += "места: $it" }

        val places = report.places.value.orEmpty()
        val withoutText = places.count { it.error != null }
        val withoutImage = places.count { it.imageError != null }
        if (withoutText > 0) problems += "описания не загружены: $withoutText из ${places.size}"
        if (withoutImage > 0) problems += "картинки не загружены: $withoutImage из ${places.size}"

        when {
            problems.isEmpty() -> setStatus("Готово: ${report.location.name}", Theme.ok)
            else -> {
                val severe = (report.weather.error != null && report.places.error != null) ||
                        (places.isNotEmpty() && withoutText == places.size)
                setStatus(
                    "Загружено с ошибками — ${problems.joinToString("; ")}",
                    if (severe) Theme.danger else Theme.warn
                )
            }
        }
    }

    private fun setStatus(text: String, color: Color) {
        status.text = "●  $text"
        status.foreground = color
    }

    private fun messageOf(error: Throwable): String = errorText(error)
}
