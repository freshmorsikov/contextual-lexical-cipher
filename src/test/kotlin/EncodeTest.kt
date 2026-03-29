package com.github.freshmorsikov

import kotlin.test.Test
import kotlin.test.assertEquals

class EncodeTest {

    @Test
    fun `WHEN encoding text THEN uses first word from each character bucket and joins with spaces`() {
        val mapping = mapOf(
            'a' to listOf("alpha", "able"),
            'b' to listOf("bravo", "baker"),
            ' ' to listOf("space", "blank"),
        )

        val encoded = "ab a".encode(mapping = mapping)

        assertEquals("alpha bravo space alpha", encoded)
    }

}
