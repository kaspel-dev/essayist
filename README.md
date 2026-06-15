# Kaspel Essayist

Kaspel Essayist is a teaching application for building typed, tool-using agents on the JVM with Spring Boot, Kotlin, Spring AI, HTMX, Embabel DICE, and the Embabel Agent Framework.

The project deliberately contains two related agents:

- `EssayWriterAgent` demonstrates a content-production workflow: build a DICE context capsule, research, draft, review, summarize, add metadata, and publish a Markdown artifact.
- `ReportingAgent` demonstrates a data-analysis workflow: query Superset through MCP, normalize the response into typed report data, design chart specifications, and render a report UI.

Use this repository as a textbook example for explaining how agentic software differs from a single prompt call. The code shows how to model agent work as explicit goals, typed intermediate states, DICE-style context engineering, tool groups, local tools, MCP tools, model roles, and observable process events.

## Learning Goals

After reading and running this project, an engineer should understand how to:

1. Define an Embabel agent with `@Agent`.
2. Split a task into typed `@Action` methods.
3. Mark the terminal business result with `@AchievesGoal`.
4. Let Embabel plan from available input types to a goal type.
5. Use Spring AI-backed LLM calls through Embabel's `Ai` API.
6. Bind model output directly into Kotlin data classes.
7. Attach local tools with `@LlmTool` and `withToolObject(...)`.
8. Expose reusable tool groups through `SelfToolGroup`.
9. Register remote MCP tools as an Embabel `ToolGroup`.
10. Stream planning, action, LLM, and tool events into a browser UI.
11. Use Embabel DICE content hashing to anchor context capsules with repeatable provenance.
12. Use a context-engineering preflight step to make later LLM actions more grounded and observable.

## Project Scope

This is not only an essay generator. It is a compact agent platform sample with four surfaces:

- `/` runs the essay-writing agent with a DICE context-engineering preflight step.
- `/reports` runs the Superset-backed reporting agent.
- `/tools` shows the active tool registry in a readable UI.
- `/tools.json` exposes the same tool registry as JSON for debugging.

The application is intentionally small enough to read in one sitting, but broad enough to demonstrate the core production concerns:

- typed domain boundaries
- multi-step workflows
- tool calling
- MCP integration
- model selection
- browser streaming
- run lifecycle management
- explainability events
- configuration through Spring profiles

## Quick Start

```bash
export ANTHROPIC_API_KEY=your-anthropic-key
export OPENAI_API_KEY=your-openai-key
./gradlew bootRun
```

Open `http://localhost:7001`.

The browser forms default to **Demo mode**. Demo mode returns deterministic essay and report outputs in a few seconds, while still showing the same run shell, goal checklist, chart rendering, and progress stream used by live runs.

Clear the **Demo mode** checkbox to run the real Embabel agent path. The live essay flow uses the in-process no-key web tools for readable page fetching and Wikipedia search.

## Demo Mode vs Live Mode

Use demo mode for presentations, workshops, and internal walkthroughs. It avoids long LLM/MCP calls and makes the UI predictable.

Use live mode when you want to exercise the actual agent graph:

- essay live mode calls Claude for the draft only; DICE context, research brief, review, TLDR, and front matter are local deterministic steps
- report live mode calls Claude and the configured Superset MCP tool group
- live mode can take significantly longer because model retries and remote tools are involved
- report live mode requires the `mcp-super` profile and a reachable Superset MCP server

## Run With Superset MCP

The reporting flow expects a Superset MCP server at `http://localhost:5008/mcp`.

```bash
SPRING_PROFILES_ACTIVE=mcp-super ./gradlew bootRun
```

Then open `http://localhost:7001/reports`.

The `/tools` page should show the `super-mcp-reporting` group with Superset MCP tools such as `list_dashboards`, `list_datasets`, `execute_sql`, `generate_chart`, and `generate_explore_link`.

## Other MCP Profiles

The project includes additional Spring profiles for web-tool experiments:

```bash
# No-key NPX MCP servers for fetch and Wikipedia
SPRING_PROFILES_ACTIVE=mcp-npx ./gradlew bootRun

# Docker Desktop MCP Toolkit gateway
SPRING_PROFILES_ACTIVE=mcp-docker-desktop ./gradlew bootRun
```

The key design point is that the essay agent asks for the `web` tool group, not for a specific implementation. The same action can use in-process tools or MCP-provided tools depending on runtime configuration.

## DICE Context Engineering

The essay workflow starts by building an `EssayContextCapsule` with Embabel DICE's `ContentHasher` and `Sha256ContentHasher`. This is the product's context-engineering preflight step: before the agent researches or writes anything, the raw user topic is normalized, hashed, and turned into a small typed context model.

That capsule gives downstream actions a shared domain context:

