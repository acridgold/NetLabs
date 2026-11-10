package socks.admin

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonTest {
    @Test
    fun escapesQuotesBackslashesAndControlChars() {
        assertEquals("\"a\\\"b\\\\c\\u000ad\"", Json.str("a\"b\\c\nd"))
    }

    @Test
    fun keepsCyrillicAsIs() {
        assertEquals("\"привет\"", Json.str("привет"))
    }
}
