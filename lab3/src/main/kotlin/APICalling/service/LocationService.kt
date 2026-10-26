package APICalling.service

import APICalling.api.GeocodingApi
import APICalling.model.Location
import java.util.concurrent.CompletableFuture

class LocationService(private val geocoding: GeocodingApi) {
    fun searchLocations(query: String): CompletableFuture<List<Location>> = geocoding.search(query)
}