- a stable `contextId` derived from the normalized topic hash
- source deduplication for repeat topic requests
- atomic propositions with confidence, importance, and decay scores
- entity mentions, writing constraints, and retrieval questions

Live and demo runs create the capsule deterministically so DICE preflight cannot trigger model retries before research starts. The live essay showcase keeps only the draft as an LLM call. Research brief assembly, review, TLDR, and front matter are local deterministic actions so the run remains reliable in workshops and demos.

### How The Integration Works

`DiceContextService` owns the integration. It injects DICE's `ContentHasher`, backed by `Sha256ContentHasher`, and uses it to create a stable hash for the normalized essay topic. That hash becomes both provenance and a repeat-detection key.

The `EssayWriterAgent` has a pure `buildContextCapsule(UserInput): EssayContextCapsule` action. Embabel can plan over this object like any other typed intermediate state, but no model call is needed to create it. The generated capsule is then passed into:

- `researchTopic`, where retrieval questions become a local research brief
- `writeDraft`, where propositions and constraints shape the only LLM-generated essay draft
- `reviewDraft`, where local checks confirm basic draft quality and DICE alignment

The browser stream also renders the capsule as a first-class artifact. This makes the context layer visible instead of hiding it inside a prompt.

### Why It Helps

DICE makes the workflow less fragile than a single prompt because the context is explicit, typed, and reusable. The same topic always produces the same source hash, so repeated requests can be recognized and audited. Atomic propositions give the writer and local checks a compact checklist of what the essay should preserve. Importance and decay scores distinguish durable domain intent from facts that may need current research. Entity mentions and retrieval questions turn a loose topic into a more precise research plan.

For a product, this is useful because the agent can explain what context it used before it starts generating content. It also gives future extensions a clean place to add persistence, proposition revision, graph projection, or cross-run memory without rewriting the essay actions.

### Current Scope

This project uses the lightweight DICE pieces that are safe for the local demo:

- `ContentHasher`
- `Sha256ContentHasher`
- deterministic context-capsule assembly
- typed propagation of the capsule through the Embabel action graph
- one bounded draft-generation LLM call for the essay body

It does not yet persist propositions to a DICE repository or project them into graph, Prolog, or memory backends. Those are natural next steps once the product needs durable cross-run knowledge.

## Build And Test

```bash
./gradlew test
./gradlew clean build
```

The project uses Gradle Kotlin DSL with Spring Boot, Kotlin JVM, Kotlin Spring, and Spring dependency-management plugins.

## Configuration

Configuration lives in `src/main/resources/application.yaml` and the profile-specific files under `src/main/resources/`.

| Property | Default | Purpose |
|---|---:|---|
| `server.port` | `7001` | Browser and API port |
| `ANTHROPIC_API_KEY` | - | Anthropic key used by Claude models |
| `OPENAI_API_KEY` | - | OpenAI key used by configured OpenAI models and embeddings |
| `essayist.output-dir` | `essays` | Directory for published Markdown essays |
| `essayist.number-of-keywords` | `5` | Maximum generated keywords for front matter |
| `embabel.models.default-llm` | `claude-sonnet-4-6` | Default model for most agent actions |

Anthropic is wired in both the Spring AI and Embabel namespaces:

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}

embabel:
  agent:
    platform:
      models:
        anthropic:
          api-key: ${ANTHROPIC_API_KEY:}
```

Do not commit literal API keys.

## Embabel Mental Model

Embabel models an agent as a graph of actions over typed data.

A user starts with an input object, such as `UserInput`. The application asks Embabel for a goal type, such as `PublishedEssay` or `PublishedReport`. Embabel inspects the available `@Action` methods, their input types, and their output types. It then plans an execution path from what it has to what it needs.

In this project, that means you do not manually call each step from a controller. Instead, the service asks the platform for a goal:

```kotlin
val essay = AgentInvocation
    .builder(agentPlatform)
    .options(processOptions)
    .build(PublishedEssay::class.java)
    .invoke(UserInput(run.topic))
