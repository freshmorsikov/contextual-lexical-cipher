package com.github.freshmorsikov

import kotlin.test.Test
import kotlin.test.assertEquals

class EncodeTest {

    @Test
    fun `WHEN encode THEN return selected words joined by spaces`() {
        val fakeSelector = object : WordSelector {
            override fun select(
                words: List<String>,
                encodedWords: List<String>
            ): String {
                return words.first()
            }
        }
        val mapping = mapOf(
            'a' to listOf("I", "alpha"),
            'b' to listOf("love", "bravo"),
            'c' to listOf("you", "charlie"),
        )

        val encoded = "abc".encode(
            mapping = mapping,
            wordSelector = fakeSelector
        )

        assertEquals("I love you", encoded)
    }

    @Test
    fun `WHEN selector returns punctuation attached to words THEN encode keeps natural spacing`() {
        val fakeSelector = object : WordSelector {
            override fun select(
                words: List<String>,
                encodedWords: List<String>
            ): String {
                return when (words.first()) {
                    "I" -> "I,"
                    "love" -> "love"
                    "you" -> "you!"
                    else -> words.first()
                }
            }
        }

        val mapping = mapOf(
            'a' to listOf("I", "alpha"),
            'b' to listOf("love", "bravo"),
            'c' to listOf("you", "charlie"),
        )

        val encoded = "abc".encode(
            mapping = mapping,
            wordSelector = fakeSelector
        )

        assertEquals("I, love you!", encoded)
    }

}
