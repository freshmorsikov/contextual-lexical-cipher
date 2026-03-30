package com.github.freshmorsikov

internal class LlmWordSelector : WordSelector {
    override fun select(words: List<String>, encodedWords: List<String>): String {
        return words.first()
    }
}
