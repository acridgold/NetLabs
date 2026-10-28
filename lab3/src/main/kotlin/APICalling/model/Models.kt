package APICalling.model

data class Location(
    val name: String,
    val street: String?,
    val houseNumber: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val postcode: String?,
    val osmKey: String?,
    val osmValue: String?,
    val osmType: String?,
    val osmId: Long?,
    val lat: Double,
    val lon: Double,
) {
    val uniqueKey: String get() = "${osmType.orEmpty()}${osmId ?: "$lat,$lon"}"

    val address: String
        get() {
            val streetLine = listOfNotNull(street, houseNumber).joinToString(" ").ifBlank { null }
            return listOfNotNull(streetLine?.takeIf { it != name }, city?.takeIf { it != name }, state, country)
                .distinct().joinToString(", ")
        }
}

data class Weather(
    val tempC: Double,
    val windMs: Double,
    val humidity: Double,
    val pressureMmHg: Double?,
    val symbol: String?,
    val time: String,
)

data class PlaceRef(val pageId: Long, val title: String, val distanceM: Int)

class Place(
    val title: String,
    val distanceM: Int,
    val description: String?,
    val imageUrl: String?,
    val imageBytes: ByteArray?,
)

data class Report(
    val location: Location,
    val radiusM: Int,
    val weather: Outcome<Weather>,
    val places: Outcome<List<Place>>,
)
