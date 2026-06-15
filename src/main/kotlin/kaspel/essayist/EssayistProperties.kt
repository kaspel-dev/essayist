package kaspel.essayist

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "essayist")
data class EssayistProperties(
    val outputDir: String = "essays",
    val numberOfKeywords: Int = 5,
) {
    val resolvedOutputDir: String
        get() = outputDir.ifBlank { "essays" }

    val resolvedNumberOfKeywords: Int
        get() = numberOfKeywords.takeIf { it > 0 } ?: 5
}
