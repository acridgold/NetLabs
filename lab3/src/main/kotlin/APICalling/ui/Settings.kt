package APICalling.ui

import APICalling.Config
import java.util.prefs.Preferences

object Settings {
    private val prefs: Preferences? = try {
        Preferences.userRoot().node("geobrief-lab3")
    } catch (e: Exception) {
        null
    }

    var graphHopperKey: String
        get() = prefs?.get("gh_key", "").orEmpty()
        set(value) {
            try {
                prefs?.put("gh_key", value)
            } catch (e: Exception) {
            }
        }

    var radiusM: Int
        get() = prefs?.getInt("radius", Config.DEFAULT_RADIUS_M) ?: Config.DEFAULT_RADIUS_M
        set(value) {
            try {
                prefs?.putInt("radius", value)
            } catch (e: Exception) {
            }
        }
}
