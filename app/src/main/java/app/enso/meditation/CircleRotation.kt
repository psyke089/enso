package app.enso.meditation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

/** Screen coordinates make increasing angles clockwise. One 15° detent = one duration step. */
class CircleRotation {
    private var previous: Double? = null
    private var pending = 0.0

    /** Continuous visual movement, independent of the duration detents. */
    var deltaDegrees: Float = 0f
        private set

    /** Remaining step budgets include the final, possibly clamped duration step at either limit. */
    fun move(angle: Double?, minimumSteps: Int = Int.MIN_VALUE, maximumSteps: Int = Int.MAX_VALUE): Int {
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
        val detent = PI / 12
        // Discard overshoot at the stop, so reversing responds immediately. Include any
        // unfinished detent to allow returning to the boundary after a small movement away.
        delta = delta.coerceIn(minimumSteps * detent - pending, maximumSteps * detent - pending)
        deltaDegrees = (delta * 180 / PI).toFloat()
        pending += delta
        val steps = (pending / detent + sign(pending) * 1e-9).toInt()
        pending -= steps * detent
        if (abs(pending) < 1e-9) pending = 0.0
        return steps
    }
}
