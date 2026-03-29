package com.github.freshmorsikov

import java.io.InputStream

fun String.getResourceAsStream(): InputStream {
    return Thread.currentThread().contextClassLoader.getResourceAsStream(this) ?: error("Resource not found")
}