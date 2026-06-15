package kaspel.essayist

import com.embabel.agent.api.event.ActionExecutionResultEvent
import com.embabel.agent.api.event.ActionExecutionStartEvent
import com.embabel.agent.api.event.AgentProcessCompletedEvent
import com.embabel.agent.api.event.AgentProcessEvent
import com.embabel.agent.api.event.AgentProcessFailedEvent
import com.embabel.agent.api.event.AgentProcessPlanFormulatedEvent
import com.embabel.agent.api.event.AgenticEventListener
import com.embabel.agent.api.event.GoalAchievedEvent
import com.embabel.agent.api.event.LlmInvocationEvent
import com.embabel.agent.api.event.ToolCallRequestEvent
import com.embabel.agent.api.event.ToolCallResponseEvent
import com.embabel.agent.api.event.ToolLoopCompletedEvent
import com.embabel.agent.api.event.ToolLoopStartEvent
import com.embabel.agent.api.invocation.AgentInvocation
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.ProcessOptions
import com.embabel.agent.core.ToolGroup
import com.embabel.agent.core.Verbosity
import com.embabel.agent.domain.io.UserInput
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

data class ReportRun(
    val id: String,
    val prompt: String,
    val demoMode: Boolean = false,
    val createdAt: Instant = Instant.now(),
) {
    val started = AtomicBoolean(false)
}

@Service
class ReportRunService(
    private val agentPlatform: AgentPlatform,
    private val essayTaskExecutor: ExecutorService,
    private val toolGroups: List<ToolGroup>,
) {

    private val runs = ConcurrentHashMap<String, ReportRun>()

    fun createRun(prompt: String, demoMode: Boolean): ReportRun {
        val run = ReportRun(id = UUID.randomUUID().toString(), prompt = prompt, demoMode = demoMode)
        runs[run.id] = run
        return run
    }

    fun readinessError(): String? {
        val reportingGroup = toolGroups.firstOrNull {
            it.metadata.role == ReportingToolGroups.SUPER_MCP_REPORTING
        } ?: return "Start this app with the mcp-super profile so Spring AI connects to http://localhost:5008/mcp."

        return if (reportingGroup.tools.isEmpty()) {
            "The Superset MCP reporting tool group is registered, but no tools were loaded. Check that http://localhost:5008/mcp is running and reachable."
        } else {
            null
        }
    }

    fun streamRun(id: String): SseEmitter {
        val run = runs[id] ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown report run: $id")
        val emitter = SseEmitter(0L)
        val stream = HtmlStream(emitter)

        if (!run.started.compareAndSet(false, true)) {
            stream.fragment(
                HtmlFragments.inlineError(
                    title = "Unable to run the reporter",
                    message = "This run has already started. Start a new run for another report.",
                )
            )
            stream.done()
            return emitter
        }

        essayTaskExecutor.submit {
            executeRun(run, stream)
        }
        return emitter
    }

    private fun executeRun(run: ReportRun, stream: HtmlStream) {
        try {
            if (run.demoMode) {
                executeDemoRun(run, stream)
                return
            }

            readinessError()?.let { message ->
                stream.fragment(
                    HtmlFragments.inlineError(
                        title = "Unable to run the reporter",
                        message = message,
                    )
                )
                stream.done()
                return
            }

            stream.fragment(
                HtmlFragments.status(
                    title = "Starting",
                    detail = "Preparing the reporting agent for \"${run.prompt}\".",
                    state = "active",
                )
            )

            val processOptions = ProcessOptions(
                verbosity = Verbosity(showPlanning = true),
                listeners = listOf(ReportProgressListener(run, stream)),
            )

            val report = AgentInvocation
                .builder(agentPlatform)
                .options(processOptions)
                .build(PublishedReport::class.java)
                .invoke(UserInput(run.prompt))

            stream.fragment(HtmlFragments.publishedReport(report))
            stream.done()
        } catch (e: Exception) {
            log.error("Report generation failed for run {}", run.id, e)
            stream.fragment(
                HtmlFragments.inlineError(
                    title = "Unable to run the reporter",
                    message = e.message
                        ?: "The reporting agent failed before returning a response. Make sure Superset MCP is running at http://localhost:5008/mcp and start this app with the mcp-super profile.",
                )
            )
            stream.done()
        } finally {
            runs.remove(run.id)
        }
    }

    private fun executeDemoRun(run: ReportRun, stream: HtmlStream) {
        stream.fragment(
            HtmlFragments.status(
                title = "Demo mode",
                detail = "Rendering a deterministic report for \"${run.prompt}\".",
                state = "active",
            ) +
                HtmlFragments.reportExplanation(
                    run = run,
                    framework = "Embabel",
                    title = "Demo plan selected",
                    detail = "The UI shows the reporting goal flow without waiting for live LLM or Superset MCP calls.",
                )
        )
        pauseForDemo()

        val steps = listOf(
            "queryReportData" to "Loaded deterministic Superset-style report data.",
            "designCharts" to "Prepared chart-ready series and findings.",
            "publishReport" to "Rendered the final report object.",
        )

        steps.forEach { (actionName, detail) ->
            stream.fragment(
                HtmlFragments.reportActionGoalUpdate(
                    run = run,
                    actionName = actionName,
                    state = "complete",
                    detail = detail,
                ) +
                    HtmlFragments.status(
                        title = "$actionName complete",
                        detail = detail,
                        state = "complete",
                    )
            )
            pauseForDemo()
        }

        stream.fragment(
            HtmlFragments.status(
                title = "Agent completed",
                detail = "Rendering the demo report output.",
                state = "complete",
            ) +
                HtmlFragments.publishedReport(DemoArtifacts.report(run.prompt))
        )
        stream.done()
    }

    companion object {
        private val log = LoggerFactory.getLogger(ReportRunService::class.java)
    }
}

