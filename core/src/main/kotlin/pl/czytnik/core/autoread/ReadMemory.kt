package pl.czytnik.core.autoread

import pl.czytnik.core.config.AutoReadConfig
import pl.czytnik.core.model.TextBlock
import pl.czytnik.core.text.TextFingerprint

/**
 * Pamięć przeczytanych bloków – zapobiega powtórzeniom (F11, docs/TECH-SPEC.md, rozdz. 3.3).
 *
 * Blok jest „przeczytany”, dopóki aparat go widzi; po [AutoReadConfig.leaveMs] nieobecności wpis wygasa i powrót do
 * tego tekstu przeczyta go ponownie. Do rozpoznania „wciąż widoczny” używamy niższego progu (histereza), żeby ucięty
 * lub częściowo rozpoznany dymek nadal trzymał blokadę.
 */
class ReadMemory(private val config: AutoReadConfig) {

    private class Entry(val fingerprint: TextFingerprint, var lastSeenMs: Long, var visible: Boolean = true)

    private val entries = ArrayDeque<Entry>()

    val size: Int get() = entries.size

    fun isRead(block: TextBlock): Boolean = isRead(TextFingerprint.of(block.rawText))

    fun isRead(text: TextFingerprint): Boolean {
        if (text.isEmpty || entries.isEmpty()) return false
        if (entries.any { text.dice(it.fingerprint) >= config.sameBlockSimilarity }) return true
        // Zawieranie sprawdzamy tylko względem tekstów widocznych teraz w kadrze: to przypadki, gdy OCR podzielił
        // przeczytany dymek na części albo scalił kilka przeczytanych w jeden. Porównanie z dawniejszymi tekstami
        // uznałoby krótki nowy dymek („WAIT!”) za przeczytany tylko dlatego, że to słowo już padło.
        val visible = entries.filter { it.visible }
        if (visible.any { text.containmentIn(it.fingerprint) >= config.containment }) return true
        val visibleTrigrams = visible.flatMapTo(HashSet()) { it.fingerprint.trigrams }
        return text.containmentIn(visibleTrigrams) >= config.containment
    }

    /**
     * Aktualizuje widoczność wpisów na podstawie bloków bieżącej klatki ([full] – pełne, [cut] – ucięte krawędzią)
     * i usuwa wpisy, które opuściły kadr.
     */
    fun observe(full: List<TextBlock>, nowMs: Long, cut: List<TextBlock> = emptyList()) {
        if (entries.isEmpty()) return
        val fullPrints = full.map { TextFingerprint.of(it.rawText) }.filterNot { it.isEmpty }
        val cutPrints = cut.map { TextFingerprint.of(it.rawText) }.filterNot { it.isEmpty }
        for (entry in entries) {
            val present = (fullPrints + cutPrints).any { block -> resembles(block, entry.fingerprint) } ||
                // Ucięty dymek to dowolnie mały kawałek przeczytanego tekstu.
                cutPrints.any { block -> block.containmentIn(entry.fingerprint) >= config.containment } ||
                // Pełny blok zawarty w przeczytanym tekście liczy się tylko, gdy to spora część (OCR podzielił dymek);
                // krótkie „WAIT!” nie może podtrzymywać dawnego dymku, w którym padło to słowo.
                fullPrints.any { block ->
                    block.containmentIn(entry.fingerprint) >= config.containment &&
                        block.trigrams.size >= entry.fingerprint.trigrams.size * MIN_PART_OF_READ_TEXT
                }
            entry.visible = present
            if (present) entry.lastSeenMs = nowMs
        }
        entries.removeAll { nowMs - it.lastSeenMs >= config.leaveMs }
    }

    private fun resembles(block: TextFingerprint, entry: TextFingerprint): Boolean =
        block.dice(entry) >= config.presentSimilarity || entry.containmentIn(block) >= config.presentSimilarity

    fun remember(blocks: List<TextBlock>, nowMs: Long) {
        for (block in blocks) {
            val fingerprint = TextFingerprint.of(block.rawText)
            if (fingerprint.isEmpty) continue
            val existing = entries.firstOrNull { it.fingerprint.dice(fingerprint) >= config.sameBlockSimilarity }
            if (existing != null) {
                existing.lastSeenMs = nowMs
                existing.visible = true
            } else {
                entries.addLast(Entry(fingerprint, nowMs))
            }
        }
        while (entries.size > config.memoryMaxEntries) entries.removeFirst()
    }

    /**
     * Po przerwie w analizie (czytanie, przetwarzanie) – traktujemy wszystkie wpisy jako widziane teraz, inaczej tekst
     * nadal leżący przed aparatem wygasłby w trakcie mowy i został przeczytany drugi raz.
     */
    fun resume(nowMs: Long) {
        entries.forEach {
            it.lastSeenMs = nowMs
            it.visible = true
        }
    }

    fun clear() = entries.clear()

    private companion object {
        const val MIN_PART_OF_READ_TEXT = 0.3
    }
}
