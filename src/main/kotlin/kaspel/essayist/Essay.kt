package kaspel.essayist

import java.util.Locale

sealed interface Essay {
    val title: String
    val content: String
}

data class DraftEssay(
    override val title: String = "",
    override val content: String = "",
) : Essay

data class EvaluatedDraft(
    override val title: String = "",
    override val content: String = "",
    val evalReport: EssayEvalReport = EssayEvalReport(),
) : Essay

data class ReviewedEssay(
    override val title: String = "",
    override val content: String = "",
    val feedback: String = "",
) : Essay

data class FinalEssay(
    override val title: String = "",
    override val content: String = "",
    val feedback: String = "",
) : Essay

data class PublishedEssay(
    override val title: String = "",
    override val content: String = "",
    val feedback: String = "",
) : Essay

data class FrontMatter(
    val description: String = "",
    val tags: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val readTime: String = "",
)

data class ResearchedTopic(
    val topic: String = "",
    val research: String = "",
)

data class EssayEvalReport(
    val relevancy: EssayEvalFinding = EssayEvalFinding(name = "relevancy"),
    val faithfulness: EssayEvalFinding = EssayEvalFinding(name = "faithfulness"),
    val dokimos: DokimosEvalReport = DokimosEvalReport(),
    val liveEvaluatorUsed: Boolean = false,
) {

    val passed: Boolean
        get() = relevancy.passed && faithfulness.passed && dokimos.passed

    fun toFeedbackBlock(): String =
        """
        Evaluation report:
        - Relevancy: ${relevancy.statusLabel()} (${relevancy.scoreLabel()}) - ${relevancy.feedback}
        - Faithfulness: ${faithfulness.statusLabel()} (${faithfulness.scoreLabel()}) - ${faithfulness.feedback}
        - Dokimos: ${dokimos.statusLabel()} (${dokimos.averageScoreLabel()}) - ${dokimos.summary}
        - Evaluator mode: ${if (liveEvaluatorUsed) "Spring AI LLM-as-judge" else "deterministic fallback"}
        """.trimIndent()
}

data class EssayEvalFinding(
    val name: String = "",
    val passed: Boolean = false,
    val score: Double = 0.0,
    val feedback: String = "",
    val evaluator: String = "",
) {

    fun statusLabel(): String = if (passed) "pass" else "needs attention"

    fun scoreLabel(): String = "%.2f".format(Locale.US, score.coerceIn(0.0, 1.0))
}

data class DokimosEvalReport(
    val datasetName: String = "Essay draft quality gates",
    val checks: List<DokimosEvalCheck> = emptyList(),
) {

    val passed: Boolean
        get() = checks.isNotEmpty() && checks.all { it.passed }

    val passRate: Double
        get() = if (checks.isEmpty()) 0.0 else checks.count { it.passed }.toDouble() / checks.size.toDouble()

    val averageScore: Double
        get() = if (checks.isEmpty()) 0.0 else checks.map { it.score.coerceIn(0.0, 1.0) }.average()

    val summary: String
        get() = if (checks.isEmpty()) {
            "No Dokimos checks were run."
        } else {
            "${checks.count { it.passed }} of ${checks.size} checks passed across the $datasetName dataset."
        }

    fun statusLabel(): String = if (passed) "pass" else "needs attention"

    fun averageScoreLabel(): String = "%.2f".format(Locale.US, averageScore.coerceIn(0.0, 1.0))

    fun passRateLabel(): String = "%.0f%%".format(Locale.US, passRate.coerceIn(0.0, 1.0) * 100.0)
}

data class DokimosEvalCheck(
    val name: String = "",
    val passed: Boolean = false,
    val score: Double = 0.0,
    val threshold: Double = 1.0,
    val reason: String = "",
    val input: String = "",
)
