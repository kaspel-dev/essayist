package kaspel.essayist

import java.time.LocalDate
import java.util.Locale

object DemoArtifacts {

    fun essay(topic: String): PublishedEssay {
        val title = topic.toDisplayTitle("Demo Essay")
        val slug = title.slugifyForDemo()
        val content = """
            ---
            title: "${title.yamlValue()}"
            slug: $slug
            date: "${LocalDate.now()}T08:00:00.000Z"
            published: true
            description: "A fast demo essay showing the Embabel action pipeline, typed outputs, and local tool usage."
            author: "Praveen Manvi"
            readTime: "3 min read"
            tags:
              - Embabel
              - Spring AI
              - Agent Architecture
            keywords:
              - typed actions
              - tool calling
              - agent workflows
            ---
            
            > **TLDR:** Embabel agents are easier to explain and operate when each step has a typed input, a typed output, and a clear goal.
            
            ## Why this topic matters
            
            ${title} is a useful example because it shows that an agent is more than one large prompt. A production agent needs explicit state, controlled tools, observable execution, and a goal object that the rest of the application can trust.
            
            ## The core pattern
            
            The workflow starts with a user input and moves through typed states. Each `@Action` method produces the next object the planner needs.
            
            ```kotlin
            @Action(description = "Write a first draft")
            fun writeDraft(research: ResearchedTopic, ai: Ai): DraftEssay
            ```
            
            This keeps the business process visible in code. It also lets Embabel plan the path to a final `@AchievesGoal` result.
            
            ## Tool use
            
            Tools should do deterministic work or provide controlled access to external systems. In this project, reading-time calculation and review checklists are local tools, while web and Superset capabilities can come from tool groups.
            
            ## Takeaway
            
            Model the workflow first, then let the LLM operate inside the boundaries of that workflow. The result is easier to demo, test, observe, and extend.
        """.trimIndent()

        return PublishedEssay(
            title = title,
            content = content,
            feedback = "Demo mode rendered a deterministic essay without calling an external model.",
        )
    }

    fun report(prompt: String): PublishedReport {
        val title = prompt.toDisplayTitle("Demo Report")
        return PublishedReport(
            title = title,
            request = prompt.ifBlank { "Show revenue trend by month and conversion by segment" },
            executiveSummary = "The demo report shows a rising revenue trend, a mid-period dip, and stronger conversion from high-intent segments.",
            metrics = listOf(
                ReportMetric(
                    label = "Revenue",
                    value = "\$1.24M",
                    unit = "USD",
                    detail = "Current demo period revenue.",
                    trend = "up",
                ),
                ReportMetric(
                    label = "Conversion Rate",
                    value = "18.6%",
                    unit = "rate",
                    detail = "Weighted conversion across all demo segments.",
                    trend = "up",
                ),
                ReportMetric(
                    label = "Best Segment",
                    value = "Search",
                    unit = "segment",
                    detail = "Highest intent and lowest acquisition waste.",
                    trend = "flat",
                ),
                ReportMetric(
                    label = "At Risk",
                    value = "Display",
                    unit = "segment",
                    detail = "Lower conversion despite broad reach.",
                    trend = "down",
                ),
            ),
            findings = listOf(
                "Revenue increased from January to June, with a short dip in April.",
                "Search and Performance Max lead conversion quality in this sample.",
                "Display requires creative or targeting changes before additional budget.",
            ),
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
                                ReportPoint("Jan", 120_000.0),
                                ReportPoint("Feb", 154_000.0),
                                ReportPoint("Mar", 193_000.0),
                                ReportPoint("Apr", 176_000.0),
                                ReportPoint("May", 252_000.0),
                                ReportPoint("Jun", 345_000.0),
                            ),
                        )
                    ),
                ),
                ReportChart(
                    title = "Conversion by segment",
                    type = "bar",
                    unit = "%",
                    xAxisLabel = "Segment",
                    yAxisLabel = "Conversion rate",
                    series = listOf(
                        ReportSeries(
                            name = "Conversion",
                            points = listOf(
                                ReportPoint("Search", 28.4),
                                ReportPoint("PMax", 24.1),
                                ReportPoint("Shopping", 16.8),
                                ReportPoint("Video", 11.3),
                                ReportPoint("Display", 6.2),
                            ),
                        )
                    ),
                ),
            ),
            sourceNotes = listOf(
                "Demo mode uses deterministic sample data so the UI can be presented without waiting for LLM or MCP calls.",
                "Turn off demo mode to query the configured Superset MCP server.",
            ),
        )
    }

    private fun String.toDisplayTitle(fallback: String): String =
        trim()
            .replace(Regex("\\s+"), " ")
            .removePrefix("Write about ")
            .removePrefix("write about ")
            .ifBlank { fallback }
            .let { value -> if (value.length <= 70) value else "${value.take(67)}..." }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    private fun String.slugifyForDemo(): String =
        lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "demo-output" }

    private fun String.yamlValue(): String =
        replace("\"", "'")
}
