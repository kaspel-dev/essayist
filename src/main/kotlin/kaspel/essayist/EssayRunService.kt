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
import com.embabel.agent.api.event.ObjectAddedEvent
import com.embabel.agent.api.event.ToolLoopCompletedEvent
import com.embabel.agent.api.event.ToolLoopStartEvent
import com.embabel.agent.api.event.ToolCallRequestEvent
import com.embabel.agent.api.event.ToolCallResponseEvent
import com.embabel.agent.api.invocation.AgentInvocation
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.ProcessOptions
import com.embabel.agent.core.Verbosity
import com.embabel.agent.domain.io.UserInput
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class EssayRun(
    val id: String,
    val topic: String,
    val demoMode: Boolean = false,
    val createdAt: Instant = Instant.now(),
) {
    val started = AtomicBoolean(false)
}

@Configuration
class EssayRunConfiguration {

    @Bean(destroyMethod = "shutdown")
    fun essayTaskExecutor(): ExecutorService = Executors.newCachedThreadPool()
}

@Service
class EssayRunService(
    private val agentPlatform: AgentPlatform,
    private val essayTaskExecutor: ExecutorService,
    private val diceContextService: DiceContextService,
    private val dokimosEvaluationService: DokimosEvaluationService,
) {

    private val runs = ConcurrentHashMap<String, EssayRun>()

    fun createRun(topic: String, demoMode: Boolean): EssayRun {
        val run = EssayRun(id = UUID.randomUUID().toString(), topic = topic, demoMode = demoMode)
        runs[run.id] = run
        return run
    }

    fun streamRun(id: String): SseEmitter {
        val run = runs[id] ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown essay run: $id")
        val emitter = SseEmitter(0L)
        val stream = HtmlStream(emitter)

        if (!run.started.compareAndSet(false, true)) {
            stream.fragment(HtmlFragments.inlineError("This run has already started. Start a new run for another topic."))
            stream.done()
            return emitter
        }

        essayTaskExecutor.submit {
            executeRun(run, stream)
        }
        return emitter
    }

    private fun executeRun(run: EssayRun, stream: HtmlStream) {
        try {
            if (run.demoMode) {
                executeDemoRun(run, stream)
                return
            }

            stream.fragment(
                HtmlFragments.status(
                    title = "Starting",
                    detail = "Preparing the essay writer for \"${run.topic}\".",
                    state = "active",
                )
            )

            val processOptions = ProcessOptions(
                verbosity = Verbosity(showPlanning = true),
                listeners = listOf(HtmlProgressListener(run, stream)),
            )

            val essay = AgentInvocation
                .builder(agentPlatform)
                .options(processOptions)
                .build(PublishedEssay::class.java)
                .invoke(UserInput(run.topic))

            stream.fragment(HtmlFragments.publishedEssay(essay))
            stream.done()
        } catch (e: Exception) {
            log.error("Essay generation failed for run {}", run.id, e)
            stream.fragment(
                HtmlFragments.inlineError(
                    e.message ?: "The essay writer failed before returning a response."
                )
            )
            stream.done()
        } finally {
            runs.remove(run.id)
        }
    }

    private fun executeDemoRun(run: EssayRun, stream: HtmlStream) {
        stream.fragment(
            HtmlFragments.status(
                title = "Demo mode",
                detail = "Rendering a deterministic essay run for \"${run.topic}\".",
                state = "active",
            ) +
                HtmlFragments.explanation(
                    run = run,
                    framework = "Embabel",
                    title = "Demo plan selected",
                    detail = "The UI shows the same goal flow without waiting for live LLM or tool calls.",
                )
        )
        pauseForDemo()

        val context = diceContextService.demoCapsule(run.topic)
        stream.fragment(
            HtmlFragments.actionGoalUpdate(
                run = run,
                actionName = "buildContextCapsule",
                state = "complete",
                detail = "Created a DICE context capsule with ${context.propositions.size} proposition(s).",
            ) +
                HtmlFragments.status(
                    title = "DICE context ready",
                    detail = "Source hash ${context.sourceHash.take(12)} anchors the demo context capsule.",
                    state = "complete",
                ) +
                HtmlFragments.explanation(
                    run = run,
                    framework = "DICE",
                    title = "Context capsule created",
                    detail = "The workflow now carries propositions, entity mentions, constraints, and retrieval questions into later actions.",
                ) +
                HtmlFragments.contextCapsule(context)
        )
        pauseForDemo()

        val demoEssay = DemoArtifacts.essay(run.topic)
        val demoDokimosReport = dokimosEvaluationService.evaluateDraft(
            draft = DraftEssay(title = demoEssay.title, content = demoEssay.content),
            research = ResearchedTopic(topic = context.topic, research = context.promptBlock()),
            context = context,
        )

        val steps = listOf(
            "researchTopic" to "Collected demo research context.",
            "writeDraft" to "Created the demo Markdown draft.",
            "evaluateDraft" to "Evaluated draft relevancy and faithfulness.",
            "reviewDraft" to "Applied the demo review checklist.",
            "addTldr" to "Added a concise summary.",
            "addFrontMatter" to "Prepared metadata and the published artifact.",
        )

        steps.forEach { (actionName, detail) ->
            var extra = ""
            if (actionName == "writeDraft") {
                extra = HtmlFragments.explanation(
                    run = run,
                    framework = "Spring AI",
                    title = "Guardrail applied",
                    detail = "Applying the essay style guardrail before draft generation.",
                )
            }
            if (actionName == "evaluateDraft") {
                extra = HtmlFragments.explanation(
                    run = run,
                    framework = "Spring AI",
                    title = "Evals completed",
                    detail = "Relevancy and faithfulness checks were recorded as typed evaluation results.",
                ) +
                    HtmlFragments.explanation(
                        run = run,
                        framework = "Dokimos",
                        title = "Quality gates completed",
                        detail = "Dokimos evaluated the draft against a dataset of deterministic quality checks.",
                    ) +
                    HtmlFragments.dokimosEvalReport(demoDokimosReport)
            }
            if (actionName == "addFrontMatter") {
                extra = HtmlFragments.explanation(
                    run = run,
                    framework = "Embabel",
                    title = "Artifact published",
                    detail = "The final action adds deterministic metadata and writes the Markdown artifact.",
                )
            }
            stream.fragment(
                HtmlFragments.actionGoalUpdate(
                    run = run,
                    actionName = actionName,
                    state = "complete",
                    detail = detail,
                ) +
                    HtmlFragments.status(
                        title = "$actionName complete",
                        detail = detail,
                        state = "complete",
                    ) + extra
            )
            pauseForDemo()
        }

        stream.fragment(
            HtmlFragments.status(
                title = "Agent completed",
                detail = "Rendering the demo essay output.",
                state = "complete",
            ) +
                HtmlFragments.publishedEssay(demoEssay)
        )
        stream.done()
    }

    companion object {
        private val log = LoggerFactory.getLogger(EssayRunService::class.java)
    }
}

