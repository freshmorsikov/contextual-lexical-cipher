package com.github.freshmorsikov

fun main() {
    print("key=")
    val key = readln()
    print("text=")
    val text = readln()

    val words = loadWords()
    println(words.joinToString())

    val mapping = words
        .shuffle(key = key)
        .toMap()

    val encodedText = text.encode(mapping = mapping)
    println(encodedText)
}

private fun loadWords(): List<String> {
    return "words.txt".getResourceAsStream()
        .bufferedReader()
        .useLines { lines ->
            lines
                .map(String::trim)
                .toList()
        }
}

private fun getSeed(key: String): Int {
    return 0 // TODO implement
}

private fun List<String>.shuffle(key: String): List<String> {
    return this // TODO implement
}

private fun List<String>.toMap(): Map<Char, List<String>> {
    return emptyMap() // TODO implement
}

private fun String.encode(mapping: Map<Char, List<String>>): String {
    return "" // TODO implement
}
