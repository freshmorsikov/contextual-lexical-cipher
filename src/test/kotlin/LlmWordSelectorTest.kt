package com.github.freshmorsikov

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LlmWordSelectorTest {

    @Test
    fun `WHEN selected word is present THEN returns kotlin success result`() {
        val result = "alpha".toWordResult()

        assertTrue(result.isSuccess)
        assertEquals("alpha", result.getOrThrow())
    }

    @Test
    fun `WHEN selected word is missing THEN returns failure with exception`() {
        val result = (null as String?).toWordResult()

        assertTrue(result.isFailure)
        assertFailsWith<NoSuchElementException> {
            result.getOrThrow()
        }
    }

    @Test
    fun `WHEN ranked words contain a valid candidate THEN picks the first ranked match`() {
        val selectedWord = LlmWordSelector().selectBestCandidate(
            candidateWords = listOf("alpha", "beta"),
            scoredWords = listOf(
                LlmWordSelector.ScoredWordPayload(
                    word = "gamma",
                    naturalnessScore = 0.99,
                ),
                LlmWordSelector.ScoredWordPayload(
                    word = "beta",
                    naturalnessScore = 0.51,
                ),
                LlmWordSelector.ScoredWordPayload(
                    word = "alpha",
                    naturalnessScore = 0.95,
                ),
            ),
        )

        assertEquals("beta", selectedWord)
    }

    @Test
    fun `WHEN ranked words contain no valid candidate THEN returns null`() {
        val selectedWord = LlmWordSelector().selectBestCandidate(
            candidateWords = listOf("alpha", "beta"),
            scoredWords = listOf(
                LlmWordSelector.ScoredWordPayload(
                    word = "gamma",
                    naturalnessScore = 0.99,
                ),
                LlmWordSelector.ScoredWordPayload(
                    word = "delta",
                    naturalnessScore = 0.91,
                ),
            ),
        )

        assertNull(selectedWord)
    }

    @Test
    fun `WHEN building LLM prompt THEN uses numbered entries and aligned system instructions`() {
        val selector = LlmWordSelector()

        val prompt = selector.buildPromptPayload(
            words = listOf("alpha", "beta"),
            sentence = "hello",
        )

        assertTrue(prompt.systemPrompt.contains("You will receive a numbered list"))
        assertTrue(prompt.systemPrompt.contains("Each item uses this format"))
        assertTrue(prompt.systemPrompt.contains("The number is only an item label"))
        assertEquals(
            """
            1. word = alpha; phrase = hello alpha
            2. word = beta; phrase = hello beta
            """.trimIndent(),
            prompt.userPrompt,
        )
    }
}
