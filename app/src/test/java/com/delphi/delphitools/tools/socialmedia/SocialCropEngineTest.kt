package com.delphi.delphitools.tools.socialmedia

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialCropEngineTest {
    private fun rect(x: Float, y: Float, width: Float, height: Float) = Rect(Offset(x, y), Size(width, height))

    @Test
    fun presets() {
        val ratios = CropPresets.all.flatMap { it.ratios }
        assertTrue("every ratio positive", ratios.all { it.widthRatio > 0 && it.heightRatio > 0 })
        assertEquals("unique ids", ratios.size, ratios.map { it.id }.toSet().size)
        assertEquals(CropPresets.all.size, CropPresets.all.map { it.id }.toSet().size)
        assertTrue("one outline label per ratio", CropPresets.all.all { it.ratios.isNotEmpty() && it.ratios.map { r -> r.label }.toSet().size == it.ratios.size })
        assertFalse(CropPresets.all.any { it.id in listOf("x", "twitter") })
        assertEquals(listOf("1:1", "3:4", "4:5", "1.91:1", "9:16"), CropPresets.instagram.ratios.map { it.label })
    }

    @Test
    fun crop() {
        val size = Size(3000f, 2000f)
        assertEquals(rect(500f, 0f, 2000f, 2000f), SocialCrop.crop(1f, size, Offset(1500f, 1000f)))
        assertEquals("clamped", rect(1000f, 0f, 2000f, 2000f), SocialCrop.crop(1f, size, Offset(2900f, 0f)))
        assertEquals(rect(0f, 250f, 3000f, 1500f), SocialCrop.crop(2f, size, Offset(1500f, 1000f)))
    }

    @Test
    fun model() {
        val bluesky = CropPresets.all.first { it.id == "bluesky" }
        var crop = SocialCrop().withImage(Size(3000f, 2000f))
        assertEquals("starts centred", rect(500f, 0f, 2000f, 2000f), crop.rect)
        crop = crop.dragged(Offset(30f, 0f), Size(300f, 200f))
        assertEquals("drag in view points", 800f, crop.rect.left)
        crop = crop.select(CropPresets.instagram.ratios[1])
        assertEquals("keeps the centre", rect(1050f, 0f, 1500f, 2000f), crop.rect)
        crop = crop.select(bluesky)
        assertEquals("bluesky-portrait", crop.ratio.id)
        assertEquals("same name, same centre", 1800f, crop.rect.center.x)
        crop = crop.dragged(Offset(100f, 0f), Size(300f, 200f))
        assertEquals("clamped at the edge", 3000f, crop.rect.right)
    }
}
