package kaspel.essayist

sealed interface Essay {
    val title: String
    val content: String
}

data class DraftEssay(
    override val title: String = "",
    override val content: String = "",
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
