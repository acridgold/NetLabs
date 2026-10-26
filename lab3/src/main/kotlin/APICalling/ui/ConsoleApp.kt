package APICalling.ui

import APICalling.api.DescriptionApi
import APICalling.api.GeocodingApi
import APICalling.api.PlacesApi
import APICalling.api.WeatherApi
import APICalling.net.JsonHttpClient
import APICalling.service.LocationService
import APICalling.service.ReportService
import java.util.concurrent.CompletionException

class ConsoleApp(
    private val locationService: LocationService,
    private val reportService: ReportService,
) {
    fun run() {
        print("Введите название места: ")
        val query = readlnOrNull()?.trim().orEmpty()
        if (query.isEmpty()) return

        try {
            val locations = locationService.searchLocations(query).join()
            if (locations.isEmpty()) {
                println("Ничего не найдено.")
                return
            }

            for ((i, l) in locations.withIndex()) {
                println("${i + 1}. $l")
            }
            print("Выберите номер: ")
            val choice: Int = readlnOrNull()?.trim()?.toIntOrNull() ?: 0
            if (choice < 1 || choice > locations.size) {
                println("Некорректный выбор.")
                return
            }

            println("Загружаю данные...")
            val report = reportService.loadReport(locations[choice - 1]).join()
            println(ReportFormatter.format(report))
        } catch (e: CompletionException) {
            System.err.println("Ошибка: ${e.cause?.message ?: e.message}")
        }
    }

    companion object {
        fun create(): ConsoleApp {
            val http = JsonHttpClient()
            return ConsoleApp(
                LocationService(GeocodingApi(http)),
                ReportService(WeatherApi(http), PlacesApi(http), DescriptionApi(http)),
            )
        }
    }
}