package com.westly.twelvepits.feature.game.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonReaderTest {
    private fun read(text: String): Any? = JsonReader(text).readDocument()

    @Test
    fun readsObjectsArraysNumbersStringsAndWords() {
        val value = read("""{"a": [1, 2.5, -3e2], "b": "x\ny\u0041", "c": true, "d": false, "e": null, "f": {}}""")
        val map = value as Map<*, *>
        assertEquals(listOf(1.0, 2.5, -300.0), map["a"])
        assertEquals("x\nyA", map["b"])
        assertEquals(true, map["c"])
        assertEquals(false, map["d"])
        assertNull(map["e"])
        assertEquals(emptyMap<String, Any?>(), map["f"])
    }

    @Test
    fun badTextIsRejected() {
        assertThrows(JsonFormatException::class.java) { read("{") }
        assertThrows(JsonFormatException::class.java) { read("{\"a\" 1}") }
        assertThrows(JsonFormatException::class.java) { read("[1,]") }
        assertThrows(JsonFormatException::class.java) { read("\"open") }
        assertThrows(JsonFormatException::class.java) { read("1 2") }
        assertThrows(JsonFormatException::class.java) { read("nul") }
    }
}
