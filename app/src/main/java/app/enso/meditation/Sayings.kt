package app.enso.meditation

import kotlin.random.Random

/** Original contemplative sayings inspired by Zen practice, without attributed quotations. */
object Sayings {
    /**
     * Entry count of the `sayings` string array. The engine only tracks an index so the UI can
     * resolve the saying in the active locale; keep `values` and `values-de` arrays in sync with
     * this constant (enforced by the instrumented smoke test).
     */
    const val count = 20

    fun next(previous: Int): Int {
        if (previous !in 0 until count) return Random.nextInt(count)
        return (previous + 1 + Random.nextInt(count - 1)) % count
    }
}
