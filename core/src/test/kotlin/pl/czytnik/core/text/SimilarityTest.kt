package pl.czytnik.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimilarityTest {

    private fun fp(text: String) = TextFingerprint.of(text)

    @Test
    fun `normalization ignores case punctuation and line breaks`() {
        assertEquals("i can t do this", TextFingerprint.normalizeForComparison("I CAN'T\nDO THIS!"))
    }

    @Test
    fun `identical texts are fully similar`() {
        assertEquals(1.0, fp("WHO ARE YOU?").dice(fp("who are you")))
    }

    @Test
    fun `small OCR error keeps texts similar`() {
        val a = fp("THIS IS THE END OF THE ROAD, STARK!")
        val b = fp("THIS IS THE END 0F THE R0AD, STARK!")
        assertTrue(a.dice(b) >= 0.75, "dice=${a.dice(b)}")
    }

    @Test
    fun `different texts are dissimilar`() {
        val a = fp("WE NEED TO GET OUT OF HERE")
        val b = fp("THE SHIELD IS STILL WORKING")
        assertTrue(a.dice(b) < 0.3, "dice=${a.dice(b)}")
    }

    @Test
    fun `empty texts`() {
        assertEquals(1.0, fp("").dice(fp("!!")))
        assertEquals(0.0, fp("").dice(fp("abc")))
    }

    @Test
    fun `part of a text is contained in the whole`() {
        val whole = fp("I AM IRON MAN AND THIS IS MY SUIT")
        val part = fp("IRON MAN AND THIS")
        assertTrue(part.containmentIn(whole) >= 0.9)
        assertTrue(whole.containmentIn(part) < 0.7)
    }
}
