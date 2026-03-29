package com.github.freshmorsikov

import kotlin.test.Test
import kotlin.test.assertEquals

class ToMapTest {

    @Test
    fun `WHEN words divide equally THEN creates equal buckets`() {
        val words = listOf(
            "a1", "a2", "a3",
            "b1", "b2", "b3",
            "c1", "c2", "c3",
        )
        val alphabet = listOf('a', 'b', 'c')
        val expected = mapOf(
            'a' to listOf("a1", "a2", "a3"),
            'b' to listOf("b1", "b2", "b3"),
            'c' to listOf("c1", "c2", "c3"),
        )

        val mapping = words.toMap(alphabet = alphabet)

        assertEquals(expected, mapping)
    }

    @Test
    fun `WHEN words have remainder THEN puts leftovers in the last bucket`() {
        val words = listOf(
            "a1", "a2", "a3",
            "b1", "b2", "b3",
            "c1", "c2", "c3", "c4", "c5",
        )
        val alphabet = listOf('a', 'b', 'c')
        val expected = mapOf(
            'a' to listOf("a1", "a2", "a3"),
            'b' to listOf("b1", "b2", "b3"),
            'c' to listOf("c1", "c2", "c3", "c4", "c5"),
        )

        val mapping = words.toMap(alphabet = alphabet)

        assertEquals(expected, mapping)
    }

}