private class HtmlProgressListener(
    private val run: EssayRun,
    private val stream: HtmlStream,
) : AgenticEventListener {

    private val seenLlmInteractions = ConcurrentHashMap.newKeySet<String>()

    override fun onProcessEvent(event: AgentProcessEvent) {
        when (event) {
            is AgentProcessPlanFormulatedEvent -> stream.fragment(
                HtmlFragments.status(
                    title = "Plan formulated",
                    detail = "Embabel selected the action path for the PublishedEssay goal.",
                    state = "complete",
                ) +
                    HtmlFragments.explanation(
                        run = run,
                        framework = "Embabel",
                        title = "Planner selected the workflow",
                        detail = "The planner uses action input/output types to move from UserInput to PublishedEssay.",
                    )
            )

            is GoalAchievedEvent -> stream.fragment(
                HtmlFragments.status(
                    title = "Goal achieved",
                    detail = event.goal.toString().compactForUi(),
                    state = "complete",
                ) +
                    HtmlFragments.explanation(
                        run = run,
                        framework = "Embabel",
                        title = "Agent goal satisfied",
                        detail = "The process produced the goal object and can hand the result back to Spring MVC.",
                    )
            )

            is ActionExecutionStartEvent -> {
                val actionName = event.action.shortName()
                var extra = ""
                if (actionName == "writeDraft") {
                    extra = HtmlFragments.explanation(
                        run = run,
                        framework = "Spring AI",
                        title = "Guardrail applied",
                        detail = "The Embabel prompt runner applies EssayStyleGuardRail to the draft-generation call.",
                    )
                }
                if (actionName == "evaluateDraft") {
                    extra = HtmlFragments.explanation(
                        run = run,
                        framework = "Spring AI",
                        title = "Evals running",
                        detail = "The draft is checked for relevancy and faithfulness before local review.",
                    ) +
                        HtmlFragments.explanation(
                            run = run,
                            framework = "Dokimos",
                            title = "Quality gates running",
                            detail = "The draft is also evaluated with Dokimos dataset examples and regex quality gates.",
                        )
                }
                stream.fragment(
                    HtmlFragments.actionGoalUpdate(
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
                        HtmlFragments.explanation(
                            run = run,
                            framework = "Embabel",
                            title = "Action started",
                            detail = "$actionName is an @Action method in EssayWriterAgent.",
                        ) + extra
                )
            }

            is ActionExecutionResultEvent -> {
                val actionName = event.action.shortName()
                stream.fragment(
                    HtmlFragments.actionGoalUpdate(
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

            is ObjectAddedEvent -> {
                val value = event.value
                if (value is EssayContextCapsule) {
                    stream.fragment(
                        HtmlFragments.explanation(
                            run = run,
                            framework = "DICE",
                            title = "Context capsule bound",
                            detail = "Embabel added the DICE context capsule to the process state for downstream actions.",
                        ) +
                            HtmlFragments.contextCapsule(value)
                    )
                }
                if (value is EvaluatedDraft) {
                    stream.fragment(
                        HtmlFragments.explanation(
                            run = run,
                            framework = "Dokimos",
                            title = "Quality gates completed",
                            detail = "Dokimos scored the draft against a small dataset of deterministic checks.",
                        ) +
                            HtmlFragments.dokimosEvalReport(value.evalReport.dokimos)
                    )
                }
            }

            is ToolLoopStartEvent -> stream.fragment(
                HtmlFragments.explanation(
                    run = run,
                    framework = "Spring AI",
                    title = "Automatic tool-calling loop",
                    detail = "Spring AI ChatClient is managing a loop to call ${event.toolNames.size} tool(s) until the request for ${event.outputClass.simpleName} is satisfied.",
                )
            )

            is ToolLoopCompletedEvent -> stream.fragment(
                HtmlFragments.explanation(
                    run = run,
                    framework = "Spring AI",
                    title = "Tool loop completed",
                    detail = "Completed ${event.totalIterations} iteration(s) in ${event.runningTime.readableDuration()}.",
                )
            )

            is ToolCallRequestEvent -> stream.fragment(
                HtmlFragments.status(
                    title = "Using ${event.tool}",
                    detail = event.action?.shortName()?.let { "Called from $it." } ?: "Calling an AI tool.",
                    state = "active",
                ) +
                    HtmlFragments.explanation(
                        run = run,
                        framework = "Spring AI",
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
                    HtmlFragments.explanation(
                        run = run,
                        framework = "Spring AI",
                        title = "Tool result returned",
                        detail = event.request.action?.shortName()
                            ?.let { "The result is available to the LLM loop inside $it." }
                            ?: "The result is available to the LLM loop.",
                    )
            )

            is LlmInvocationEvent -> {
                if (seenLlmInteractions.add(event.interactionId.toString())) {
                    stream.fragment(
                        HtmlFragments.status(
                            title = "LLM response received",
                            detail = "${event.invocation.llmMetadata.name} used ${event.invocation.usage.promptTokens ?: 0} prompt tokens.",
                            state = "complete",
                        ) +
                            HtmlFragments.explanation(
                                run = run,
                                framework = "Spring AI",
                                title = "Model invocation completed",
                                detail = "${event.invocation.llmMetadata.name} handled interaction ${event.interactionId}.",
                            )
                    )
                }
            }

            is AgentProcessCompletedEvent -> stream.fragment(
                HtmlFragments.status(
                    title = "Agent completed",
                    detail = "Rendering the structured output.",
                    state = "complete",
                )
            )

            is AgentProcessFailedEvent -> stream.fragment(
                HtmlFragments.status(
                    title = "Agent failed",
                    detail = "The process ended before an essay was produced.",
                    state = "error",
                )
            )
        }
    }
}
