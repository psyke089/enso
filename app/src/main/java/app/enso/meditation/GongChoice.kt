package app.enso.meditation

/**
 * Selectable local gong recordings. Adding a future gong is one entry here plus one
 * file in `res/raw`, so the player never hard-codes a single recording.
 */
enum class GongChoice(val resourceName: String, val label: String) {
    Root("gong_root", "Root"),
    RootDeep("gong_root_deep", "Root Deep"),
    Sacral("gong_sacral", "Sacral"),
    SolarPlexus("gong_solar", "Solar Plexus"),
    Heart("gong_heart", "Heart"),
    Throat("gong_throat", "Throat"),
    ThirdEye("gong_third_eye", "Third Eye"),
    Crown("gong_crown", "Crown"),
    Bell("gong_bell", "Bell");

    fun next(): GongChoice = values()[(ordinal + 1) % values().size]

    fun previous(): GongChoice = values()[(ordinal + values().size - 1) % values().size]

    companion object {
        fun fromName(name: String?): GongChoice? = values().firstOrNull { it.name == name }
    }
}
