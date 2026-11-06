package APICalling

import java.io.File
import java.util.*

object Config {
    const val USER_AGENT = "GeoBriefKt/1.0 (https://github.com/AcridGold)"

    // Поиск мест
    const val DEFAULT_RADIUS_M = 1500
    const val MAX_RADIUS_M = 10_000      // ограничение Wikipedia GeoData
    const val MAX_PLACES = 10
    const val GEOCODE_LIMIT = 10
    const val IMAGE_SIZE_PX = 480        // ширина миниатюры из Википедии

    const val REQUEST_TIMEOUT_S = 15L
    const val IMAGE_TIMEOUT_S = 6L

    const val GEOCODING_URL =
        "https://graphhopper.com/api/1/geocode"                          // [1] GraphHopper Geocoding
    const val WEATHER_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact"       // [2] MET Norway
    const val PLACES_URL = "https://ru.wikipedia.org/w/api.php"                                // [3] Wikipedia GeoData
    const val DESCRIPTION_URL =
        "https://ru.wikipedia.org/w/api.php"                           // [4] Wikipedia TextExtracts + PageImages

    const val HPA_TO_MMHG = 0.750062

    private const val SECRETS_FILE = "secrets.properties"

    @Volatile
    var graphHopperKey: String = loadKey()

    private fun loadKey(): String {
        System.getenv("GRAPHHOPPER_KEY")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return try {
            val file = File(SECRETS_FILE)
            if (!file.isFile) "" else {
                val props = Properties()
                file.inputStream().use { props.load(it) }
                props.getProperty("graphhopper.key", "").trim()
            }
        } catch (e: Exception) {
            ""
        }
    }
}
