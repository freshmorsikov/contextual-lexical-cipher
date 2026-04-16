package com.github.freshmorsikov

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.params.LLMParams
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

    private val llmClientDelegate = lazy {
        OpenAILLMClient(
            apiKey = requireNotNull(System.getenv("OPENAI_API_KEY")) {
                "OPENAI_API_KEY environment variable is required to use GPT-4o mini"
            }
        )
    }
    private val llmClient by llmClientDelegate

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
                    prompt = prompt(
                        id = "word_selector",
                        params = LLMParams(
                            temperature = 1.0,
                            maxTokens = 20,
                        )
                    ) {
                        system(
                            """
                                You are scoring one candidate continuation.
                                Output exactly the candidate word provided by the user.
                                Do not add punctuation.
                                Do not add explanation.
                                Output only the word.
                            """.trimIndent()
                        )
                        user(
                            """
                                Unfinished text:
                                "$sentence"

                                Candidate words:
                                ${words.joinToString(separator = ", ")}
                            """.trimIndent()
                        )
                    },
                    model = OpenAIModels.Chat.GPT4oMini
                )

                val selectedWord = response
                    .firstOrNull()
                    ?.content
                    ?.trim()
                logDebug(
                    type = "response",
                    message = "content = $selectedWord",
                )
                if (selectedWord == null) {
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
                        type = "success",
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
        if (llmClientDelegate.isInitialized()) {
            llmClient.close()
        }
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
