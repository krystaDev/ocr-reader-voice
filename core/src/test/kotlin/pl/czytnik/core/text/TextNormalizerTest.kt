package pl.czytnik.core.text

import pl.czytnik.core.model.LanguageTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextNormalizerTest {

    @Test
    fun `lines are joined with spaces`() {
        assertEquals("Weź jedną tabletkę dwa razy dziennie.", TextNormalizer.joinLines(listOf("Weź jedną tabletkę", "dwa razy dziennie.")))
    }

    @Test
    fun `hyphenated Polish word is merged`() {
        assertEquals("Przeniesienie wyrazu", TextNormalizer.joinLines(listOf("Przenie-", "sienie wyrazu")))
    }

    @Test
    fun `upper case hyphenation keeps the hyphen without space`() {
        assertEquals("I AM SPIDER-MAN!", TextNormalizer.joinLines(listOf("I AM SPIDER-", "MAN!")))
    }

    @Test
    fun `dash at the end of a sentence is not a hyphenation`() {
        assertEquals("WAIT - WHAT?", TextNormalizer.joinLines(listOf("WAIT -", "WHAT?")))
    }

    @Test
    fun `blank lines are skipped`() {
        assertEquals("A B", TextNormalizer.joinLines(listOf(" A ", "", "B")))
    }

    @Test
    fun `mostly upper case detection`() {
        assertTrue(TextNormalizer.isMostlyUpperCase("I CAN'T DO THIS, Tony!"))
        assertFalse(TextNormalizer.isMostlyUpperCase("Normal sentence with USA inside."))
        assertFalse(TextNormalizer.isMostlyUpperCase("NO!"))
    }

    @Test
    fun `all caps English becomes sentence case with capital I`() {
        assertEquals(
            "I can't do this! Who are you? I'm not sure I... I'll try.",
            TextNormalizer.normalizeAllCaps("I CAN'T DO THIS! WHO ARE YOU? I'M NOT SURE I... I'LL TRY.", LanguageTag.ENGLISH),
        )
    }

    @Test
    fun `all caps Polish does not special-case i`() {
        assertEquals("Idę i wracam.", TextNormalizer.normalizeAllCaps("IDĘ I WRACAM.", LanguageTag.POLISH))
    }

    @Test
    fun `normal text is not changed`() {
        val text = "Paracetamol 500 mg. Stosować wg zaleceń lekarza."
        assertEquals(text, TextNormalizer.normalizeAllCaps(text, LanguageTag.POLISH))
    }
}
