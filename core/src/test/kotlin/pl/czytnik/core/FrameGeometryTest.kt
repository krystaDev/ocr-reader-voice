package pl.czytnik.core

import pl.czytnik.core.model.Box
import pl.czytnik.core.model.FrameGeometry
import pl.czytnik.core.model.PixelRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrameGeometryTest {

    // Bufor 400×300; w buforze prostokąt przy lewym górnym rogu.
    private val rect = PixelRect(10, 20, 50, 40)

    @Test
    fun `no rotation`() {
        assertEquals(rect, FrameGeometry.toUpright(rect, 400, 300, 0))
    }

    @Test
    fun `90 degrees - top left corner goes to top right`() {
        // Po obrocie obraz ma 300×400; lewy górny róg bufora trafia w prawy górny róg.
        assertEquals(PixelRect(260, 10, 280, 50), FrameGeometry.toUpright(rect, 400, 300, 90))
    }

    @Test
    fun `180 degrees - top left corner goes to bottom right`() {
        assertEquals(PixelRect(350, 260, 390, 280), FrameGeometry.toUpright(rect, 400, 300, 180))
    }

    @Test
    fun `270 degrees - top left corner goes to bottom left`() {
        assertEquals(PixelRect(20, 350, 40, 390), FrameGeometry.toUpright(rect, 400, 300, 270))
    }

    @Test
    fun `four quarter turns preserve area and stay inside the image`() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val upright = FrameGeometry.toUpright(rect, 400, 300, rotation)
            assertEquals(rect.width * rect.height, upright.width * upright.height)
            val (w, h) = if (rotation % 180 == 0) 400 to 300 else 300 to 400
            assert(upright.left >= 0 && upright.top >= 0 && upright.right <= w && upright.bottom <= h) { "$rotation: $upright" }
        }
    }

    @Test
    fun `normalize to crop and clip`() {
        val crop = PixelRect(100, 0, 300, 200)
        assertEquals(Box(0.25, 0.5, 0.75, 1.0), FrameGeometry.normalize(PixelRect(150, 100, 250, 200), crop))
        // Wystaje poza lewą krawędź kadru – przycięty, więc dotyka krawędzi.
        assertEquals(Box(0.0, 0.0, 0.25, 0.5), FrameGeometry.normalize(PixelRect(50, 0, 150, 100), crop))
        assertNull(FrameGeometry.normalize(PixelRect(0, 0, 90, 100), crop))
    }
}
