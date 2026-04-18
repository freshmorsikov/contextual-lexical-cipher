package com.github.freshmorsikov

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.structure.Structure
import ai.koog.prompt.structure.StructuredOutputPrompts
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.structure.json.JsonStructure
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import kotlin.text.lowercase

internal class LlmWordSelector : WordSelector, AutoCloseable {

    sealed interface Result {
        data class Success(val word: String) : Result
        data object Error : Result
    }

    @Serializable
    internal data class ScoredWordPayload(
        @SerialName("word")
        val word: String,
        @SerialName("naturalnessScore")
        val naturalnessScore: Double,
    )

    @Serializable
    internal data class ScoredWordsPayload(
        @SerialName("words")
        val words: List<ScoredWordPayload>,
    )

    companion object {
        private val LINKING_WORDS = loadWordResource("linking_words.txt")
        private const val MAX_ATTEMPTS = 3
        private val scoredWordsStructure = JsonStructure.create<ScoredWordsPayload>(
            id = "ScoredWords",
            serializer = serializer<ScoredWordsPayload>(),
            examples = listOf(
                ScoredWordsPayload(
                    words = listOf(
                        ScoredWordPayload(
                            word = "example",
                            naturalnessScore = 0.42,
                        )
                    )
                )
            )
        )
    }

    private val llmClientDelegate = lazy {
        OpenAILLMClient(
            apiKey = requireNotNull(System.getenv("OPENAI_API_KEY")) {
                "OPENAI_API_KEY environment variable is required to use GPT-4o mini"
            }
        )
    }
    private val llmClient by llmClientDelegate
    private val promptExecutor by lazy {
        StructuredPromptExecutor(llmClient)
    }

    private fun List<String>.toSentence(): String {
        return joinToString(separator = " ")//.ifBlank { "<empty>" }
    }

    private class StructuredPromptExecutor(
        private val llmClient: OpenAILLMClient,
    ) {
        suspend fun <T> executeStructured(
            prompt: ai.koog.prompt.dsl.Prompt,
            mainModel: ai.koog.prompt.llm.LLModel,
            structure: Structure<T, *>,
            retries: Int = 1,
        ): kotlin.Result<StructuredResponse<T>> {
            val structuredPrompt = prompt(prompt) {
                user {
                    StructuredOutputPrompts.outputInstructionPrompt(this, structure)
                    StructuredOutputPrompts.examplesPrompt(this, structure)
                }
            }

            repeat(retries) {
                val response = llmClient.execute(
                    prompt = structuredPrompt,
                    model = mainModel,
                ).singleOrNull() ?: return kotlin.Result.failure(
                    IllegalStateException("Expected exactly one LLM response")
                )

                val assistantMessage = response as? ai.koog.prompt.message.Message.Assistant
                    ?: return kotlin.Result.failure(
                        IllegalStateException("Expected assistant response, got ${response::class.qualifiedName}")
                    )

                try {
                    return kotlin.Result.success(
                        StructuredResponse(
                            data = structure.parse(assistantMessage.content),
                            structure = structure,
                            message = assistantMessage,
                        )
                    )
                } catch (_: SerializationException) {
                }
            }

            return kotlin.Result.failure(
                IllegalStateException("Unable to parse structured output after <$retries> retries")
            )
        }
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
                val response: kotlin.Result<StructuredResponse<ScoredWordsPayload>> = promptExecutor.executeStructured(
                    prompt = prompt("word_selector") {
                        system(
                            """
                                You will receive multiple lines in this format:
                                word = <candidate>; phrase = <full phrase with that candidate>

                                For each line, judge how natural the phrase sounds in normal English.
                                Return JSON as an object with a single field:
                                words: array of objects with:
                                - word: the exact candidate word from the input
                                - naturalnessScore: a number from 0.0 to 1.0

                                Higher score means the phrase sounds more natural.
                                Only use candidate words that appear in the input.
                                Do not add explanation or extra fields.
                            """.trimIndent()
                        )
                        user(
                            words.joinToString(separator = "\n") { word ->
                                "word = $word; phrase = $sentence $word"
                            }
                        )
                    },
                    mainModel = OpenAIModels.Chat.GPT4oMini,
                    structure = scoredWordsStructure
                )

                val a = response.getOrThrow()
                val scoredWords = response.getOrThrow().data.words
                logDebug(
                    type = "response",
                    message = buildString {
                        append("in: ${a.message.metaInfo.inputTokensCount} \n")
                        append("out: ${a.message.metaInfo.outputTokensCount} \n")
                        append("ScoredWords: $scoredWords")
                    }
                )
                val word = selectBestCandidate(
                    candidateWords = words,
                    scoredWords = scoredWords,
                )
                logDebug(
                    type = "success",
                    message = "selectedWord = $word",
                )

                word.toResult()
            }
        }.getOrElse {
            logDebug(
                type = "error",
                message = "${it::class.qualifiedName}: ${it.message}"
            )

            Result.Error
        }
    }

    private fun selectBestCandidate(
        candidateWords: List<String>,
        scoredWords: List<ScoredWordPayload>,
    ): String? {
        val candidateLowercaseWords = candidateWords.map { candidateWord ->
            candidateWord.lowercase()
        }
        return scoredWords
            .filter { word ->
                candidateLowercaseWords.contains(word.word.lowercase())
            }
            .maxByOrNull { word ->
                word.naturalnessScore
            }?.word
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
