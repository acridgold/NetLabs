package APICalling

object Config {
    // Для MET Norway и Wikimedia
    const val USER_AGENT = "PENINSULA"

    const val SEARCH_RADIUS_M = 1500
    const val MAX_PLACES = 10

    const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"                       // [1] Open-Meteo Geocoding
    const val WEATHER_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact"             // [2] MET Norway
    const val PLACES_URL = "https://query.wikidata.org/sparql"                                       // [3] Wikidata SPARQL
    const val DESCRIPTION_URL = "https://www.wikidata.org/w/api.php"                                 // [4] Wikidata wbgetentities
}