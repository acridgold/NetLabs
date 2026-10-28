package APICalling

object Config {
    const val USER_AGENT = "GeoBriefKt/1.0 (https://github.com/AcridGold)"

    // Поиск мест
    const val DEFAULT_RADIUS_M = 1500
    const val MAX_RADIUS_M = 10_000
    const val MAX_PLACES = 10
    const val GEOCODE_LIMIT = 10
    const val IMAGE_SIZE_PX = 320

    const val GEOCODING_URL = "https://graphhopper.com/api/1/geocode"                          // [1] GraphHopper Geocoding
    const val WEATHER_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact"       // [2] MET Norway
    const val PLACES_URL = "https://ru.wikipedia.org/w/api.php"                                // [3] Wikipedia GeoData
    const val DESCRIPTION_URL = "https://ru.wikipedia.org/w/api.php"                           // [4] Wikipedia TextExtracts + PageImages

    const val HPA_TO_MMHG = 0.750062

    private const val DEFAULT_GRAPHHOPPER_KEY = "b332357a-5d07-43ea-9905-8321530a1ac4"

    @Volatile
    var graphHopperKey: String = System.getenv("GRAPHHOPPER_KEY")?.takeIf { it.isNotBlank() } ?: DEFAULT_GRAPHHOPPER_KEY
}
