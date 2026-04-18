package com.github.freshmorsikov

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
}
