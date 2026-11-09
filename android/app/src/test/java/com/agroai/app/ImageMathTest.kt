package com.agroai.app

import com.agroai.app.ml.ImageMath
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageMathTest {
    @Test
    fun centerSquareLandscape() {
        assertEquals(Triple(80, 0, 480), ImageMath.centerSquare(640, 480))
    }

    @Test
    fun centerSquarePortrait() {
        assertEquals(Triple(0, 80, 480), ImageMath.centerSquare(480, 640))
    }

    @Test
    fun centerSquareOddDifferenceRoundsDown() {
        assertEquals(Triple(0, 0, 3), ImageMath.centerSquare(3, 4))
    }

    @Test
    fun sampleSizeKeepsShortSideAboveMinimum() {
        assertEquals(4, ImageMath.sampleSize(4000, 3000, 448)) // 3000/4 = 750 >= 448, /8 = 375 < 448
        assertEquals(1, ImageMath.sampleSize(600, 500, 448))
    }

    @Test
    fun quantizeIdentityForUnitScale() {
        assertEquals(200, ImageMath.quantize(200f, 1f, 0, 0, 255))
    }

    @Test
    fun quantizeAppliesScaleZeroPointAndClamps() {
        assertEquals(100 + 10, ImageMath.quantize(50f, 0.5f, 10, 0, 255))
        assertEquals(127, ImageMath.quantize(255f, 1f, 0, -128, 127))
        assertEquals(0, ImageMath.quantize(-5f, 1f, 0, 0, 255))
    }
}