private class ReportProgressListener(
    private val run: ReportRun,
    private val stream: HtmlStream,
) : AgenticEventListener {

    override fun onProcessEvent(event: AgentProcessEvent) {
        when (event) {
            is AgentProcessPlanFormulatedEvent -> stream.fragment(
                HtmlFragments.status(
                    title = "Plan formulated",
                    detail = "Embabel selected the action path for the PublishedReport goal.",
                    state = "complete",
                ) +
                    HtmlFragments.reportExplanation(
                        run = run,
                        framework = "Embabel",
                        title = "Planner selected the workflow",
                        detail = "The planner uses action input/output types to move from UserInput to PublishedReport.",
                    )
            )

            is GoalAchievedEvent -> stream.fragment(
                HtmlFragments.status(
                    title = "Goal achieved",
                    detail = event.goal.toString().compactForUi(),
                    state = "complete",
                ) +
                    HtmlFragments.reportExplanation(
                        run = run,
                        framework = "Embabel",
                        title = "Agent goal satisfied",
                        detail = "The process produced chart-ready report data for the UI.",
                    )
            )

            is ActionExecutionStartEvent -> {
                val actionName = event.action.shortName()
                stream.fragment(
                    HtmlFragments.reportActionGoalUpdate(
                        run = run,
                        actionName = actionName,
                        state = "active",
                        detail = event.action.description,
                    ) +
                        HtmlFragments.status(
                            title = actionName,
                            detail = event.action.description,
                            state = "active",
                        ) +
                        HtmlFragments.reportExplanation(
                            run = run,
                            framework = "Embabel",
                            title = "Action started",
                            detail = "$actionName is an @Action method in ReportingAgent.",
                        )
                )
            }

            is ActionExecutionResultEvent -> {
                val actionName = event.action.shortName()
                stream.fragment(
                    HtmlFragments.reportActionGoalUpdate(
                        run = run,
                        actionName = actionName,
                        state = "complete",
                        detail = "Finished in ${event.runningTime.readableDuration()}.",
                    ) +
                        HtmlFragments.status(
                            title = "$actionName complete",
                            detail = "Finished in ${event.runningTime.readableDuration()}.",
                            state = "complete",
                        )
                )
            }

            is ToolLoopStartEvent -> stream.fragment(
                HtmlFragments.reportExplanation(
                    run = run,
                    framework = "Spring AI",
                    title = "Tool loop opened",
                    detail = "The LLM can call ${event.toolNames.size} tool(s) while creating ${event.outputClass.simpleName}.",
                )
            )

            is ToolLoopCompletedEvent -> stream.fragment(
                HtmlFragments.reportExplanation(
                    run = run,
                    framework = "Spring AI",
                    title = "Tool loop completed",
                    detail = "Completed ${event.totalIterations} iteration(s) in ${event.runningTime.readableDuration()}.",
                )
            )

            is ToolCallRequestEvent -> stream.fragment(
                HtmlFragments.status(
                    title = "Using ${event.tool}",
                    detail = event.action?.shortName()?.let { "Called from $it." } ?: "Calling a Superset MCP tool.",
                    state = "active",
                ) +
                    HtmlFragments.reportExplanation(
                        run = run,
                        framework = "Superset MCP",
                        title = "Tool requested by the model",
                        detail = event.toolGroupMetadata?.role
                            ?.let { "The model requested ${event.tool} from the $it tool group." }
                            ?: "The model requested ${event.tool}.",
                    )
            )

            is ToolCallResponseEvent -> stream.fragment(
                HtmlFragments.status(
                    title = "${event.request.tool} returned",
                    detail = "Tool call finished in ${event.runningTime.readableDuration()}.",
                    state = "complete",
                ) +
                    HtmlFragments.reportExplanation(
                        run = run,
                        framework = "Superset MCP",
                        title = "Tool result returned",
                        detail = event.request.action?.shortName()
                            ?.let { "The result is available to the LLM loop inside $it." }
                            ?: "The result is available to the LLM loop.",
                    )
            )

            is LlmInvocationEvent -> stream.fragment(
                HtmlFragments.status(
                    title = "LLM response received",
                    detail = "${event.invocation.llmMetadata.name} used ${event.invocation.usage.promptTokens ?: 0} prompt tokens.",
                    state = "complete",
                ) +
                    HtmlFragments.reportExplanation(
                        run = run,
                        framework = "Spring AI",
                        title = "Model invocation completed",
                        detail = "${event.invocation.llmMetadata.name} handled interaction ${event.interactionId}.",
                    )
            )

            is AgentProcessCompletedEvent -> stream.fragment(
                HtmlFragments.status(
                    title = "Agent completed",
                    detail = "Rendering the reporting dashboard.",
                    state = "complete",
                )
            )

            is AgentProcessFailedEvent -> stream.fragment(
                HtmlFragments.status(
                    title = "Agent failed",
                    detail = "The process ended before a report was produced.",
                    state = "error",
                )
            )
        }
    }
}
