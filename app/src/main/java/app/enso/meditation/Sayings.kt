package app.enso.meditation

import kotlin.random.Random

/** Original contemplative sayings inspired by Zen practice, without attributed quotations. */
object Sayings {
    val all = listOf(
        "Let this breath be enough.",
        "Sit quietly. Let the world unfold.",
        "Return gently to what is here.",
        "Nothing to hold. Nothing to hurry.",
        "Make room for the passing clouds.",
        "Meet this moment with kindness.",
        "Let stillness take its own time.",
        "Begin again with a softer heart.",
        "The breath asks only that you listen.",
        "Leave a little space for wonder.",
        "Rest in the space between thoughts.",
        "Each breath is a quiet beginning.",
        "You can set the burden down.",
        "Let the mind settle like clear water.",
        "Be gentle with what you find.",
        "A quiet moment needs no answer.",
        "Allow this moment to be as it is.",
        "Notice. Soften. Begin again.",
        "There is no hurry in this breath.",
        "Let your attention be a kindness.",
    )

    fun next(previous: Int): Int {
        if (previous !in all.indices) return Random.nextInt(all.size)
        return (previous + 1 + Random.nextInt(all.size - 1)) % all.size
    }
}
