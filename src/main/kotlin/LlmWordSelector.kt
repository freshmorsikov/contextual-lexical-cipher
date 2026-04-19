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

internal open class LlmWordSelector : WordSelector, AutoCloseable {

    internal data class PromptPayload(
        val systemPrompt: String,
        val userPrompt: String,
    )

    @Serializable
    internal data class ScoredWordPayload(
        @SerialName("word")
        val word: String,
        @SerialName("naturalnessScore")
        val naturalnessScore: Double,
        @SerialName("punctuation")
        val punctuation: String? = null,
    )

    @Serializable
    internal data class ScoredWordsPayload(
        @SerialName("words")
        val words: List<ScoredWordPayload>,
    )

    companion object {
        private val LINKING_WORDS = loadWordResource("linking_words.txt")
        private val ALLOWED_PUNCTUATION = setOf(",", ".", "!", "?", ":", ";")
        private const val MAX_ATTEMPTS = 3
        private val scoredWordsStructure = JsonStructure.create<ScoredWordsPayload>(
            id = "ScoredWords",
            serializer = serializer<ScoredWordsPayload>(),
            examples = listOf(
                ScoredWordsPayload(
                    words = listOf(
                        ScoredWordPayload(
                            word = "be",
                            naturalnessScore = 0.95,
                            punctuation = ".",
                        ),
                        ScoredWordPayload(
                            word = "develop",
                            naturalnessScore = 0.91,
                            punctuation = null,
                        ),
                        ScoredWordPayload(
                            word = "do",
                            naturalnessScore = 0.84,
                            punctuation = ",",
                        ),
                        ScoredWordPayload(
                            word = "prepare",
                            naturalnessScore = 0.7,
                            punctuation = null,
                        ),
                        ScoredWordPayload(
                            word = "stay",
                            naturalnessScore = 0.69,
                            punctuation = null,
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
        return joinToString(separator = " ")
    }

    internal fun buildPromptPayload(
        words: List<String>,
        sentence: String,
    ): PromptPayload {
        val systemPrompt = """
            You will receive the current phrase followed by a numbered list of candidate words.
            The prompt starts with the current phrase in this format:
            phrase = <current phrase>

            Each candidate item uses this format:
            <number>. word = <candidate>

            Choose the top 5 candidate words that best fit the existing text and make the phrase sound natural in normal English.
            Return JSON as an object with a single field:
            words: array of objects with:
            - word: the exact candidate word from the input
            - naturalnessScore: a number from 0.0 to 1.0
            - punctuation: either null, ",", ".", "!", "?", ":", ";" to optionally follow the chosen word

            Return at most 5 words, ordered from most probable to least probable.
            Higher score means the phrase sounds more natural.
            Care about repetition and overusing, do not repeat the same words too often, and try to build natural text.
            Only use candidate words that appear in the input.
            Use punctuation only when it makes the text sound more natural.
            The number is only an item label and is not part of the candidate word.
            Do not add explanation or extra fields.
        """.trimIndent()
        val userPrompt = buildString {
            appendLine("phrase = $sentence <new word here>")
            append(
                words.mapIndexed { index, word ->
                    "${index + 1}. word = $word"
                }.joinToString(separator = "\n")
            )
        }

        return PromptPayload(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
        )
    }

    internal fun buildFirstWordPromptPayload(
        words: List<String>,
    ): PromptPayload {
        val systemPrompt = """
            You will receive a numbered list of candidate words.

            Choose the top 5 candidate words that would work best as the best opening for a text and make the opening sound natural in normal English.
            Return JSON as an object with a single field:
            words: array of objects with:
            - word: the exact candidate word from the input
            - naturalnessScore: a number from 0.0 to 1.0
            - punctuation: null

            Return at most 5 words, ordered from most probable to least probable.
            Higher score means the word is a stronger, more natural opening word.
            Prefer words that feel natural as an opening word for a sentence or text.
            Only use candidate words that appear in the input.
            Do not add punctuation to the first word.
            The number is only an item label and is not part of the candidate word.
            Do not add explanation or extra fields.
        """.trimIndent()
        val userPrompt = words.mapIndexed { index, word ->
            "${index + 1}. word = $word"
        }.joinToString(separator = "\n")

        return PromptPayload(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
        )
    }

    private class StructuredPromptExecutor(
        private val llmClient: OpenAILLMClient,
    ) {
        suspend fun <T> executeStructured(
            prompt: ai.koog.prompt.dsl.Prompt,
            mainModel: ai.koog.prompt.llm.LLModel,
            structure: Structure<T, *>,
            retries: Int = 1,
        ): Result<StructuredResponse<T>> {
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
                ).singleOrNull() ?: return Result.failure(
                    IllegalStateException("Expected exactly one LLM response")
                )

                val assistantMessage = response as? ai.koog.prompt.message.Message.Assistant
                    ?: return Result.failure(
                        IllegalStateException("Expected assistant response, got ${response::class.qualifiedName}")
                    )

                try {
                    return Result.success(
                        StructuredResponse(
                            data = structure.parse(assistantMessage.content),
                            structure = structure,
                            message = assistantMessage,
                        )
                    )
                } catch (_: SerializationException) {
                }
            }

            return Result.failure(
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

        logDebug(
            type = "text",
            message = encodedWords.joinToString(separator = " "),
        )

        repeat(MAX_ATTEMPTS) { i ->
            val wordSet = if (i == MAX_ATTEMPTS - 1) {
                words
            } else {
                mixedWords
            }
            val sentence = (encodedWords + newWords).toSentence()
            val result = if (sentence.isBlank()) {
                getWord(
                    words = wordSet,
                    promptPayload = buildFirstWordPromptPayload(wordSet),
                )
            } else {
                getWord(
                    words = wordSet,
                    promptPayload = buildPromptPayload(
                        words = wordSet,
                        sentence = sentence,
                    ),
                )
            }
            result.onSuccess { word ->
                val selectedWord = if (sentence.isBlank()) {
                    word.removeTrailingAllowedPunctuation()
                } else {
                    word
                }
                newWords += selectedWord

                if (selectedWord.removeTrailingAllowedPunctuation() in words) {
                    return newWords.toSentence()
                }
            }
        }

        return "-"
    }

    internal open fun getWord(words: List<String>, promptPayload: PromptPayload): Result<String> {
        logDebug(
            type = "request",
            message = """
                Candidate words: ${words.joinToString(separator = ", ")}
            """.trimIndent(),
        )

        return runCatching {
            runBlocking {
                val response: Result<StructuredResponse<ScoredWordsPayload>> = promptExecutor.executeStructured(
                    prompt = prompt("word_selector") {
                        system(promptPayload.systemPrompt)
                        user(promptPayload.userPrompt)
                    },
                    mainModel = OpenAIModels.Chat.GPT5Mini,
                    structure = scoredWordsStructure
                )

                val structuredResponse = response.getOrThrow()
                val scoredWords = structuredResponse.data.words
                logDebug(
                    type = "response",
                    message = buildString {
                        append("in: ${structuredResponse.message.metaInfo.inputTokensCount} \n")
                        append("out: ${structuredResponse.message.metaInfo.outputTokensCount} \n")
                        append(scoredWords.joinToString { "${it.word}(${it.punctuation.orEmpty()}) (${it.naturalnessScore})" })
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

                word.toWordResult()
            }
        }.fold(
            onSuccess = { it },
            onFailure = {
                logDebug(
                    type = "error",
                    message = "${it::class.qualifiedName}: ${it.message}"
                )

                Result.failure(it)
            }
        )
    }

    internal fun selectBestCandidate(
        candidateWords: List<String>,
        scoredWords: List<ScoredWordPayload>,
    ): String? {
        val candidateLowercaseWords = candidateWords.map { candidateWord ->
            candidateWord.lowercase()
        }
        val selectedWord = scoredWords.firstOrNull { word ->
            candidateLowercaseWords.contains(word.word.lowercase())
        } ?: return null
        val punctuation = selectedWord.punctuation?.takeIf {
            it in ALLOWED_PUNCTUATION
        }.orEmpty()

        return selectedWord.word + punctuation
    }

    private fun String.removeTrailingAllowedPunctuation(): String {
        return if (lastOrNull()?.toString() in ALLOWED_PUNCTUATION) {
            dropLast(1)
        } else {
            this
        }
    }

    override fun close() {
        if (llmClientDelegate.isInitialized()) {
            llmClient.close()
        }
    }

    private fun logDebug(type: String, message: String) {
        println("================")
        println("LLM | $type")
        println(message)
    }

}
