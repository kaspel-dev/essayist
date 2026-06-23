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
    val liveEvaluatorUsed: Boolean = false,
) {

    val passed: Boolean
        get() = relevancy.passed && faithfulness.passed

    fun toFeedbackBlock(): String =
        """
        Evaluation report:
        - Relevancy: ${relevancy.statusLabel()} (${relevancy.scoreLabel()}) - ${relevancy.feedback}
        - Faithfulness: ${faithfulness.statusLabel()} (${faithfulness.scoreLabel()}) - ${faithfulness.feedback}
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
