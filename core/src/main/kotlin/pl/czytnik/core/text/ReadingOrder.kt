package pl.czytnik.core.text

import pl.czytnik.core.model.TextBlock

/**
 * Naturalna kolejność czytania bloków (F4): rekurencyjny XY-cut – najpierw podział na wiersze (poziome przerwy),
 * potem na kolumny od lewej (pionowe przerwy). Dobrze pasuje do kadrów komiksu i układów ulotek.
 */
object ReadingOrder {

    /** Przerwa mniejsza niż ta wartość (ułamek kadru) nie dzieli bloków. */
    private const val MIN_GAP = 0.005

    fun order(blocks: List<TextBlock>): List<TextBlock> {
        if (blocks.size <= 1) return blocks
        val rows = groupByProjection(blocks, start = { it.box.top }, end = { it.box.bottom })
        if (rows.size > 1) return rows.flatMap(::order)
        val columns = groupByProjection(blocks, start = { it.box.left }, end = { it.box.right })
        if (columns.size > 1) return columns.flatMap(::order)
        return blocks.sortedWith(compareBy({ it.box.top }, { it.box.left }))
    }

    /** Grupuje bloki, których rzuty na oś nachodzą na siebie; grupy w kolejności osi. */
    private fun groupByProjection(
        blocks: List<TextBlock>,
        start: (TextBlock) -> Double,
        end: (TextBlock) -> Double,
    ): List<List<TextBlock>> {
        val sorted = blocks.sortedBy(start)
        val groups = mutableListOf(mutableListOf(sorted.first()))
        var groupEnd = end(sorted.first())
        for (block in sorted.drop(1)) {
            if (start(block) - groupEnd >= MIN_GAP) {
                groups += mutableListOf(block)
                groupEnd = end(block)
            } else {
                groups.last() += block
                groupEnd = maxOf(groupEnd, end(block))
            }
        }
        return groups
    }
}

/** Bloki → akapity gotowe do tłumaczenia i mowy, w kolejności czytania. */
object Paragraphs {
    fun from(blocks: List<TextBlock>): List<String> =
        ReadingOrder.order(blocks)
            .map { block -> TextNormalizer.joinLines(block.lines.map { it.text }) }
            .filter { it.isNotBlank() }
}
