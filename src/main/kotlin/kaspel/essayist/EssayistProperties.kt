package kaspel.essayist

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "essayist")
data class EssayistProperties(
    val outputDir: String = "essays",
    val numberOfKeywords: Int = 5,
    val evals: EssayEvalProperties = EssayEvalProperties(),
) {
    val resolvedOutputDir: String
        get() = outputDir.ifBlank { "essays" }

    val resolvedNumberOfKeywords: Int
        get() = numberOfKeywords.takeIf { it > 0 } ?: 5

    val liveEvalsEnabled: Boolean
        get() = evals.live

    val resolvedEvalMinimumScore: Double
        get() = evals.minimumScore.coerceIn(0.0, 1.0)
}

data class EssayEvalProperties(
    val live: Boolean = false,
    val minimumScore: Double = 0.6,
)
