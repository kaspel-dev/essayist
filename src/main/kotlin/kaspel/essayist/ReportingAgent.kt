package kaspel.essayist

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.Ai
import com.embabel.agent.domain.io.UserInput
import com.embabel.common.ai.model.LlmOptions
import org.slf4j.LoggerFactory

@Agent(description = "Build chart-ready business reports using Superset MCP data tools")
class ReportingAgent(
    private val reportChartPlanner: ReportChartPlanner,
) {

    @Action(description = "Query Superset MCP for reporting data")
    fun queryReportData(userInput: UserInput, ai: Ai): ReportData {
        log.info("Automatic tool-calling loop enabled for queryReportData")
        return ai
            .withLlm(LlmOptions.withDefaults().withMaxTokens(2048))
            .withToolGroup(ReportingToolGroups.SUPER_MCP_REPORTING)
            .withId("super-mcp-report-query")
            .withPromptContributors(listOf(Personas.JSON_OUTPUT))
            .creating(ReportData::class.java)
            .fromPrompt(
                """
                Build a compact report dataset for this request using the Superset MCP reporting tools.
                The Superset MCP server is configured at http://localhost:5008/mcp through Spring AI MCP.
                This is an MCP capability showcase, so use the smallest useful tool path:
                - Prefer one execute_sql call when you can answer directly.
                - If you must discover a dataset first, call one list or get metadata tool, then one execute_sql call.
                - Stop after at most 2 MCP tool calls.
                - Do not call chart creation, explore-link, export, or dashboard rendering tools.
                - If you cannot identify the needed dataset after one discovery call, return empty rows and explain the gap in sourceNotes.

                Request: ${userInput.content}

                Return structured data that can be used to generate charts.
                Keep the response compact: at most 4 metrics, 8 rows, and 4 numeric values per row.
                Keep rows.values numeric. Do not put commas, currency symbols, or percent signs in rows.values.
                Put display formatting such as %, USD, or count in metric.value or metric.unit.
                Do not invent values. If a requested number is not available from tool responses,
                omit it and add a source note explaining the gap.
                """.trimIndent()
            )
    }

    @Action(description = "Design chart specifications locally from MCP report data")
    fun designCharts(data: ReportData): ChartPlan =
        reportChartPlanner.plan(data)

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

    companion object {
        private val log = LoggerFactory.getLogger(ReportingAgent::class.java)
    }
}
