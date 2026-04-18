package com.github.freshmorsikov

fun String?.toWordResult(): Result<String> {
    return if (this == null) {
        Result.failure(
            NoSuchElementException("No candidate word was selected")
        )
    } else {
        Result.success(this)
    }
}
