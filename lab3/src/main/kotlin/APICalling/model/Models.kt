package APICalling.model

import java.util.Locale

data class Location(
    val name: String,
    val admin: String?,
    val country: String?,
    val lat: Double,
    val lon: Double,
) {
    override fun toString() =
        listOfNotNull(name, admin, country).distinct().joinToString(", ") +
                "  [${"%.4f".format(Locale.US, lat)}, ${"%.4f".format(Locale.US, lon)}]"
}

data class Weather(
    val tempC: Double,
    val windMs: Double,
    val humidity: Double,
    val symbol: String?,
    val time: String,
)

data class PlaceRef(val name: String, val kind: String, val wikidataId: String)

data class Place(val name: String, val kind: String, val description: String?)

data class Report(
    val location: Location,
    val weather: Outcome<Weather>,
    val places: Outcome<List<Place>>,
)