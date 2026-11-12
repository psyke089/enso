package app.enso.meditation

import androidx.annotation.RawRes
import androidx.annotation.StringRes

/**
 * Selectable local gong recordings. Adding a future gong is one entry here plus one
 * file in `res/raw`; the player never resolves resources by name, so R8 stays safe.
 */
enum class GongChoice(@StringRes val labelRes: Int, @RawRes val soundRes: Int) {
    Root(R.string.gong_root, R.raw.gong_root),
    RootDeep(R.string.gong_root_deep, R.raw.gong_root_deep),
    Sacral(R.string.gong_sacral, R.raw.gong_sacral),
    SolarPlexus(R.string.gong_solar_plexus, R.raw.gong_solar),
    Heart(R.string.gong_heart, R.raw.gong_heart),
    Throat(R.string.gong_throat, R.raw.gong_throat),
    ThirdEye(R.string.gong_third_eye, R.raw.gong_third_eye),
    Crown(R.string.gong_crown, R.raw.gong_crown),
    Bell(R.string.gong_bell, R.raw.gong_bell);

    fun next(): GongChoice = entries[(ordinal + 1) % entries.size]

    fun previous(): GongChoice = entries[(ordinal + entries.size - 1) % entries.size]

    companion object {
        fun fromName(name: String?): GongChoice? = entries.firstOrNull { it.name == name }
    }
}
