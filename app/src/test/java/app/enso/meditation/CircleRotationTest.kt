package app.enso.meditation

import kotlin.math.PI
import org.junit.Assert.*
import org.junit.Test

class CircleRotationTest {
    private fun radians(degrees: Double) = degrees * PI / 180

    @Test fun smallMovementsAccumulateAndDirectionCanReverse() {
        val rotation = CircleRotation()
        assertEquals(0, rotation.move(0.0))
        assertEquals(0, rotation.move(radians(8.0)))
        assertEquals(8f, rotation.deltaDegrees, .001f)
        assertEquals(1, rotation.move(radians(16.0)))
        assertEquals(-1, rotation.move(radians(-1.0)))
        assertEquals(-17f, rotation.deltaDegrees, .001f)
    }

    @Test fun crossingAngleSeamWorksInBothDirections() {
        for (direction in listOf(-1, 1)) {
            val rotation = CircleRotation()
            rotation.move(radians(170.0 * direction))
            assertEquals(direction, rotation.move(radians(-170.0 * direction)))
            assertEquals(20f * direction, rotation.deltaDegrees, .001f)
        }
    }

    @Test fun centerCrossingDiscardsUnstableAnglesAndPendingMovement() {
        val rotation = CircleRotation()
        rotation.move(0.0)
        rotation.move(radians(10.0))
        assertEquals(0, rotation.move(null))
        assertEquals(0f, rotation.deltaDegrees, 0f)
        assertEquals(0, rotation.move(PI))
        assertEquals(0f, rotation.deltaDegrees, 0f)
        assertEquals(0, rotation.move(radians(175.0)))
    }

    @Test fun multipleTurnsAndLimitsUseNormalDurationSteps() {
        for (direction in listOf(-1, 1)) {
            val rotation = CircleRotation()
            val engine = TimerEngine(TimerSnapshot(selectedMs = 60 * 60_000L))
            rotation.move(0.0)
            var steps = 0
            for (degrees in 10..1080 step 10) {
                val angle = radians((degrees * direction + 180.0) % 360 - 180)
                val change = rotation.move(angle)
                steps += change
                repeat(kotlin.math.abs(change)) { engine.adjustDuration(direction) }
            }
            assertEquals(direction * 72, steps)
            assertEquals(if (direction < 0) DURATION_STEP else 7 * 60 * 60_000L, engine.snapshot.selectedMs)
        }
    }
}
