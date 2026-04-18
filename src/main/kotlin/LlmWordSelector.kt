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

internal class LlmWordSelector : WordSelector, AutoCloseable {

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
        return joinToString(separator = " ")
    }

    internal fun buildPromptPayload(
        words: List<String>,
        sentence: String,
    ): PromptPayload {
        val systemPrompt = """
            You will receive a numbered list.
            Each item uses this format:
            <number>. word = <candidate>; phrase = <full phrase with that candidate>

            Choose the top 5 candidate words that best fit the existing text and make the phrase sound natural in normal English.
            Return JSON as an object with a single field:
            words: array of objects with:
            - word: the exact candidate word from the input
            - naturalnessScore: a number from 0.0 to 1.0

            Return at most 5 words, ordered from most probable to least probable.
            Higher score means the phrase sounds more natural.
            Care about repetition and overusing, do not repeat the same words too often, and try to build natural text.
            Only use candidate words that appear in the input.
            The number is only an item label and is not part of the candidate word.
            Do not add explanation or extra fields.
        """.trimIndent()
        val userPrompt = words.mapIndexed { index, word ->
            "${index + 1}. word = $word; phrase = $sentence $word"
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
        val mixedWords = words + LINKING_WORDS
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
            result.onSuccess { word ->
                newWords += word

                if (word in words) {
                    return newWords.toSentence()
                }
            }
        }

        return "-"
    }

    private fun getWord(words: List<String>, sentence: String): Result<String> {
        val promptPayload = buildPromptPayload(
            words = words,
            sentence = sentence,
        )
        logDebug(
            type = "request",
            message = """
                Current sentence: $sentence
                Candidate words: ${words.joinToString(separator = ", ")}
            """.trimIndent()
        )

        return runCatching {
            runBlocking {
                val response: Result<StructuredResponse<ScoredWordsPayload>> = promptExecutor.executeStructured(
                    prompt = prompt("word_selector") {
                        system(promptPayload.systemPrompt)
                        user(promptPayload.userPrompt)
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
        return scoredWords.firstOrNull { word ->
            candidateLowercaseWords.contains(word.word.lowercase())
        }?.word
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
