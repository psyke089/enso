package app.enso.meditation

import androidx.annotation.StringRes

/**
 * Selectable local gong recordings. Adding a future gong is one entry here plus one
 * file in `res/raw`, so the player never hard-codes a single recording.
 */
enum class GongChoice(val resourceName: String, @StringRes val labelRes: Int) {
    Root("gong_root", R.string.gong_root),
    RootDeep("gong_root_deep", R.string.gong_root_deep),
    Sacral("gong_sacral", R.string.gong_sacral),
    SolarPlexus("gong_solar", R.string.gong_solar_plexus),
    Heart("gong_heart", R.string.gong_heart),
    Throat("gong_throat", R.string.gong_throat),
    ThirdEye("gong_third_eye", R.string.gong_third_eye),
    Crown("gong_crown", R.string.gong_crown),
    Bell("gong_bell", R.string.gong_bell);

    fun next(): GongChoice = values()[(ordinal + 1) % values().size]

    fun previous(): GongChoice = values()[(ordinal + values().size - 1) % values().size]

    companion object {
        fun fromName(name: String?): GongChoice? = values().firstOrNull { it.name == name }
    }
}
