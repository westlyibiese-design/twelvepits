package com.westly.twelvepits.feature.game.ui

/** Thrown when a JSON text is malformed. */
class JsonFormatException(message: String) : RuntimeException(message)

/**
 * A very small JSON reader (pure Kotlin) so the board geometry can be parsed in plain unit tests.
 * Objects become Map<String, Any?>, arrays become List<Any?>, numbers become Double,
 * strings become String, booleans become Boolean and null becomes null.
 */
internal class JsonReader(private val text: String) {
    private var pos = 0

    fun readDocument(): Any? {
        skipSpace()
        val value = readValue()
        skipSpace()
        if (pos != text.length) fail("Unexpected text after the JSON value")
        return value
    }

    private fun readValue(): Any? {
        skipSpace()
        if (pos >= text.length) fail("Unexpected end of JSON")
        return when (val c = text[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> readWord("true", true)
            'f' -> readWord("false", false)
            'n' -> readWord("null", null)
            else -> if (c == '-' || c in '0'..'9') readNumber() else fail("Unexpected character '$c'")
        }
    }

    private fun readObject(): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        pos += 1
        skipSpace()
        if (peek() == '}') {
            pos += 1
            return result
        }
        while (true) {
            skipSpace()
            if (peek() != '"') fail("Expected a quoted key")
            val key = readString()
            skipSpace()
            if (peek() != ':') fail("Expected ':' after key")
            pos += 1
            result[key] = readValue()
            skipSpace()
            when (peek()) {
                ',' -> pos += 1
                '}' -> {
                    pos += 1
                    return result
                }
                else -> fail("Expected ',' or '}'")
            }
        }
    }

    private fun readArray(): List<Any?> {
        val result = ArrayList<Any?>()
        pos += 1
        skipSpace()
        if (peek() == ']') {
            pos += 1
            return result
        }
        while (true) {
            result.add(readValue())
            skipSpace()
            when (peek()) {
                ',' -> pos += 1
                ']' -> {
                    pos += 1
                    return result
                }
                else -> fail("Expected ',' or ']'")
            }
        }
    }

    private fun readString(): String {
        pos += 1
        val builder = StringBuilder()
        while (true) {
            if (pos >= text.length) fail("Unterminated string")
            val c = text[pos]
            pos += 1
            when (c) {
                '"' -> return builder.toString()
                '\\' -> {
                    if (pos >= text.length) fail("Unterminated escape")
                    val e = text[pos]
                    pos += 1
                    when (e) {
                        '"' -> builder.append('"')
                        '\\' -> builder.append('\\')
                        '/' -> builder.append('/')
                        'b' -> builder.append('\b')
                        'f' -> builder.append('\u000C')
                        'n' -> builder.append('\n')
                        'r' -> builder.append('\r')
                        't' -> builder.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) fail("Bad unicode escape")
                            val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: fail("Bad unicode escape")
                            builder.append(code.toChar())
                            pos += 4
                        }
                        else -> fail("Bad escape '\\$e'")
                    }
                }
                else -> builder.append(c)
            }
        }
    }

    private fun readNumber(): Double {
        val start = pos
        if (peek() == '-') pos += 1
        while (pos < text.length && (text[pos] in '0'..'9' || text[pos] in ".eE+-")) pos += 1
        return text.substring(start, pos).toDoubleOrNull() ?: fail("Bad number")
    }

    private fun readWord(word: String, value: Any?): Any? {
        if (!text.startsWith(word, pos)) fail("Unexpected word")
        pos += word.length
        return value
    }

    private fun peek(): Char = if (pos < text.length) text[pos] else '\u0000'

    private fun skipSpace() {
        while (pos < text.length && text[pos].isWhitespace()) pos += 1
    }

    private fun fail(message: String): Nothing = throw JsonFormatException("$message at position $pos")
}
