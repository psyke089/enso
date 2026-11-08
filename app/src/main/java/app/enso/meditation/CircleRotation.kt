package app.enso.meditation

import kotlin.math.PI

/** Screen coordinates make increasing angles clockwise. One 15° detent = one duration step. */
class CircleRotation {
    private var previous: Double? = null
    private var pending = 0.0

    fun move(angle: Double?): Int {
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
        pending += delta
        val steps = (pending / (PI / 12)).toInt()
        pending -= steps * (PI / 12)
        return steps
    }
}
