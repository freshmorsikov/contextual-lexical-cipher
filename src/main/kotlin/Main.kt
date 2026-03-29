package com.github.freshmorsikov

import java.security.MessageDigest
import kotlin.random.Random

internal val ALPHABET: List<Char> = buildList {
    addAll('a'..'z')
    addAll('A'..'Z')
    addAll('0'..'9')
    add(' ')
    addAll(
        listOf(
            '!', '"', '#', '$', '%', '&', '\'',
            '(', ')', '*', '+', ',', '-', '.',
            '/', ':', ';', '<', '=', '>', '?',
            '@', '[', '\\', ']', '^', '_', '`',
            '{', '|', '}', '~'
        )
    )
}

fun main() {
    print("key=")
    val key = readln()
    print("text=")
    val text = readln()

    val words = loadWords()

    val mapping = words
        .shuffle(key = key)
        .toMap(alphabet = ALPHABET)

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

internal fun List<String>.shuffle(key: String): List<String> {
    return shuffled(Random(getSeed(key)))
}

internal fun getSeed(key: String): Int {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray())

    return digest
        .take(Int.SIZE_BYTES)
        .fold(0) { acc, byte ->
            (acc shl 8) or (byte.toInt() and 0xff)
        }
}

internal fun List<String>.toMap(alphabet: List<Char>): Map<Char, List<String>> {
    val wordCount = size
    val bucketSize = wordCount / alphabet.size

    return buildMap(alphabet.size) {
        var startIndex = 0

        alphabet.forEachIndexed { index, char ->
            val endIndex = if (index == alphabet.lastIndex) {
                wordCount
            } else {
                startIndex + bucketSize
            }

            put(char, subList(startIndex, endIndex))
            startIndex = endIndex
        }
    }
}

private fun String.encode(mapping: Map<Char, List<String>>): String {
    return "" // TODO implement
}
