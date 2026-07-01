package kaspel.essayist

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator
import org.springframework.ai.chat.evaluation.RelevancyEvaluator
import org.springframework.ai.document.Document
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.ai.evaluation.EvaluationResponse
import org.springframework.ai.evaluation.Evaluator
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Service
class EssayEvaluationService(
    private val chatClientBuilderProvider: ObjectProvider<ChatClient.Builder>,
    private val properties: EssayistProperties,
    private val dokimosEvaluationService: DokimosEvaluationService,
) {

    fun evaluateDraft(
        draft: DraftEssay,
        research: ResearchedTopic,
        context: EssayContextCapsule,
    ): EssayEvalReport {
        val relevancyRequest = EvaluationRequest(
            context.topic,
            listOf(Document(context.promptBlock()), Document(research.research)),
            draft.content,
        )
        val faithfulnessRequest = EvaluationRequest(
            "${context.promptBlock()}\n\nResearch brief:\n${research.research}",
            emptyList<Document>(),
            draft.content,
        )
        val dokimosReport = dokimosEvaluationService.evaluateDraft(draft, research, context)

        val liveReport = if (properties.liveEvalsEnabled) {
            evaluateWithSpringAi(relevancyRequest, faithfulnessRequest)
        } else {
            null
        }

        return liveReport?.copy(dokimos = dokimosReport) ?: EssayEvalReport(
            relevancy = heuristicRelevancyEvaluator(context)
                .evaluate(relevancyRequest)
                .toFinding(name = "relevancy", evaluator = "deterministic-relevancy"),
            faithfulness = heuristicFaithfulnessEvaluator(context, research)
                .evaluate(faithfulnessRequest)
                .toFinding(name = "faithfulness", evaluator = "deterministic-faithfulness"),
            dokimos = dokimosReport,
            liveEvaluatorUsed = false,
        )
    }

    private fun evaluateWithSpringAi(
        relevancyRequest: EvaluationRequest,
        faithfulnessRequest: EvaluationRequest,
    ): EssayEvalReport? {
        val chatClientBuilder = chatClientBuilderProvider.getIfAvailable()
        if (chatClientBuilder == null) {
            log.warn("Spring AI live evals were enabled, but no ChatClient.Builder bean is available")
            return null
        }

        return try {
            val relevancy = RelevancyEvaluator(chatClientBuilder.clone())
                .evaluate(relevancyRequest)
                .toFinding(name = "relevancy", evaluator = "spring-ai-relevancy")
            val faithfulness = FactCheckingEvaluator.builder(chatClientBuilder.clone())
                .build()
                .evaluate(faithfulnessRequest)
                .toFinding(name = "faithfulness", evaluator = "spring-ai-fact-checking")

            EssayEvalReport(
                relevancy = relevancy,
                faithfulness = faithfulness,
                liveEvaluatorUsed = true,
            )
        } catch (e: Exception) {
            log.warn("Spring AI live evals failed; falling back to deterministic evals", e)
            null
        }
    }

    private fun heuristicRelevancyEvaluator(context: EssayContextCapsule): Evaluator =
        Evaluator { request ->
            val content = request.responseContent.orEmpty()
            val normalizedContent = content.lowercase(Locale.US)
            val topicTerms = context.topic
                .split(Regex("[^A-Za-z0-9+#.]+"))
                .map { it.trim().lowercase(Locale.US) }
                .filter { it.length >= 3 }
                .distinct()
                .take(MAX_EVAL_TERMS)
            val termCoverage = topicTerms.coverageIn(normalizedContent)
            val propositionCoverage = context.propositions
                .map { listOf(it.subject, it.target, it.text).bestCoverageTerm() }
                .filter { it.isNotBlank() }
                .coverageIn(normalizedContent)
            val score = averageNonEmpty(termCoverage, propositionCoverage)
            val passed = content.isNotBlank() && score >= properties.resolvedEvalMinimumScore

            EvaluationResponse(
                passed,
                score.toFloat(),
                "Matched %.0f%% of topic/proposition signals from the DICE context.".format(Locale.US, score * 100),
                mapOf(
                    "topicTerms" to topicTerms,
                    "minimumScore" to properties.resolvedEvalMinimumScore,
                ),
            )
        }

    private fun heuristicFaithfulnessEvaluator(
        context: EssayContextCapsule,
        research: ResearchedTopic,
    ): Evaluator =
        Evaluator { request ->
            val content = request.responseContent.orEmpty()
            val normalizedContent = content.lowercase(Locale.US)
            val groundingText = "${context.promptBlock()}\n${research.research}".lowercase(Locale.US)
            val contextAnchors = context.propositions
                .flatMap { listOf(it.subject, it.target) }
                .map { it.trim().lowercase(Locale.US) }
                .filter { it.length >= 3 }
                .distinct()
                .take(MAX_EVAL_TERMS)
            val anchorCoverage = contextAnchors.coverageIn(normalizedContent)
            val unsupportedRisk = RISKY_CURRENT_CLAIM.findAll(normalizedContent)
                .map { it.value }
                .filterNot { groundingText.contains(it) }
                .toList()
            val riskPenalty = min(0.35, unsupportedRisk.size * 0.07)
            val score = (0.72 + (anchorCoverage * 0.28) - riskPenalty).coerceIn(0.0, 1.0)
            val passed = content.isNotBlank() && score >= properties.resolvedEvalMinimumScore
            val feedback = if (unsupportedRisk.isEmpty()) {
                "Draft stays anchored to the supplied DICE context and avoids unsupported current-fact language."
            } else {
                "Draft contains version-sensitive language that should be source-checked: ${unsupportedRisk.distinct().joinToString(", ")}."
            }

            EvaluationResponse(
                passed,
                score.toFloat(),
                feedback,
                mapOf(
                    "anchors" to contextAnchors,
                    "unsupportedRiskTerms" to unsupportedRisk.distinct(),
                    "minimumScore" to properties.resolvedEvalMinimumScore,
                ),
            )
        }

    private fun EvaluationResponse.toFinding(name: String, evaluator: String): EssayEvalFinding =
        EssayEvalFinding(
            name = name,
            passed = isPass,
            score = score.toDouble().coerceIn(0.0, 1.0),
            feedback = feedback.orEmpty().ifBlank { "No evaluator feedback was returned." },
            evaluator = evaluator,
        )

    private fun List<String>.coverageIn(normalizedContent: String): Double {
        if (isEmpty()) {
            return 1.0
        }
        val matched = count { normalizedContent.contains(it.lowercase(Locale.US)) }
        return matched.toDouble() / size.toDouble()
    }

    private fun List<String>.bestCoverageTerm(): String =
        map { it.trim() }
            .filter { it.length >= 3 }
            .maxByOrNull { it.length }
            .orEmpty()

    private fun averageNonEmpty(vararg values: Double): Double =
        values
            .filter { !it.isNaN() }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.let { max(0.0, min(1.0, it)) }
            ?: 0.0

    companion object {
        private val log = LoggerFactory.getLogger(EssayEvaluationService::class.java)
        private val RISKY_CURRENT_CLAIM = Regex("\\b(latest|today|currently|always|never|guaranteed|proven)\\b")
        private const val MAX_EVAL_TERMS = 8
    }
}
