package kaspel.essayist

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.Ai
import com.embabel.agent.domain.io.UserInput
import com.embabel.common.ai.model.LlmOptions
import kaspel.essayist.guardrails.EssayStyleGuardRail
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

@Agent(description = "Write and review an essay about a given topic")
class EssayWriterAgent(
    private val properties: EssayistProperties,
    private val readingStatsTool: ReadingStatsTool,
    private val essayQualityTool: EssayQualityTool,
    private val diceContextService: DiceContextService,
    private val essayEvaluationService: EssayEvaluationService,
    private val essayStyleGuardRail: EssayStyleGuardRail,
) {

    @Action(description = "Build a DICE context capsule")
    fun buildContextCapsule(userInput: UserInput): EssayContextCapsule =
        diceContextService.capsuleForTopic(userInput.content)

    @Action(description = "Prepare a DICE-guided research brief")
    fun researchTopic(context: EssayContextCapsule): ResearchedTopic =
        ResearchedTopic(
            topic = context.topic,
            research = buildString {
                appendLine("DICE context brief for ${context.topic}.")
                appendLine()
                appendLine("Propositions to preserve:")
                context.propositions.forEach { proposition ->
                    appendLine("- ${proposition.text}")
                }
                appendLine()
                appendLine("Research questions to address:")
                context.retrievalQuestions.forEach { question ->
                    appendLine("- $question")
                }
                appendLine()
                appendLine("Writing constraints:")
                context.constraints.forEach { constraint ->
                    appendLine("- $constraint")
                }
            },
        )

    @Action(description = "Write a first draft of the essay")
    fun writeDraft(research: ResearchedTopic, context: EssayContextCapsule, ai: Ai): DraftEssay {
        return ai
            .withLlm(LlmOptions.withDefaults().withMaxTokens(4096))
            .withId("essay-draft-writer")
            .withPromptContributors(listOf(Personas.WRITER, Personas.JSON_OUTPUT))
            .withGuardRails(essayStyleGuardRail)
            .creating(DraftEssay::class.java)
            .fromPrompt(
                """
                Write an essay about: ${research.topic}

                Shared DICE context:
                ${context.promptBlock()}

                Use the following research to inform your writing:
                ${research.research}

                Keep it practical and beginner friendly.
                Use short sentences and plain language.
                Keep the essay between 700 and 900 words.
                Include at most one short code example if it helps.
                Write the content in Markdown.
                """.trimIndent()
            )
    }

    @Action(description = "Evaluate the draft for relevancy and faithfulness")
    fun evaluateDraft(draft: DraftEssay, research: ResearchedTopic, context: EssayContextCapsule): EvaluatedDraft {
        val evalReport = essayEvaluationService.evaluateDraft(draft, research, context)
        return EvaluatedDraft(
            title = draft.title,
            content = draft.content,
            evalReport = evalReport,
        )
    }

    @Action(description = "Review the draft with deterministic local checks")
    fun reviewDraft(draft: EvaluatedDraft, context: EssayContextCapsule): ReviewedEssay {
        val checklist = essayQualityTool.buildEssayQualityChecklist(draft.title, draft.content)
        val alignedPropositions = context.propositions.count { proposition ->
            draft.content.contains(proposition.subject, ignoreCase = true) ||
                draft.content.contains(proposition.target, ignoreCase = true) ||
                proposition.subject.isBlank()
        }
        val feedback =
            """
            Local review completed.
            DICE alignment: $alignedPropositions of ${context.propositions.size} proposition(s) have visible support in the draft.
            ${draft.evalReport.toFeedbackBlock()}
            $checklist
            """.trimIndent()

        return ReviewedEssay(
            title = draft.title.ifBlank { context.topic },
            content = draft.content,
            feedback = feedback,
        )
    }

    @Action(description = "Add a TLDR summary to the top of the essay")
    fun addTldr(essay: ReviewedEssay, context: EssayContextCapsule): FinalEssay {
        val tldr = context.propositions
            .maxByOrNull { it.importance }
            ?.text
            ?: "This essay explains ${essay.title} with a practical, context-grounded workflow."
        val contentWithTldr = "> **TLDR:** $tldr\n\n${essay.content}"
        return FinalEssay(essay.title, contentWithTldr, essay.feedback)
    }

    @AchievesGoal(description = "A reviewed and polished essay with front matter")
    @Action(description = "Add front matter to the top of the essay")
    fun addFrontMatter(essay: FinalEssay, context: EssayContextCapsule): PublishedEssay {
        val frontMatter = FrontMatter(
            description = "A DICE-guided essay about ${context.topic}.",
            tags = listOf("DICE", "Embabel", "Context Engineering"),
            keywords = context.entityMentions
                .map { it.span }
                .filter { it.isNotBlank() }
                .distinct()
                .take(properties.resolvedNumberOfKeywords)
                .ifEmpty { listOf("DICE", "context engineering") },
            readTime = readingStatsTool.calculateReadingStats(essay.content),
        )

        val slug = slugify(essay.title)
        val tags = frontMatter.tags.joinToString("\n") { "  - $it" }
        val keywords = frontMatter.keywords.joinToString("\n") { "  - $it" }

        val frontMatterBlock =
            """
            ---
            title: "${essay.title}"
            slug: $slug
            date: "${LocalDate.now()}T08:00:00.000Z"
            published: true
            description: "${frontMatter.description}"
            author: "Praveen Manvi"
            readTime: "${frontMatter.readTime}"
            tags:
            $tags
            keywords:
            $keywords
            ---
            """.trimIndent()

        val publishedEssay = PublishedEssay(
            title = essay.title,
            content = "$frontMatterBlock\n\n${essay.content}",
            feedback = essay.feedback,
        )
        writeToFile(publishedEssay)
        return publishedEssay
    }

    private fun writeToFile(essay: Essay) {
        val filename = "${slugify(essay.title)}.md"
        val outputDir = Path.of(properties.resolvedOutputDir)
        val filePath = outputDir.resolve(filename)

        try {
            Files.createDirectories(outputDir)
            Files.writeString(filePath, essay.content)
            log.info("Essay written to {}", filePath.toAbsolutePath())
        } catch (e: IOException) {
            log.error("Failed to write essay to {}: {}", filePath, e.message)
        }
    }

    private fun slugify(value: String): String =
        value
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    companion object {
        private val log = LoggerFactory.getLogger(EssayWriterAgent::class.java)
    }
}
