package com.github.freshmorsikov

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.runBlocking

internal class LlmWordSelector : WordSelector, AutoCloseable {

    sealed interface Result {
        data class Success(val word: String) : Result
        data object Error : Result
    }

    companion object {
        val LINKING_WORDS = loadWordResource("linking_words.txt")
    }

    private val apiKey: String = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY is not set")
    private val llmClient: OpenAILLMClient = OpenAILLMClient(apiKey)

    override fun select(
        words: List<String>,
        encodedWords: List<String>,
    ): String {
        val sentence = encodedWords.joinToString(separator = " ").ifBlank { "<empty>" }
        val result = getWord(
            words = words,
            sentence = sentence,
        )
        return when (result) {
            is Result.Success -> return result.word
            is Result.Error -> {
                tryWithLinkinWord(
                    words = words,
                    sentence = sentence,
                )
            }
        }
    }

    private fun tryWithLinkinWord(
        words: List<String>,
        sentence: String,
    ): String {
        val linkingResult = getWord(
            words = LINKING_WORDS,
            sentence = sentence,
        )

        return when (linkingResult) {
            is Result.Success -> {
                val word = getWord(
                    words = words,
                    sentence = "$sentence ${linkingResult.word}",
                ).wordOrNone()
                "${linkingResult.word} $word"
            }

            is Result.Error -> {
                "-"
            }
        }
    }

    private fun getWord(words: List<String>, sentence: String): Result {
        logDebug(
            type = "request",
            message = """
                Current sentence: $sentence
                Candidate words: ${words.joinToString(separator = ", ")}
            """.trimIndent()
        )

        return runCatching {
            runBlocking {
                val response = llmClient.execute(
                    prompt = prompt("word_selector") {
                        system(
                            """
                                You are choosing the next word in a sentence.
                                Return exactly one candidate word that best continues the sentence.
                                Return only the chosen word.
                            """.trimIndent()
                        )
                        user(
                            """
                                Current sentence: $sentence
                                Candidate words: ${words.joinToString(separator = ", ")}
                            """.trimIndent()
                        )
                    },
                    model = OpenAIModels.Chat.GPT5Nano,
                )

                val selectedWord = response
                    .firstOrNull()
                    ?.content
                    ?.trim()
                if (selectedWord == null) {
                    logDebug(
                        type = "error",
                        message = "selectedWord = null",
                    )
                    Result.Error
                } else {
                    val word = words.indexOfFirst { word ->
                        word.lowercase() == selectedWord.lowercase()
                    }.takeIf { index ->
                        index != -1
                    }?.let { index ->
                        words[index]
                    }
                    logDebug(
                        type = "result",
                        message = "selectedWord = $word",
                    )

                    word.toResult()
                }
            }
        }.getOrElse {
            logDebug(
                type = "error",
                message = "${it::class.qualifiedName}: ${it.message}"
            )

            Result.Error
        }
    }

    override fun close() {
        llmClient.close()
    }

    private fun String?.toResult(): Result {
        if (this == null) return Result.Error
        return Result.Success(this)
    }

    private fun Result.wordOrNone(): String {
        return when (this) {
            is Result.Error -> "-"
            is Result.Success -> word
        }
    }

    private fun logDebug(type: String, message: String) {
        println("================")
        println("LLM | $type")
        println(message)
    }

}
