package app.enso.meditation

import kotlin.math.PI

/** Screen coordinates make increasing angles clockwise. One 15° detent = one duration step. */
class CircleRotation {
    private var previous: Double? = null
    private var pending = 0.0

    /** Continuous visual movement, independent of the duration detents. */
    var deltaDegrees: Float = 0f
        private set

    fun move(angle: Double?): Int {
        deltaDegrees = 0f
        val last = previous
        previous = angle
        if (angle == null) {
            pending = 0.0
            return 0
        }
        if (last == null) return 0
        var delta = angle - last
        if (delta > PI) delta -= 2 * PI
        if (delta < -PI) delta += 2 * PI
        deltaDegrees = (delta * 180 / PI).toFloat()
        pending += delta
        val steps = (pending / (PI / 12)).toInt()
        pending -= steps * (PI / 12)
        return steps
    }
}
