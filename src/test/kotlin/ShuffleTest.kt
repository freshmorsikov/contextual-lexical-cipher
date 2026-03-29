package com.github.freshmorsikov

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ShuffleTest {

    @Test
    fun `WHEN pass same key THEN produces the same shuffle`() {
        val values = listOf("alpha", "beta", "gamma", "delta", "epsilon")

        val first = values.shuffle(key = "secret-key")
        val second = values.shuffle(key = "secret-key")

        assertEquals(first, second)
    }

    @Test
    fun `WHEN pass different key THEN produces different shuffle`() {
        val values = listOf("alpha", "beta", "gamma", "delta", "epsilon")

        val first = values.shuffle(key = "secret-key")
        val second = values.shuffle(key = "another-key")

        assertNotEquals(first, second)
    }


}
