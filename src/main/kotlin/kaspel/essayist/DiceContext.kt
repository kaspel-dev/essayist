package kaspel.essayist

import com.embabel.dice.common.ContentHasher
import com.embabel.dice.common.support.Sha256ContentHasher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Configuration
class DiceContextConfiguration {

    @Bean
    fun diceContentHasher(): ContentHasher = Sha256ContentHasher
}

@Service
class DiceContextService(
    private val contentHasher: ContentHasher,
) {

    private val processedHashes = ConcurrentHashMap.newKeySet<String>()

    fun prepareRequest(topic: String): DiceContextRequest {
        val normalizedTopic = topic.normalizeContextText().ifBlank { "Untitled essay topic" }
        val sourceHash = contentHasher.hash(normalizedTopic)
        return DiceContextRequest(
            topic = normalizedTopic,
            contextId = "essay-${sourceHash.take(16)}",
            sourceHash = sourceHash,
            duplicateSource = !processedHashes.add(sourceHash),
        )
    }

    fun assemble(request: DiceContextRequest, extraction: DiceContextExtraction): EssayContextCapsule {
        val propositions = extraction.propositions
            .mapNotNull { it.sanitized() }
            .distinctBy { it.text.lowercase(Locale.US) }
            .take(MAX_PROPOSITIONS)
            .ifEmpty { listOf(request.defaultProposition()) }

        val mentions = extraction.entityMentions
            .mapNotNull { it.sanitized() }
            .distinctBy { "${it.span.lowercase(Locale.US)}:${it.type.lowercase(Locale.US)}" }
            .take(MAX_ENTITY_MENTIONS)

        return EssayContextCapsule(
            topic = request.topic,
            contextId = request.contextId,
            sourceHash = request.sourceHash,
            duplicateSource = request.duplicateSource,
            propositions = propositions,
            entityMentions = mentions,
            constraints = extraction.constraints.cleanTextList(MAX_CONSTRAINTS)
                .ifEmpty { listOf("Keep research, drafting, and review aligned to the extracted DICE propositions.") },
            retrievalQuestions = extraction.retrievalQuestions.cleanTextList(MAX_RETRIEVAL_QUESTIONS)
                .ifEmpty { listOf("What current, verifiable sources clarify ${request.topic}?") },
        )
    }

    fun capsuleForTopic(topic: String): EssayContextCapsule {
        val request = prepareRequest(topic)
        return assemble(request, heuristicExtraction(request))
    }

    fun demoCapsule(topic: String): EssayContextCapsule {
        val request = prepareRequest(topic)
        return assemble(
            request = request,
            extraction = DiceContextExtraction(
                propositions = listOf(
                    DiceContextProposition(
                        text = "The essay should explain ${request.topic} for readers who need a practical starting point.",
                        subject = request.topic,
                        predicate = "should be explained for",
                        target = "practical beginner readers",
                        confidence = 0.82,
                        importance = 0.88,
                        decay = 0.08,
                        reasoning = "The user asked for an essay topic rather than a narrow implementation ticket.",
                    ),
                    DiceContextProposition(
                        text = "The draft should preserve a clear distinction between domain context and model-generated content.",
                        subject = "draft",
                        predicate = "should preserve",
                        target = "domain context boundaries",
                        confidence = 0.78,
                        importance = 0.84,
                        decay = 0.12,
                        reasoning = "Context engineering is useful only when the generated essay keeps provenance visible.",
                    ),
                ),
                entityMentions = listOf(
                    DiceEntityMention(span = request.topic, type = "Topic", role = "SUBJECT"),
                    DiceEntityMention(span = "reader", type = "Audience", role = "OBJECT"),
                ),
                constraints = listOf(
                    "Use atomic claims that can be checked during review.",
                    "Prefer concrete examples over broad claims.",
                ),
                retrievalQuestions = listOf(
                    "Which facts about ${request.topic} need current source verification?",
                    "Which examples will make the topic useful to a JVM/Spring developer?",
                ),
            ),
        )
    }

    private fun heuristicExtraction(request: DiceContextRequest): DiceContextExtraction {
        val topic = request.topic
        val focusTerms = topic
            .split(Regex("[^A-Za-z0-9+#.]+"))
            .map { it.trim() }
            .filter { it.length >= 3 }
            .distinctBy { it.lowercase(Locale.US) }
            .take(6)

        return DiceContextExtraction(
            propositions = listOf(
                DiceContextProposition(
                    text = "The essay should answer the user's request about $topic.",
                    subject = topic,
                    predicate = "should answer",
                    target = "user request",
                    confidence = 0.92,
                    importance = 0.95,
                    decay = 0.05,
                    reasoning = "The submitted topic is the source of truth for the essay workflow.",
                ),
                DiceContextProposition(
                    text = "The essay should separate durable domain concepts from current implementation details.",
                    subject = "essay",
                    predicate = "should separate",
                    target = "domain concepts and implementation details",
                    confidence = 0.76,
                    importance = 0.84,
                    decay = 0.18,
                    reasoning = "Context engineering benefits from explicit boundaries between stable concepts and changing facts.",
                ),
                DiceContextProposition(
                    text = "Research should verify any current facts before they are used in the draft.",
                    subject = "research",
                    predicate = "should verify",
                    target = "current facts",
                    confidence = 0.82,
                    importance = 0.9,
                    decay = 0.32,
                    reasoning = "The web research action can ground time-sensitive claims before drafting.",
                ),
            ),
            entityMentions = listOf(
                DiceEntityMention(span = topic, type = "Topic", role = "SUBJECT"),
            ) + focusTerms.map { DiceEntityMention(span = it, type = "Keyword", role = "OTHER") },
            constraints = listOf(
                "Keep the essay aligned to the source topic and extracted propositions.",
                "Prefer concrete examples and implementation tradeoffs over broad claims.",
                "Mark current or version-sensitive facts as research-dependent.",
            ),
            retrievalQuestions = listOf(
                "What are the most current facts or APIs relevant to $topic?",
                "Which examples would make $topic useful to a JVM or Spring developer?",
                "What limitations or risks should a product team understand before applying $topic?",
            ),
        )
    }

    private fun DiceContextRequest.defaultProposition(): DiceContextProposition =
        DiceContextProposition(
            text = "The essay should explain $topic in practical terms.",
            subject = topic,
            predicate = "should explain",
            target = "practical reader needs",
            confidence = 0.6,
            importance = 0.7,
            decay = 0.1,
            reasoning = "Fallback proposition created because extraction returned no usable propositions.",
        )

    companion object {
        private const val MAX_PROPOSITIONS = 6
        private const val MAX_ENTITY_MENTIONS = 10
        private const val MAX_CONSTRAINTS = 6
        private const val MAX_RETRIEVAL_QUESTIONS = 5
    }
}

