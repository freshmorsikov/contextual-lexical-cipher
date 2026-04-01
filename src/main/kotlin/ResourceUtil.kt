package com.github.freshmorsikov

import java.io.InputStream

fun String.getResourceAsStream(): InputStream {
    return Thread.currentThread().contextClassLoader.getResourceAsStream(this) ?: error("Resource not found")
}

fun loadWordResource(resourceName: String): List<String> {
    return resourceName.getResourceAsStream()
        .bufferedReader()
        .useLines { lines ->
            lines
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList()
        }
}
