package com.delphi.delphitools.tools.audiovideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperModelsTest {
    @Test
    fun catalogueMatchesTheIosTiers() {
        assertEquals(listOf("whisper-tiny", "whisper-base", "whisper-small", "whisper-large-turbo"), WhisperModel.all.map { it.id })
        assertEquals(listOf("Tiny", "Base", "Small", "Large v3 Turbo"), WhisperModel.all.map { it.label })
    }

    @Test
    fun everyFileIsPinnedSizedAndHashed() {
        for (model in WhisperModel.all) {
            assertTrue(Regex("[0-9a-f]{40}").matches(model.revision))
            assertEquals(6, model.files.size)
            assertEquals(model.files.size, model.files.map { it.name }.toSet().size)
            assertTrue(model.files.any { it.name == WhisperModel.ENCODER } && model.files.any { it.name == WhisperModel.DECODER })
            for (file in model.files) {
                assertTrue(Regex("[0-9a-f]{64}").matches(file.sha256))
                assertTrue(file.bytes > 0)
                val url = model.url(file)
                assertTrue(url, url.startsWith("https://huggingface.co/${model.repo}/resolve/${model.revision}/"))
                assertTrue(url, !url.contains("/main/"))
            }
        }
        assertEquals(41_904_576L, WhisperModel.all.first().bytes)
    }

    @Test
    fun resumesOnlyAPartialThatFits() {
        assertEquals(0L, WhisperDownloadPlan.resumeFrom(0, 100))
        assertEquals(40L, WhisperDownloadPlan.resumeFrom(40, 100))
        assertEquals(100L, WhisperDownloadPlan.resumeFrom(100, 100))
        assertEquals(0L, WhisperDownloadPlan.resumeFrom(140, 100))
    }

    @Test
    fun acceptsOnlyTheAskedRangeOrTheWholeFile() {
        assertEquals(0L, WhisperDownloadPlan.bodyStart(200, 40, null))
        assertEquals(40L, WhisperDownloadPlan.bodyStart(206, 40, "bytes 40-99/100"))
        assertNull(WhisperDownloadPlan.bodyStart(206, 40, "bytes 0-99/100"))
        assertNull(WhisperDownloadPlan.bodyStart(206, 40, null))
        assertNull(WhisperDownloadPlan.bodyStart(416, 40, null))
        assertNull(WhisperDownloadPlan.bodyStart(404, 0, null))
    }

    @Test
    fun remainingCountsWhatIsMissing() {
        val model = WhisperModel.all.first()
        assertEquals(model.bytes, WhisperDownloadPlan.remaining(model) { 0 })
        assertEquals(0L, WhisperDownloadPlan.remaining(model) { it.bytes })
        assertEquals(0L, WhisperDownloadPlan.remaining(model) { it.bytes * 2 })
        val encoder = model.files.first { it.name == WhisperModel.ENCODER }
        assertEquals(model.bytes - encoder.bytes, WhisperDownloadPlan.remaining(model) { if (it == encoder) it.bytes else 0 })
    }

    @Test
    fun settlesOnInstalledOnlyWhenEveryFileIsWhole() {
        val model = WhisperModel.all.first()
        assertEquals(WhisperState.Installed, WhisperDownloadPlan.settled(model, { it.bytes }, null))
        assertEquals(WhisperState.Absent, WhisperDownloadPlan.settled(model, { if (it.name == WhisperModel.DECODER) 1 else it.bytes }, null))
        assertEquals(WhisperState.Absent, WhisperDownloadPlan.settled(model, { null }, WhisperState.Downloading(0.5)))
        val failed = WhisperState.Failed(WhisperFailure.Checksum)
        assertEquals(failed, WhisperDownloadPlan.settled(model, { null }, failed))
    }

    @Test
    fun fractionAndHex() {
        assertEquals(0.5, WhisperDownloadPlan.fraction(50, 100), 1e-9)
        assertEquals(0.0, WhisperDownloadPlan.fraction(5, 0), 1e-9)
        assertEquals(1.0, WhisperDownloadPlan.fraction(150, 100), 1e-9)
        assertEquals("00ff10", WhisperDownloadPlan.hex(byteArrayOf(0, -1, 16)))
    }
}
