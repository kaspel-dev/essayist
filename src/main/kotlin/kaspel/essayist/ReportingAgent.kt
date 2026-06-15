package kaspel.essayist

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.Ai
import com.embabel.agent.domain.io.UserInput
import com.embabel.common.ai.model.LlmOptions

@Agent(description = "Build chart-ready business reports using Superset MCP data tools")
class ReportingAgent {

    @Action(description = "Query Superset MCP for reporting data")
    fun queryReportData(userInput: UserInput, ai: Ai): ReportData =
        ai
            .withDefaultLlm()
            .withToolGroup(ReportingToolGroups.SUPER_MCP_REPORTING)
            .withId("super-mcp-report-query")
            .withPromptContributors(listOf(Personas.JSON_OUTPUT))
            .creating(ReportData::class.java)
            .fromPrompt(
                """
                Build a report dataset for this request using the Superset MCP reporting tools.
                The Superset MCP server is configured at http://localhost:5008/mcp through Spring AI MCP.
                Call the available MCP tools before producing the report data.
                Use no more than 8 tool calls.

                Request: ${userInput.content}

                Return structured data that can be used to generate charts.
                Keep rows.values numeric. Do not put commas, currency symbols, or percent signs in rows.values.
                Put display formatting such as %, USD, or count in metric.value or metric.unit.
                Do not invent values. If a requested number is not available from tool responses,
                omit it and add a source note explaining the gap.
                """.trimIndent()
            )

    @Action(description = "Design chart specifications for the report")
    fun designCharts(data: ReportData, ai: Ai): ChartPlan =
        ai
            .withLlm(LlmOptions.withDefaults().withMaxTokens(8192))
            .withId("super-mcp-chart-designer")
            .withPromptContributors(listOf(Personas.JSON_OUTPUT))
            .creating(ChartPlan::class.java)
            .fromPrompt(
                """
                Convert this report dataset into chart specifications and concise findings.

                Dataset:
                $data

                Create 1 to 4 charts when the dataset contains chartable numeric values.
                Use only these chart types: bar, line.
                Each chart must contain at least one series and every point must have a label and numeric value.
                Do not invent values or categories. Prefer readable labels under 24 characters.
                If there is no chartable data, return an empty charts array and explain why in notes.
                """.trimIndent()
            )

    @AchievesGoal(description = "A reporting result with chart specifications")
    @Action(description = "Publish the reporting result")
    fun publishReport(data: ReportData, plan: ChartPlan): PublishedReport =
        PublishedReport(
            title = plan.title.ifBlank { data.title.ifBlank { "Reporting agent output" } },
            request = data.request,
            executiveSummary = plan.executiveSummary.ifBlank { data.sourceSummary },
            metrics = data.metrics,
            findings = plan.findings,
            charts = plan.charts,
            sourceNotes = data.sourceNotes + plan.notes,
        )
}
