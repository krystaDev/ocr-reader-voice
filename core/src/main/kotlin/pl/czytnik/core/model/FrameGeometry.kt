package pl.czytnik.core.model

/** Prostokąt w pikselach (lewy/górny włącznie, prawy/dolny wyłącznie), jak `android.graphics.Rect`. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Przeliczenia między buforem aparatu a kadrem widzianym przez użytkownika.
 *
 * Aparat dostarcza bufor w orientacji matrycy i kąt [rotationDegrees], o jaki trzeba go obrócić zgodnie z ruchem
 * wskazówek zegara, żeby był „prosto”. ML Kit zwraca ramki w układzie obróconego obrazu, a CameraX podaje obszar
 * widoczny na podglądzie (`cropRect`) w układzie bufora.
 */
object FrameGeometry {

    /** Obraca prostokąt z układu bufora do układu obrazu „prosto”. */
    fun toUpright(rect: PixelRect, bufferWidth: Int, bufferHeight: Int, rotationDegrees: Int): PixelRect =
        when (((rotationDegrees % 360) + 360) % 360) {
            0 -> rect
            90 -> PixelRect(bufferHeight - rect.bottom, rect.left, bufferHeight - rect.top, rect.right)
            180 -> PixelRect(bufferWidth - rect.right, bufferHeight - rect.bottom, bufferWidth - rect.left, bufferHeight - rect.top)
            270 -> PixelRect(rect.top, bufferWidth - rect.right, rect.bottom, bufferWidth - rect.left)
            else -> throw IllegalArgumentException("Unsupported rotation: $rotationDegrees")
        }

    /**
     * Normalizuje ramkę (układ „prosto”) do kadru [crop] (układ „prosto”): 0..1, przycięta do kadru.
     * Zwraca `null`, gdy ramka leży całkowicie poza kadrem.
     */
    fun normalize(rect: PixelRect, crop: PixelRect): Box? {
        if (crop.width <= 0 || crop.height <= 0) return null
        val left = ((rect.left - crop.left).toDouble() / crop.width).coerceIn(0.0, 1.0)
        val top = ((rect.top - crop.top).toDouble() / crop.height).coerceIn(0.0, 1.0)
        val right = ((rect.right - crop.left).toDouble() / crop.width).coerceIn(0.0, 1.0)
        val bottom = ((rect.bottom - crop.top).toDouble() / crop.height).coerceIn(0.0, 1.0)
        if (right <= left || bottom <= top) return null
        return Box(left, top, right, bottom)
    }
}