```

Embabel handles the action path. The application handles HTTP, streaming, rendering, persistence, and operational guardrails.

## Embabel Concepts Showcased

### `@Agent`

An `@Agent` class groups actions that belong to one agent capability.

Examples:

- `EssayWriterAgent` writes and publishes essays.
- `ReportingAgent` builds chart-ready reports from Superset MCP data.

### `@Action`

An `@Action` method is a typed step in the agent graph.

The input parameters define what the action requires. The return type defines what the action produces. This is the core design move in Embabel: workflows are assembled from typed capabilities, not from hard-coded controller logic.

Example from `EssayWriterAgent`:

```kotlin
@Action(description = "Write a first draft of the essay")
fun writeDraft(research: ResearchedTopic, ai: Ai): DraftEssay
```

This says: if Embabel has a `ResearchedTopic`, it can produce a `DraftEssay`.

### `@AchievesGoal`

`@AchievesGoal` marks an action result as satisfying a business goal.

Examples:

- `PublishedEssay` is the terminal result for the essay workflow.
- `PublishedReport` is the terminal result for the reporting workflow.

### Typed Intermediate State

The project avoids passing raw strings between every step. It uses domain objects:

- `ResearchedTopic`
- `EssayContextCapsule`
- `DraftEssay`
- `ReviewedEssay`
- `FinalEssay`
- `FrontMatter`
- `PublishedEssay`
- `ReportData`
- `ChartPlan`
- `PublishedReport`

This makes the workflow easier to test, explain, and extend. It also gives the LLM a clear schema when Embabel asks it to create an object.

### `Ai`

Embabel injects `Ai` into actions that need model work. The action can then choose:

- which LLM to use
- which tools are available
- what prompt contributors apply
- what output type should be created
- what interaction ID should be used for logging and observability

Example:

```kotlin
ai
    .withLlm(LlmOptions.withDefaults().withMaxTokens(4096))
    .withId("essay-draft-writer")
    .creating(DraftEssay::class.java)
    .fromPrompt(...)
```

### Prompt Contributors

The project uses `Personas` to keep reusable prompt fragments out of action methods. The simplified essay path uses writer and JSON-output instructions.

This is a useful pattern for organization-wide standards: tone, output constraints, citation rules, safety rules, and formatting rules can live in reusable contributors instead of being copied across prompts.

### Tool Groups

Tool groups let actions depend on capabilities rather than concrete implementations.

The reporting agent uses a Superset MCP tool group. The app also includes local web tools and MCP profiles for experiments, but the simplified essay showcase avoids remote tool calls so it remains reliable for demos.

### Local `@LlmTool` Tools

The project contains local deterministic tools:

- `EssayQualityTool` builds a review checklist.
- `ReadingStatsTool` calculates reading statistics.
- `WebTools` exposes `fetch_readable` and `search_wikipedia`.

These are ordinary Kotlin/Spring components made available to the model in controlled places.

### MCP Tools

`ReportingToolConfiguration` adapts remote Superset MCP tools into an Embabel `ToolGroup`:

```kotlin
McpToolGroup(
    description = ToolGroupDescription(
        description = "Reporting tools exposed by the Superset MCP server.",
        role = ReportingToolGroups.SUPER_MCP_REPORTING,
    ),
    provider = "Superset MCP",
    clients = mcpSyncClients,
    filter = { true },
)
```

The reporting agent can then call:

```kotlin
withToolGroup(ReportingToolGroups.SUPER_MCP_REPORTING)
```

The agent code does not need to know how Superset is connected. Spring AI owns the MCP client configuration, while Embabel owns the agent-level tool group abstraction.

### Process Events And Explainability

`EssayRunService` and `ReportRunService` attach `AgenticEventListener` instances through `ProcessOptions`.

The listeners convert framework events into UI fragments:

- plan formulated
- action started
- action completed
- tool loop started
- tool requested
- tool returned
- LLM response received
- goal achieved
- process failed

This is the project's main explainability pattern. The UI is not guessing what happened; it is rendering Embabel and Spring AI process events.

## Essay Agent Walkthrough

`EssayWriterAgent` is the simplest teaching path.

1. `buildContextCapsule(UserInput): EssayContextCapsule`

   Uses DICE hashing and local heuristics to create the shared context capsule. This step is deterministic, fast, and visible in the UI.

2. `researchTopic(EssayContextCapsule): ResearchedTopic`

   Converts the capsule's propositions, retrieval questions, and constraints into a local research brief. This keeps the showcase fast and removes external tool latency.

3. `writeDraft(ResearchedTopic, EssayContextCapsule, Ai): DraftEssay`

   Produces Markdown content from the research brief and DICE capsule. This is the only LLM call in the simplified live essay path.

4. `reviewDraft(DraftEssay, EssayContextCapsule): ReviewedEssay`

   Uses the local `EssayQualityTool` and a DICE-alignment check. No second model call is made.

5. `addTldr(ReviewedEssay, EssayContextCapsule): FinalEssay`

   Adds a concise deterministic summary from the most important DICE proposition.

6. `addFrontMatter(FinalEssay, EssayContextCapsule): PublishedEssay`

   Uses `ReadingStatsTool`, generates deterministic YAML front matter, writes the Markdown file to disk, and returns the goal object.

## Reporting Agent Walkthrough

`ReportingAgent` is the data-tool example.

1. `queryReportData(UserInput, Ai): ReportData`

   Calls Superset MCP tools before creating structured report data. The prompt asks the model not to invent values and to record source gaps in `sourceNotes`.

2. `designCharts(ReportData, Ai): ChartPlan`

   Converts data rows and metrics into UI-renderable chart specifications.

3. `publishReport(ReportData, ChartPlan): PublishedReport`

   Combines source data and chart planning into the final goal object.

This agent is useful for teaching MCP because the model sees a large tool surface, but the application still requires a typed result before the UI renders anything.

## HTTP And UI Flow

The browser UI is intentionally simple:

- static HTML pages live in `src/main/resources/static`
- HTMX posts form submissions to Spring MVC controllers
- controllers create run shells as HTML
- browser `EventSource` connections subscribe to progress streams
- services invoke Embabel in background executor tasks
- listeners stream HTML fragments back to the browser

This keeps the teaching focus on the agent lifecycle. There is no frontend build step and no client-side framework hiding the flow.

## Tool Registry

Open `http://localhost:7001/tools` to inspect active tools.

