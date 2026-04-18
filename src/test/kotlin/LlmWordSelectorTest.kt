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

        assertTrue(prompt.systemPrompt.contains("You will receive the current phrase followed by a numbered list of candidate words"))
        assertTrue(prompt.systemPrompt.contains("The prompt starts with the current phrase"))
        assertTrue(prompt.systemPrompt.contains("Each candidate item uses this format"))
        assertTrue(prompt.systemPrompt.contains("The number is only an item label"))
        assertEquals(
            """
            phrase = hello <new word here>
            1. word = alpha
            2. word = beta
            """.trimIndent(),
            prompt.userPrompt,
        )
    }

    @Test
    fun `WHEN building first word prompt THEN uses opening specific instructions`() {
        val selector = LlmWordSelector()

        val prompt = selector.buildFirstWordPromptPayload(
            words = listOf("alpha", "beta"),
        )

        assertTrue(prompt.systemPrompt.contains("best opening for a text"))
        assertTrue(prompt.systemPrompt.contains("opening word"))
        assertTrue(prompt.systemPrompt.contains("Only use candidate words that appear in the input"))
        assertEquals(
            """
            1. word = alpha
            2. word = beta
            """.trimIndent(),
            prompt.userPrompt,
        )
    }

    @Test
    fun `WHEN no encoded words exist THEN first word prompt is used`() {
        val selector = object : LlmWordSelector() {
            override fun getWord(
                words: List<String>,
                promptPayload: PromptPayload,
            ): Result<String> {
                assertTrue(promptPayload.systemPrompt.contains("best opening for a text"))
                return Result.success("alpha")
            }
        }

        val selectedWord = selector.select(
            words = listOf("alpha", "beta"),
            encodedWords = emptyList(),
        )

        assertEquals("alpha", selectedWord)
    }
}
