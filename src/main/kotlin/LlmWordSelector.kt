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
        private val LINKING_WORDS = loadWordResource("linking_words.txt")
        private const val MAX_ATTEMPTS = 3
    }

    private val apiKey: String = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY is not set")
    private val llmClient: OpenAILLMClient = OpenAILLMClient(apiKey)

    private fun List<String>.toSentence(): String {
        return joinToString(separator = " ").ifBlank { "<empty>" }
    }

    override fun select(
        words: List<String>,
        encodedWords: List<String>,
    ): String {
        val mixedWords = LINKING_WORDS + words
        val newWords = mutableListOf<String>()

        repeat(MAX_ATTEMPTS) { i ->
            val wordSet = if (i == MAX_ATTEMPTS - 1) {
                words
            } else {
                mixedWords
            }
            val result = getWord(
                words = wordSet,
                sentence = (encodedWords + newWords).toSentence()
            )
            when (result) {
                is Result.Success -> {
                    newWords += result.word

                    if (result.word in words) {
                        return newWords.toSentence()
                    }
                }

                is Result.Error -> {}
            }
        }

        return "-"
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

    private fun logDebug(type: String, message: String) {
        println("================")
        println("LLM | $type")
        println(message)
    }

}