The registry page answers practical debugging questions:

- Which tool groups are registered?
- Which provider owns each group?
- How many tools were loaded?
- Is the Superset MCP profile active?
- Which endpoint is configured?

Use `http://localhost:7001/tools.json` when you need the same data for scripts or tests.

## Code Map

| File | Purpose |
|---|---|
| `EssayWriterAgent.kt` | Main essay agent and Embabel action graph |
| `DiceContext.kt` | DICE hashing, context-capsule assembly, and capsule domain objects |
| `ReportingAgent.kt` | Superset-backed report agent |
| `Essay.kt` | Essay workflow domain objects |
| `Reporting.kt` | Reporting workflow domain objects |
| `WebTools.kt` | In-process web `SelfToolGroup` |
| `EssayQualityTool.kt` | Local deterministic review tool |
| `ReadingStatsTool.kt` | Local deterministic reading-time tool |
| `ReportingToolConfiguration.kt` | Superset MCP tool group registration |
| `EssayRunService.kt` | Essay run lifecycle, Embabel invocation, event streaming |
| `ReportRunService.kt` | Report run lifecycle and Superset MCP readiness checks |
| `ToolingController.kt` | Runtime tool registry UI and JSON endpoint |
| `HtmlFragments.kt` | Server-rendered HTML fragments for run streams and outputs |
| `application.yaml` | Default model, key, and app configuration |
| `application-mcp-super.yaml` | Superset MCP profile |
| `application-mcp-npx.yaml` | NPX MCP profile |
| `application-mcp-docker-desktop.yaml` | Docker Desktop MCP profile |

## Design Lessons

### Prefer Typed Workflow State

Typed data classes make agent behavior easier to reason about than a chain of plain strings. They also give the planner and the model crisp contracts.

### Separate Agent Logic From Transport

The agents do not know about HTTP, HTMX, SSE, or HTML. Controllers and services handle transport. Agents handle task decomposition and business outputs.

### Ask For Capabilities, Not Implementations

`CoreToolGroups.WEB` is a capability. It can be backed by local tools or MCP tools. This makes the agent portable across environments.

### Keep Deterministic Work In Tools

Reading-time calculation and review checklist construction are implemented as local tools. This makes the model responsible for judgment and synthesis, not basic deterministic computation.

### Make Observability A First-Class Feature

The UI exposes planning, actions, model calls, and tool calls. This is essential for teaching and for production debugging.

## Suggested Teaching Path

1. Start with the domain objects in `Essay.kt`.
2. Read `EssayWriterAgent.kt` from top to bottom.
3. Trace how `PublishedEssay` is requested in `EssayRunService`.
4. Open `/tools` and identify the default `web` tools.
5. Run an essay and watch the event stream update.
6. Enable `mcp-super` and inspect `/tools` again.
7. Read `ReportingAgent.kt` and compare local tools with MCP tools.
8. Add a small new action or local tool as an exercise.

## Extension Exercises

- Add a citation-verification action between `researchTopic` and `writeDraft`.
- Add a plagiarism or style policy tool to `reviewDraft`.
- Add an optional second-pass model review and compare outputs.
- Add a report action that validates generated chart data before publishing.
- Restrict the Superset MCP tool group with a `filter` instead of exposing all tools.
- Persist run history so completed runs can be revisited after page refresh.
- Add tests for the tool registry and readiness messages.

## Production Notes

This repository is a teaching example, not a complete production platform.

Before using the same patterns in production, consider:

- authentication and authorization
- tenant isolation
- rate limits and timeouts
- secret management
- durable run state
- structured audit logs
- tool allowlists
- prompt/version management
- human approval for write operations
- stricter source attribution for research outputs

## References

- Embabel Agent Framework documentation
- Spring AI MCP reference: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html
