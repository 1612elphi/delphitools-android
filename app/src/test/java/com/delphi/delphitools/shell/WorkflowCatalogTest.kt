package com.delphi.delphitools.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// dt-ios WorkflowCatalog.selfCheck
class WorkflowCatalogTest {
    @Test
    fun everyPresetIsAValidChain() {
        assertEquals(emptyList<String>(), WorkflowCatalog.presets.filter { WorkflowCatalog.custom(it.steps) == null }.map { it.id })
    }

    @Test
    fun everyPortNamesAPortedTool() {
        assertEquals(emptySet<String>(), WorkflowCatalog.ports.keys - tools.map { it.id }.toSet())
    }

    @Test
    fun chainsFollowPorts() {
        assertNotNull(WorkflowCatalog.custom(listOf("pixel-picker", "gradient-genny")))
        // a colour tail never finishes
        assertNull(WorkflowCatalog.custom(listOf("gradient-genny", "pixel-picker")))
        assertNull(WorkflowCatalog.custom(listOf("video-trimmer")))
        // paste image has no drop zone
        assertNull(WorkflowCatalog.custom(listOf("qr-genny", "paste-image")))
        assertNotNull(WorkflowCatalog.custom(listOf("image-tracer", "svg-optimiser", "svg-optimiser")))
        // svg into a bitmap drop zone
        assertNull(WorkflowCatalog.custom(listOf("image-tracer", "image-compressor")))
        assertNull(WorkflowCatalog.custom(listOf("image-tracer", "svg-optimiser", "svg-optimiser", "svg-optimiser")))
        assertFalse(WorkflowCatalog.nextTools(toolWithId("video-trimmer")!!).any { it.id == "background-remover" })
    }

    @Test
    fun barcodeSpellsSixLetters() {
        assertEquals(setOf(0, 1, 2, 3, 4, 5), barcodeBars.map { (it.first / 6).toInt() }.toSet())
    }
}
