package APICalling.service

import APICalling.api.GeocodingApi
import APICalling.model.Location
import APICalling.net.RequestScope
import APICalling.net.cancelScopeOnCancel
import java.util.concurrent.CompletableFuture

class LocationService(private val geocoding: GeocodingApi) {
    fun searchLocations(query: String): CompletableFuture<List<Location>> {
        val scope = RequestScope()
        return geocoding.search(query, scope).cancelScopeOnCancel(scope)
    }
}