package kaspel.essayist

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HtmlFragmentsTests {

    @Test
    fun `published essay renders structured html output`() {
        val essay = PublishedEssay(
            title = "Spring AI with Embabel",
            feedback = "Reviewed and tightened.",
            content = """
                ---
                title: "Spring AI with Embabel"
                slug: spring-ai-with-embabel
                description: "A practical walkthrough."
                readTime: "3 min read"
                tags:
                  - Spring AI
                  - Embabel
                keywords:
                  - agents
                  - tool calling
                ---
                
                > **TLDR:** Build typed agent workflows.
                
                ## Why it matters
                
                Use `@Action` methods to model the workflow.
                
                ```kotlin
                fun main() = println("hello")
                ```
            """.trimIndent(),
        )

        val html = HtmlFragments.publishedEssay(essay)

        assertThat(html).contains("Structured HTML output")
        assertThat(html).contains("<article class=\"article-preview\">")
        assertThat(html).contains("<strong>TLDR:</strong>")
        assertThat(html).contains("<code>@Action</code>")
        assertThat(html).contains("class=\"language-kotlin\"")
        assertThat(html).contains("<span class=\"chip\">Spring AI</span>")
        assertThat(html).contains("Markdown artifact")
    }

    @Test
    fun `run shell includes replaceable goal rows and explainability target`() {
        val run = EssayRun(id = "test-run", topic = "Explain Spring AI")

        val shell = HtmlFragments.runShell(run)
        val update = HtmlFragments.actionGoalUpdate(
            run = run,
            actionName = "buildContextCapsule",
            state = "active",
            detail = "Building DICE context.",
        )

        assertThat(shell).contains("DICE")
        assertThat(shell).contains("id=\"run-test-run-goal-context\"")
        assertThat(shell).contains("id=\"run-test-run-goal-research\"")
        assertThat(shell).contains("id=\"run-test-run-explainability\"")
        assertThat(update).contains("data-replace-target=\"#run-test-run-goal-context\"")
        assertThat(update).contains("Building DICE context.")
    }

    @Test
    fun `context capsule renders dice propositions and provenance`() {
        val capsule = EssayContextCapsule(
            topic = "Context engineering with DICE",
            contextId = "essay-123",
            sourceHash = "abcdef1234567890abcdef",
            propositions = listOf(
                DiceContextProposition(
                    text = "DICE turns prompts into typed context.",
                    confidence = 0.87,
                    importance = 0.91,
                    decay = 0.1,
                    reasoning = "The topic names context engineering.",
                )
            ),
            entityMentions = listOf(DiceEntityMention(span = "DICE", type = "Library", role = "SUBJECT")),
            constraints = listOf("Keep propositions atomic."),
            retrievalQuestions = listOf("Which DICE APIs support context engineering?"),
        )

        val html = HtmlFragments.contextCapsule(capsule)

        assertThat(html).contains("DICE context capsule")
        assertThat(html).contains("essay-123")
        assertThat(html).contains("abcdef1234567890")
        assertThat(html).contains("DICE turns prompts into typed context.")
        assertThat(html).contains("87%")
        assertThat(html).contains("Library / SUBJECT")
        assertThat(html).contains("Keep propositions atomic.")
    }

    @Test
    fun `published report renders chart output`() {
        val report = PublishedReport(
            title = "Revenue report",
            request = "Show revenue by month",
            executiveSummary = "Revenue increased across the period.",
            metrics = listOf(
                ReportMetric(
                    label = "Revenue",
                    value = "120000",
                    unit = "USD",
                    detail = "Current period revenue.",
                    trend = "up",
                )
            ),
            findings = listOf("March was the strongest month."),
            charts = listOf(
                ReportChart(
                    title = "Monthly revenue",
                    type = "bar",
                    unit = "USD",
                    xAxisLabel = "Month",
                    yAxisLabel = "Revenue",
                    series = listOf(
                        ReportSeries(
                            name = "Revenue",
                            points = listOf(
                                ReportPoint("Jan", 80_000.0),
                                ReportPoint("Feb", 95_000.0),
                                ReportPoint("Mar", 120_000.0),
                            )
                        )
                    )
                )
            ),
            sourceNotes = listOf("Loaded from Superset MCP."),
        )

        val html = HtmlFragments.publishedReport(report)

        assertThat(html).contains("Chart report")
        assertThat(html).contains("metric-card--up")
        assertThat(html).contains("<svg class=\"chart-svg\"")
        assertThat(html).contains("<rect")
        assertThat(html).contains("Monthly revenue")
        assertThat(html).contains("Report data")
    }

    @Test
    fun `report run shell includes replaceable goal rows and explainability target`() {
        val run = ReportRun(id = "report-test-run", prompt = "Show revenue by month")

        val shell = HtmlFragments.reportRunShell(run)
        val update = HtmlFragments.reportActionGoalUpdate(
            run = run,
            actionName = "queryReportData",
            state = "active",
            detail = "Querying Superset MCP.",
        )

        assertThat(shell).contains("id=\"report-run-report-test-run-goal-query\"")
        assertThat(shell).contains("id=\"report-run-report-test-run-explainability\"")
        assertThat(update).contains("data-replace-target=\"#report-run-report-test-run-goal-query\"")
        assertThat(update).contains("Querying Superset MCP.")
    }
}
