package kaspel.essayist

import com.embabel.agent.api.annotation.LlmTool
import org.springframework.stereotype.Component

@Component
class EssayQualityTool {

    @LlmTool(
        name = "build_essay_quality_checklist",
        description = "Build a deterministic review checklist for a technical essay draft."
    )
    fun buildEssayQualityChecklist(
        @LlmTool.Param(description = "Essay title")
        title: String?,
        @LlmTool.Param(description = "Essay Markdown content")
        content: String?,
    ): String {
        val safeTitle = title.orEmpty().trim().ifBlank { "Untitled" }
        val safeContent = content.orEmpty()
        val words = safeContent.trim().takeIf { it.isNotBlank() }
            ?.split(Regex("\\s+"))
            ?.size
            ?: 0
        val headings = Regex("""(?m)^#{2,6}\s+""").findAll(safeContent).count()
        val codeBlocks = Regex("""(?m)^```""").findAll(safeContent).count() / 2
        val links = Regex("""https?://[^\s)]+""").findAll(safeContent).count()

        return """
            Review checklist for "$safeTitle":
            - Word count: $words
            - Section headings: $headings
            - Code examples: $codeBlocks
            - Source links: $links
            - Check that the introduction states who the essay is for.
            - Check that each major claim is supported by either research context or a source link.
            - Check that examples are short enough for a beginner to scan.
            - Check that the conclusion gives practical next steps.
        """.trimIndent()
    }
}
