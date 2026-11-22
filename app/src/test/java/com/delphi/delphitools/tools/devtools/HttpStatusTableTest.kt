package com.delphi.delphitools.tools.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpStatusTableTest {
    @Test
    fun table() {
        assertEquals(63, HttpStatusTable.all.size)
        assertEquals(HttpRegistryState.Unused, HttpStatusTable.lookup(418)?.registry)
        assertEquals(true, HttpStatusTable.lookup(200)?.isCacheable)
        assertEquals(false, HttpStatusTable.lookup(201)?.isCacheable)
        assertEquals("RFC 9110 §15.5.5", HttpStatusTable.lookup(404)?.reference)
        assertEquals(1, HttpStatusTable.groups("40", null).size)
        assertEquals(418, HttpStatusTable.groups("teapot", null).first().items.first().code)
        assertEquals(HttpStatusTable.count(5), HttpStatusTable.groups("", 5).first().items.size)
    }

    @Test
    fun facts() {
        assertEquals("RFC 9110 §15.3.1 · Cacheable", HttpStatusTable.lookup(200)?.facts)
        assertEquals("RFC 9110 §15.4.6 · Deprecated", HttpStatusTable.lookup(305)?.facts)
        assertTrue(HttpStatusTable.groups("  NOT found ", 4).single().items.any { it.code == 404 })
    }
}