data class DiceContextRequest(
    val topic: String,
    val contextId: String,
    val sourceHash: String,
    val duplicateSource: Boolean,
)

data class DiceContextExtraction(
    val propositions: List<DiceContextProposition> = emptyList(),
    val entityMentions: List<DiceEntityMention> = emptyList(),
    val constraints: List<String> = emptyList(),
    val retrievalQuestions: List<String> = emptyList(),
)

data class EssayContextCapsule(
    val topic: String = "",
    val contextId: String = "",
    val sourceHash: String = "",
    val duplicateSource: Boolean = false,
    val propositions: List<DiceContextProposition> = emptyList(),
    val entityMentions: List<DiceEntityMention> = emptyList(),
    val constraints: List<String> = emptyList(),
    val retrievalQuestions: List<String> = emptyList(),
) {

    fun promptBlock(): String =
        """
        DICE context capsule
        Context id: $contextId
        Source hash: $sourceHash
        Duplicate source: ${if (duplicateSource) "yes" else "no"}

        Atomic propositions:
        ${propositions.joinToString("\n") { "- ${it.promptLine()}" }}

        Entity mentions:
        ${entityMentions.joinToString("\n") { "- ${it.promptLine()}" }.ifBlank { "- none extracted" }}

        Writing constraints:
        ${constraints.joinToString("\n") { "- $it" }}

        Retrieval questions:
        ${retrievalQuestions.joinToString("\n") { "- $it" }}
        """.trimIndent()
}

data class DiceContextProposition(
    val text: String = "",
    val subject: String = "",
    val predicate: String = "",
    val target: String = "",
    val confidence: Double = 0.5,
    val importance: Double = 0.5,
    val decay: Double = 0.0,
    val reasoning: String = "",
) {

    fun promptLine(): String =
        "$text (confidence=${confidence.asScore()}, importance=${importance.asScore()}, decay=${decay.asScore()})"
}

data class DiceEntityMention(
    val span: String = "",
    val type: String = "",
    val role: String = "OTHER",
) {

    fun promptLine(): String =
        "${span.ifBlank { "unknown" }} [${type.ifBlank { "Entity" }}, ${role.ifBlank { "OTHER" }}]"
}

private fun DiceContextProposition.sanitized(): DiceContextProposition? {
    val cleanText = text.normalizeContextText()
    if (cleanText.isBlank()) {
        return null
    }
    return copy(
        text = cleanText,
        subject = subject.normalizeContextText(),
        predicate = predicate.normalizeContextText(),
        target = target.normalizeContextText(),
        confidence = confidence.clampScore(),
        importance = importance.clampScore(),
        decay = decay.clampScore(),
        reasoning = reasoning.normalizeContextText(),
    )
}

private fun DiceEntityMention.sanitized(): DiceEntityMention? {
    val cleanSpan = span.normalizeContextText()
    if (cleanSpan.isBlank()) {
        return null
    }
    return copy(
        span = cleanSpan,
        type = type.normalizeContextText().ifBlank { "Entity" },
        role = role.normalizeContextText().uppercase(Locale.US).ifBlank { "OTHER" },
    )
}

private fun List<String>.cleanTextList(limit: Int): List<String> =
    map { it.normalizeContextText() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.US) }
        .take(limit)

private fun String.normalizeContextText(): String =
    replace(Regex("\\s+"), " ")
        .trim()

private fun Double.clampScore(): Double =
    when {
        isNaN() -> 0.0
        this < 0.0 -> 0.0
        this > 1.0 -> 1.0
        else -> this
    }

private fun Double.asScore(): String =
    String.format(Locale.US, "%.2f", clampScore())
