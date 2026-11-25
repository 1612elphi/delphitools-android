package com.delphi.delphitools.tools.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CronBuilderModelTest {
    @Test
    fun emptiedFieldCountsAsEvery() {
        val model = CronBuilderModel()
        model.write(CronFieldKind.Minute, "")
        assertEquals("* 9 * * MON-FRI", model.boxText)
        assertEquals("", model.source(CronFieldKind.Minute))
        assertTrue(model.parse(CronFieldKind.Minute).ok)
        assertEquals(5, model.parsed.fieldCount)
    }
}
