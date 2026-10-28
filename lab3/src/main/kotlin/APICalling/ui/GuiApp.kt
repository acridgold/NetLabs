package APICalling.ui

import APICalling.api.DescriptionApi
import APICalling.api.GeocodingApi
import APICalling.api.PlacesApi
import APICalling.api.WeatherApi
import APICalling.net.JsonHttpClient
import APICalling.service.LocationService
import APICalling.service.ReportService
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.UIManager

class GuiApp(
    private val locationService: LocationService,
    private val reportService: ReportService,
) {
    fun run() {
        System.setProperty("awt.useSystemAAFontSettings", "on")
        System.setProperty("swing.aatext", "true")
        SwingUtilities.invokeLater {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName())
            } catch (e: Exception) {
            }
            JFrame("GeoBrief — погода и места рядом").apply {
                defaultCloseOperation = JFrame.EXIT_ON_CLOSE
                contentPane = MainPanel(locationService, reportService)
                minimumSize = Dimension(1060, 700)
                setSize(1260, 820)
                setLocationRelativeTo(null)
                isVisible = true
            }
        }
    }

    companion object {
        fun create(): GuiApp {
            val http = JsonHttpClient()
            return GuiApp(
                LocationService(GeocodingApi(http)),
                ReportService(WeatherApi(http), PlacesApi(http), DescriptionApi(http)),
            )
        }
    }
}
