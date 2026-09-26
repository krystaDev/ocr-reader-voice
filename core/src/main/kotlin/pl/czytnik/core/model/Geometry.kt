package pl.czytnik.core.model

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Prostokąt we współrzędnych znormalizowanych do kadru (0..1), w orientacji, w jakiej widzi go użytkownik.
 */
data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init {
        require(left <= right && top <= bottom) { "Invalid box: $this" }
    }

    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val centerX: Double get() = (left + right) / 2
    val centerY: Double get() = (top + bottom) / 2

    fun union(other: Box) = Box(
        left = min(left, other.left),
        top = min(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom),
    )

    /** Czy prostokąt dotyka marginesu przy którejkolwiek krawędzi kadru. */
    fun touchesFrameEdge(margin: Double): Boolean =
        left <= margin || top <= margin || right >= 1 - margin || bottom >= 1 - margin

    /** Odległość środków jako ułamek przekątnej kadru jednostkowego. */
    fun centerDistance(other: Box): Double =
        hypot(centerX - other.centerX, centerY - other.centerY) / FRAME_DIAGONAL

    companion object {
        private val FRAME_DIAGONAL = hypot(1.0, 1.0)

        fun unionOf(boxes: Iterable<Box>): Box? = boxes.reduceOrNull(Box::union)
    }
}
