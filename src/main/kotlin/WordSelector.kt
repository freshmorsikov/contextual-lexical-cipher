package com.github.freshmorsikov

internal interface WordSelector {
    fun select(words: List<String>, encodedWords: List<String>): String
}
