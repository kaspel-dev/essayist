package kaspel.essayist

import org.springframework.web.util.HtmlUtils
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object HtmlFragments {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    fun runShell(run: EssayRun): String =
        """
        <article class="run" data-stream-url="/runs/${run.id}/events">
          <header class="run__header">
            <div>
              <p class="eyebrow">Essay run</p>
              <h2>${run.topic.escapeHtml()}</h2>
            </div>
            <time>${timeFormatter.format(run.createdAt).escapeHtml()}</time>
          </header>
          <div class="run__body">
            <aside class="run__sidebar" aria-label="Run progress">
              <section class="goals" aria-labelledby="${run.domId("goals-title")}">
                <h3 id="${run.domId("goals-title")}">Goals</h3>
                <ol class="goal-list">
                  ${PipelineGoal.entries.joinToString("\n") { goalItem(run, it, "queued", it.detail) }}
                </ol>
              </section>
              <section class="explainability" aria-labelledby="${run.domId("explainability-title")}">
                <h3 id="${run.domId("explainability-title")}">Explainability</h3>
                <div class="framework-map">
                  <p><strong>Embabel</strong> plans and executes typed agent actions toward the published-essay goal.</p>
                  <p><strong>DICE</strong> builds a domain-integrated context capsule before research.</p>
                  <p><strong>Spring AI</strong> carries the model and tool calls used inside those actions.</p>
                  <p><strong>Dokimos</strong> runs deterministic eval gates over the draft before review.</p>
                </div>
                <div id="${run.domId("explainability")}" class="explainability__events" data-explain-target>
                  <p class="muted">Waiting for the first framework event.</p>
                </div>
              </section>
            </aside>
            <section class="run__main" aria-label="Run timeline">
              <div class="stream-heading">
                <h3>Live progress</h3>
                <p>Server-sent HTML from the running agent process.</p>
              </div>
              <div class="stream" data-stream-target>
                ${status("Queued", "Opening an HTML stream for the agent run.", "active")}
              </div>
            </section>
          </div>
        </article>
        """.trimIndent()

    fun reportRunShell(run: ReportRun): String =
        """
        <article class="run run--report" data-stream-url="/report-runs/${run.id}/events">
          <header class="run__header">
            <div>
              <p class="eyebrow">Reporting run</p>
              <h2>${run.prompt.escapeHtml()}</h2>
            </div>
            <time>${timeFormatter.format(run.createdAt).escapeHtml()}</time>
          </header>
          <div class="run__body">
            <aside class="run__sidebar" aria-label="Report run progress">
              <section class="goals" aria-labelledby="${run.domId("goals-title")}">
                <h3 id="${run.domId("goals-title")}">Goals</h3>
                <ol class="goal-list">
                  ${ReportPipelineGoal.entries.joinToString("\n") { goalItem(run, it, "queued", it.detail) }}
                </ol>
              </section>
              <section class="explainability" aria-labelledby="${run.domId("explainability-title")}">
                <h3 id="${run.domId("explainability-title")}">Explainability</h3>
                <div class="framework-map">
                  <p><strong>Embabel</strong> plans and executes typed report actions toward the chart report goal.</p>
                  <p><strong>Superset MCP</strong> supplies the data tools from the configured remote MCP server.</p>
                  <p><strong>Spring AI</strong> carries the model and MCP tool calls used inside those actions.</p>
                </div>
                <div id="${run.domId("explainability")}" class="explainability__events" data-explain-target>
                  <p class="muted">Waiting for the first framework event.</p>
                </div>
              </section>
            </aside>
            <section class="run__main" aria-label="Report run timeline">
              <div class="stream-heading">
                <h3>Live progress</h3>
                <p>Server-sent HTML from the reporting agent process.</p>
              </div>
              <div class="stream" data-stream-target>
                ${status("Queued", "Opening an HTML stream for the reporting run.", "active")}
              </div>
            </section>
          </div>
        </article>
        """.trimIndent()

    fun status(title: String, detail: String, state: String): String =
        """
        <section class="stream-item stream-item--${state.escapeHtml()}">
          <span class="stream-item__dot" aria-hidden="true"></span>
          <div>
            <h3>${title.escapeHtml()}</h3>
            <p>${detail.escapeHtml()}</p>
          </div>
        </section>
        """.trimIndent()

    fun actionGoalUpdate(run: EssayRun, actionName: String, state: String, detail: String): String =
        PipelineGoal.fromAction(actionName)?.let { goal ->
            goalItem(run, goal, state, detail)
        }.orEmpty()

    fun reportActionGoalUpdate(run: ReportRun, actionName: String, state: String, detail: String): String =
        ReportPipelineGoal.fromAction(actionName)?.let { goal ->
            goalItem(run, goal, state, detail)
        }.orEmpty()

    fun explanation(run: EssayRun, framework: String, title: String, detail: String): String =
        """
        <section class="explain-event" data-append-target="#${run.domId("explainability")}">
          <span>${framework.escapeHtml()}</span>
          <div>
            <h4>${title.escapeHtml()}</h4>
            <p>${detail.escapeHtml()}</p>
          </div>
        </section>
        """.trimIndent()

    fun reportExplanation(run: ReportRun, framework: String, title: String, detail: String): String =
        """
        <section class="explain-event" data-append-target="#${run.domId("explainability")}">
          <span>${framework.escapeHtml()}</span>
          <div>
            <h4>${title.escapeHtml()}</h4>
            <p>${detail.escapeHtml()}</p>
          </div>
        </section>
        """.trimIndent()

    fun publishedEssay(essay: PublishedEssay): String =
        renderPublishedEssay(essay).let { rendered ->
            """
            <section class="result">
              <div class="result__summary">
                <p class="eyebrow">Structured HTML output</p>
                <h2>${rendered.title.escapeHtml()}</h2>
                <p>${essay.feedback.escapeHtml()}</p>
              </div>
              <dl class="metadata-grid">
                <div>
                  <dt>Description</dt>
                  <dd>${rendered.description.escapeHtml()}</dd>
                </div>
                <div>
                  <dt>Read time</dt>
                  <dd>${rendered.readTime.escapeHtml()}</dd>
                </div>
                <div>
                  <dt>Tags</dt>
                  <dd>${rendered.tags.toChipList()}</dd>
                </div>
                <div>
                  <dt>Keywords</dt>
                  <dd>${rendered.keywords.toChipList()}</dd>
                </div>
              </dl>
              <div class="result__output">
                <article class="article-preview">
                  ${rendered.bodyHtml}
                </article>
                <details class="markdown-artifact">
                  <summary>Markdown artifact</summary>
                  <pre><code>${essay.content.escapeHtml()}</code></pre>
                </details>
              </div>
            </section>
            """.trimIndent()
        }

    fun contextCapsule(capsule: EssayContextCapsule): String =
        """
        <section class="dice-capsule">
          <div class="dice-capsule__header">
            <div>
              <p class="eyebrow">DICE context capsule</p>
              <h3>${capsule.topic.escapeHtml()}</h3>
            </div>
            <span>${if (capsule.duplicateSource) "Duplicate source" else "New source"}</span>
          </div>
          <dl class="dice-capsule__meta">
            <div>
              <dt>Context id</dt>
              <dd><code>${capsule.contextId.escapeHtml()}</code></dd>
            </div>
            <div>
              <dt>Source hash</dt>
              <dd><code>${capsule.sourceHash.take(16).escapeHtml()}</code></dd>
            </div>
          </dl>
          <div class="dice-capsule__grid">
            <section>
              <h4>Propositions</h4>
              <ol class="proposition-list">
                ${capsule.propositions.toPropositionItems()}
              </ol>
            </section>
            <section>
              <h4>Entity mentions</h4>
              <ul class="compact-list">
                ${capsule.entityMentions.toEntityMentionItems()}
              </ul>
            </section>
            <section>
              <h4>Constraints</h4>
              <ul class="compact-list">
                ${capsule.constraints.toPlainListItems()}
              </ul>
            </section>
            <section>
              <h4>Retrieval questions</h4>
              <ul class="compact-list">
                ${capsule.retrievalQuestions.toPlainListItems()}
              </ul>
            </section>
          </div>
        </section>
        """.trimIndent()

    fun dokimosEvalReport(report: DokimosEvalReport): String =
        """
        <section class="dokimos-report">
          <div class="dokimos-report__header">
            <div>
              <p class="eyebrow">Dokimos evaluation</p>
              <h3>${report.datasetName.escapeHtml()}</h3>
            </div>
            <span class="status-pill status-pill--${if (report.passed) "ready" else "warning"}">${report.statusLabel().escapeHtml()}</span>
          </div>
          <dl class="dokimos-report__metrics">
            <div>
              <dt>Pass rate</dt>
              <dd>${report.passRateLabel().escapeHtml()}</dd>
            </div>
            <div>
              <dt>Average score</dt>
              <dd>${report.averageScoreLabel().escapeHtml()}</dd>
            </div>
            <div>
              <dt>Checks</dt>
              <dd>${report.checks.size}</dd>
            </div>
          </dl>
          <ol class="dokimos-check-list">
            ${report.checks.toDokimosCheckItems()}
          </ol>
        </section>
        """.trimIndent()

    fun publishedReport(report: PublishedReport): String =
        """
        <section class="result report-result">
          <div class="result__summary">
            <p class="eyebrow">Chart report</p>
            <h2>${report.title.escapeHtml()}</h2>
            <p>${report.executiveSummary.ifBlank { "No summary generated." }.escapeHtml()}</p>
          </div>
          ${report.metrics.toMetricStrip()}
          ${report.findings.toFindingsBlock()}
          <div class="chart-grid">
            ${report.charts.toChartGrid()}
          </div>
          <details class="markdown-artifact report-data">
            <summary>Report data</summary>
            <pre><code>${report.toReportDataText().escapeHtml()}</code></pre>
          </details>
        </section>
        """.trimIndent()

    fun inlineError(message: String, title: String = "Unable to run the writer"): String =
        """
        <section class="notice notice--error">
          <strong>${title.escapeHtml()}</strong>
          <p>${message.escapeHtml()}</p>
        </section>
        """.trimIndent()

    fun toolingPage(overview: ToolingOverview): String =
        """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>Kaspel Tooling</title>
          <link rel="stylesheet" href="/styles.css">
        </head>
        <body>
          <main class="app-frame">
            <section class="workspace">
              <header class="topbar">
                <div>
                  <p class="eyebrow">Kaspel Tooling</p>
                  <h1>Registered tools</h1>
                </div>
                <nav class="topbar__actions" aria-label="Workspace views">
                  <a class="topbar__link" href="/">Essays</a>
                  <a class="topbar__link" href="/reports">Reports</a>
                  <a class="topbar__link topbar__link--active" href="/tools">Tools</a>
                  <a class="topbar__link" href="/tools.json">JSON</a>
                </nav>
              </header>

              <section class="tool-summary" aria-label="Tooling summary">
                <div>
                  <span>${overview.totalGroups}</span>
                  <p>Tool groups</p>
                </div>
                <div>
                  <span>${overview.totalTools}</span>
                  <p>Loaded tools</p>
                </div>
                <div>
                  <span>${overview.superMcp.loadedTools}</span>
                  <p>Superset MCP tools</p>
                </div>
              </section>

              ${overview.superMcp.toSuperMcpPanel()}

              <section class="tool-section" aria-labelledby="registered-tool-groups">
                <div class="section-heading">
                  <p class="eyebrow">Runtime registry</p>
                  <h2 id="registered-tool-groups">Active tool groups</h2>
                </div>
                <div class="tool-grid">
                  ${overview.groups.toToolGroupGrid()}
                </div>
              </section>
            </section>
          </main>
        </body>
        </html>
        """.trimIndent()

    private fun SuperMcpOverview.toSuperMcpPanel(): String =
        """
        <section class="tool-panel" aria-labelledby="super-mcp-title">
          <div class="tool-panel__header">
            <div>
              <p class="eyebrow">MCP connection</p>
              <h2 id="super-mcp-title">${provider.escapeHtml()}</h2>
            </div>
            <span class="status-pill status-pill--${statusClass()}">${statusLabel().escapeHtml()}</span>
          </div>
          <p>${description.escapeHtml()}</p>
          <dl class="tool-detail-grid">
            <div>
              <dt>Profile</dt>
              <dd><code>${profile.escapeHtml()}</code></dd>
            </div>
            <div>
              <dt>Role</dt>
              <dd><code>${role.escapeHtml()}</code></dd>
            </div>
            <div>
              <dt>Endpoint</dt>
              <dd><code>${fullEndpoint.escapeHtml()}</code></dd>
            </div>
            <div>
              <dt>Registration</dt>
              <dd>${if (registered) "Registered" else "Not registered"}</dd>
            </div>
          </dl>
          <p class="muted">Start command: <code>SPRING_PROFILES_ACTIVE=${profile.escapeHtml()} ./gradlew bootRun</code></p>
        </section>
        """.trimIndent()

    private fun List<ToolGroupOverview>.toToolGroupGrid(): String =
        if (isEmpty()) {
            """
            <section class="notice">
              <strong>No tool groups registered</strong>
              <p>The application context did not expose any Embabel tool groups.</p>
            </section>
            """.trimIndent()
        } else {
            joinToString("\n") { it.toToolGroupCard() }
        }

    private fun ToolGroupOverview.toToolGroupCard(): String =
        """
        <article class="tool-card">
          <header>
            <div>
              <p>${provider.escapeHtml()}</p>
              <h3>${role.escapeHtml()}</h3>
            </div>
            <span>${tools.size}</span>
          </header>
          <p>${description.escapeHtml()}</p>
          <ul class="tool-list">
            ${tools.toToolListItems()}
          </ul>
        </article>
        """.trimIndent()

    private fun List<ToolOverview>.toToolListItems(): String =
        if (isEmpty()) {
            """<li><span class="muted">No tools loaded.</span></li>"""
        } else {
            joinToString("\n") { tool ->
                """
                <li>
                  <strong>${tool.name.escapeHtml()}</strong>
                  <span>${tool.description.escapeHtml()}</span>
                </li>
                """.trimIndent()
            }
        }

    private fun SuperMcpOverview.statusLabel(): String =
        when {
            registered && loadedTools > 0 -> "Ready"
            registered -> "Registered"
            profileActive -> "Profile active"
            else -> "Profile inactive"
        }

    private fun SuperMcpOverview.statusClass(): String =
        when {
            registered && loadedTools > 0 -> "ready"
            registered || profileActive -> "registered"
            else -> "inactive"
        }

    private fun goalItem(run: EssayRun, goal: PipelineGoal, state: String, detail: String): String =
        """
        <li id="${run.domId("goal-${goal.id}")}" class="goal goal--${state.escapeHtml()}" data-replace-target="#${run.domId("goal-${goal.id}")}">
          <span class="goal__state">${state.goalLabel().escapeHtml()}</span>
          <div>
            <strong>${goal.title.escapeHtml()}</strong>
            <p>${detail.escapeHtml()}</p>
          </div>
        </li>
        """.trimIndent()

    private fun goalItem(run: ReportRun, goal: ReportPipelineGoal, state: String, detail: String): String =
        """
        <li id="${run.domId("goal-${goal.id}")}" class="goal goal--${state.escapeHtml()}" data-replace-target="#${run.domId("goal-${goal.id}")}">
          <span class="goal__state">${state.goalLabel().escapeHtml()}</span>
          <div>
            <strong>${goal.title.escapeHtml()}</strong>
            <p>${detail.escapeHtml()}</p>
          </div>
        </li>
        """.trimIndent()
}

private enum class PipelineGoal(
    val id: String,
    val actionName: String,
    val title: String,
    val detail: String,
) {
    Context(
        id = "context",
        actionName = "buildContextCapsule",
        title = "Build DICE context",
        detail = "Extract propositions, entities, constraints, and retrieval questions.",
    ),
    Research(
        id = "research",
        actionName = "researchTopic",
        title = "Research topic",
        detail = "Collect useful context for the requested topic.",
    ),
    Draft(
        id = "draft",
        actionName = "writeDraft",
        title = "Write draft",
        detail = "Create a practical beginner-friendly Markdown draft.",
    ),
    Eval(
        id = "eval",
        actionName = "evaluateDraft",
        title = "Evaluate draft",
        detail = "Check relevancy and faithfulness before review.",
    ),
    Review(
        id = "review",
        actionName = "reviewDraft",
        title = "Review content",
        detail = "Tighten the draft and correct technical issues.",
    ),
    Tldr(
        id = "tldr",
        actionName = "addTldr",
        title = "Add TLDR",
        detail = "Summarize the essay at the top of the artifact.",
    ),
    FrontMatter(
        id = "front-matter",
        actionName = "addFrontMatter",
        title = "Publish artifact",
        detail = "Create front matter, reading stats, and the saved Markdown file.",
    );

    companion object {
        fun fromAction(actionName: String): PipelineGoal? =
            entries.firstOrNull { it.actionName == actionName }
    }
}

private enum class ReportPipelineGoal(
    val id: String,
    val actionName: String,
    val title: String,
    val detail: String,
) {
    Query(
        id = "query",
        actionName = "queryReportData",
        title = "Query Superset MCP",
        detail = "Collect source data through the remote Superset MCP server.",
    ),
    Charts(
        id = "charts",
        actionName = "designCharts",
        title = "Design charts",
        detail = "Shape numeric values into chart-ready series.",
    ),
    Publish(
        id = "publish",
        actionName = "publishReport",
        title = "Publish report",
        detail = "Render the structured report and chart output.",
    );

    companion object {
        fun fromAction(actionName: String): ReportPipelineGoal? =
            entries.firstOrNull { it.actionName == actionName }
    }
}

private data class RenderedEssay(
    val title: String,
    val description: String,
    val readTime: String,
    val tags: List<String>,
    val keywords: List<String>,
    val bodyHtml: String,
)

private fun renderPublishedEssay(essay: PublishedEssay): RenderedEssay {
    val parsed = parseFrontMatter(essay.content)
    return RenderedEssay(
        title = parsed.metadata.singleValue("title").ifBlank { essay.title },
        description = parsed.metadata.singleValue("description").ifBlank { "No description generated." },
        readTime = parsed.metadata.singleValue("readTime").ifBlank { "Not available" },
        tags = parsed.metadata["tags"].orEmpty(),
        keywords = parsed.metadata["keywords"].orEmpty(),
        bodyHtml = markdownToHtml(parsed.body),
    )
}

private data class ParsedMarkdown(
    val metadata: Map<String, List<String>>,
    val body: String,
)

private fun parseFrontMatter(content: String): ParsedMarkdown {
    val lines = content.lines()
    if (lines.firstOrNull()?.trim() != "---") {
        return ParsedMarkdown(emptyMap(), content)
    }

    val metadata = linkedMapOf<String, MutableList<String>>()
    var currentListKey: String? = null
    var closingIndex: Int? = null

    for (index in 1 until lines.size) {
        val line = lines[index]
        if (line.trim() == "---") {
            closingIndex = index
            break
        }

        val trimmed = line.trim()
        if (trimmed.startsWith("- ") && currentListKey != null) {
            metadata.getOrPut(currentListKey) { mutableListOf() }
                .add(trimmed.removePrefix("- ").cleanYamlValue())
            continue
        }

        val separatorIndex = line.indexOf(':')
        if (separatorIndex > 0 && !line.startsWith(" ")) {
            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1).trim()
            currentListKey = if (value.isBlank()) {
                metadata.getOrPut(key) { mutableListOf() }
                key
            } else {
                metadata[key] = mutableListOf(value.cleanYamlValue())
                null
            }
        }
    }

    val bodyStart = closingIndex?.plus(1) ?: 0
    return ParsedMarkdown(
        metadata = metadata,
        body = lines.drop(bodyStart).joinToString("\n").trim(),
    )
}

private fun markdownToHtml(markdown: String): String {
    val html = StringBuilder()
    val paragraph = mutableListOf<String>()
    var inCodeBlock = false
    var codeLanguage = ""
    var inUnorderedList = false
    var inOrderedList = false

    fun closeParagraph() {
        if (paragraph.isNotEmpty()) {
            html.append("<p>")
                .append(inlineMarkdown(paragraph.joinToString(" ")))
                .append("</p>\n")
            paragraph.clear()
        }
    }

    fun closeLists() {
        if (inUnorderedList) {
            html.append("</ul>\n")
            inUnorderedList = false
        }
        if (inOrderedList) {
            html.append("</ol>\n")
            inOrderedList = false
        }
    }

    markdown.lines().forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            closeParagraph()
            closeLists()
            if (inCodeBlock) {
                html.append("</code></pre>\n")
                inCodeBlock = false
            } else {
                codeLanguage = trimmed.removePrefix("```").trim()
                val languageClass = codeLanguage.takeIf { it.isNotBlank() }
                    ?.let { " class=\"language-${it.escapeHtml()}\"" }
                    .orEmpty()
                html.append("<pre><code$languageClass>")
                inCodeBlock = true
            }
            return@forEach
        }

        if (inCodeBlock) {
            html.append(rawLine.escapeHtml()).append('\n')
            return@forEach
        }

        if (trimmed.isBlank()) {
            closeParagraph()
            closeLists()
            return@forEach
        }

        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").matchEntire(trimmed)
        if (headingMatch != null) {
            closeParagraph()
            closeLists()
            val level = (headingMatch.groupValues[1].length + 1).coerceAtMost(6)
            html.append("<h$level>")
                .append(inlineMarkdown(headingMatch.groupValues[2]))
                .append("</h$level>\n")
            return@forEach
        }

        if (trimmed.startsWith(">")) {
            closeParagraph()
            closeLists()
            html.append("<blockquote><p>")
                .append(inlineMarkdown(trimmed.removePrefix(">").trim()))
                .append("</p></blockquote>\n")
            return@forEach
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            closeParagraph()
            if (inOrderedList) {
                html.append("</ol>\n")
                inOrderedList = false
            }
            if (!inUnorderedList) {
                html.append("<ul>\n")
                inUnorderedList = true
            }
            html.append("<li>")
                .append(inlineMarkdown(trimmed.drop(2)))
                .append("</li>\n")
            return@forEach
        }

        val orderedMatch = Regex("^\\d+\\.\\s+(.+)$").matchEntire(trimmed)
        if (orderedMatch != null) {
            closeParagraph()
            if (inUnorderedList) {
                html.append("</ul>\n")
                inUnorderedList = false
            }
            if (!inOrderedList) {
                html.append("<ol>\n")
                inOrderedList = true
            }
            html.append("<li>")
                .append(inlineMarkdown(orderedMatch.groupValues[1]))
                .append("</li>\n")
            return@forEach
        }

        closeLists()
        paragraph.add(trimmed)
    }

    closeParagraph()
    closeLists()
    if (inCodeBlock) {
        html.append("</code></pre>\n")
    }
    return html.toString()
}

private fun inlineMarkdown(text: String): String =
    text.escapeHtml()
        .replace(Regex("`([^`]+)`")) { "<code>${it.groupValues[1]}</code>" }
        .replace(Regex("\\*\\*(.+?)\\*\\*")) { "<strong>${it.groupValues[1]}</strong>" }
        .replace(Regex("\\*(.+?)\\*")) { "<em>${it.groupValues[1]}</em>" }

private fun Map<String, List<String>>.singleValue(key: String): String =
    this[key]?.firstOrNull().orEmpty()

private fun String.cleanYamlValue(): String =
    trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")

private fun List<String>.toChipList(): String =
    if (isEmpty()) {
        "<span class=\"muted\">None</span>"
    } else {
        joinToString("") { "<span class=\"chip\">${it.escapeHtml()}</span>" }
    }

private fun List<DiceContextProposition>.toPropositionItems(): String =
    if (isEmpty()) {
        """<li><span class="muted">No propositions extracted.</span></li>"""
    } else {
        joinToString("\n") { proposition ->
            """
            <li>
              <p>${proposition.text.escapeHtml()}</p>
              <dl>
                <div><dt>Confidence</dt><dd>${proposition.confidence.toPercent()}</dd></div>
                <div><dt>Importance</dt><dd>${proposition.importance.toPercent()}</dd></div>
                <div><dt>Decay</dt><dd>${proposition.decay.toPercent()}</dd></div>
              </dl>
              ${proposition.reasoning.takeIf { it.isNotBlank() }?.let { "<span>${it.escapeHtml()}</span>" }.orEmpty()}
            </li>
            """.trimIndent()
        }
    }

private fun List<DiceEntityMention>.toEntityMentionItems(): String =
    if (isEmpty()) {
        """<li><span class="muted">No entity mentions extracted.</span></li>"""
    } else {
        joinToString("\n") { mention ->
            """
            <li>
              <strong>${mention.span.escapeHtml()}</strong>
              <span>${mention.type.escapeHtml()} / ${mention.role.escapeHtml()}</span>
            </li>
            """.trimIndent()
        }
    }

private fun List<String>.toPlainListItems(): String =
    if (isEmpty()) {
        """<li><span class="muted">None</span></li>"""
    } else {
        joinToString("\n") { "<li>${it.escapeHtml()}</li>" }
    }

private fun List<DokimosEvalCheck>.toDokimosCheckItems(): String =
    if (isEmpty()) {
        """<li><span class="muted">No Dokimos checks were run.</span></li>"""
    } else {
        joinToString("\n") { check ->
            val state = if (check.passed) "pass" else "fail"
            """
            <li class="dokimos-check dokimos-check--$state">
              <span>${if (check.passed) "Pass" else "Review"}</span>
              <div>
                <strong>${check.name.escapeHtml()}</strong>
                <p>${check.input.escapeHtml()}</p>
                <dl>
                  <div><dt>Score</dt><dd>${check.score.toScoreLabel()}</dd></div>
                  <div><dt>Threshold</dt><dd>${check.threshold.toScoreLabel()}</dd></div>
                </dl>
                <small>${check.reason.escapeHtml()}</small>
              </div>
            </li>
            """.trimIndent()
        }
    }

private fun List<ReportMetric>.toMetricStrip(): String {
    val cards = filter { it.label.isNotBlank() || it.value.isNotBlank() }
    if (cards.isEmpty()) {
        return """
        <section class="notice">
          <strong>No metrics returned</strong>
          <p>The report did not include summary metrics.</p>
        </section>
        """.trimIndent()
    }

    return """
    <dl class="metric-strip">
      ${cards.joinToString("\n") { metric ->
        val value = listOf(metric.value, metric.unit)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Not available" }
        """
        <div class="metric-card metric-card--${metric.trend.safeCssToken()}">
          <dt>${metric.label.ifBlank { "Metric" }.escapeHtml()}</dt>
          <dd>${value.escapeHtml()}</dd>
          <p>${metric.detail.escapeHtml()}</p>
        </div>
        """.trimIndent()
    }}
    </dl>
    """.trimIndent()
}

private fun List<String>.toFindingsBlock(): String =
    if (isEmpty()) {
        ""
    } else {
        """
        <section class="report-findings">
          <h3>Findings</h3>
          <ul>
            ${joinToString("\n") { "<li>${it.escapeHtml()}</li>" }}
          </ul>
        </section>
        """.trimIndent()
    }

private fun List<ReportChart>.toChartGrid(): String =
    if (isEmpty()) {
        """
        <section class="notice">
          <strong>No chartable data returned</strong>
          <p>The reporting agent completed, but it did not return numeric chart series.</p>
        </section>
        """.trimIndent()
    } else {
        joinToString("\n") { renderReportChart(it) }
    }

private fun renderReportChart(chart: ReportChart): String {
    val series = chart.series
        .mapNotNull { rawSeries ->
            val points = rawSeries.points
                .filter { it.label.isNotBlank() && it.value.isFinite() }
                .take(MAX_CHART_POINTS)
            if (points.isEmpty()) {
                null
            } else {
                NumericReportSeries(
                    name = rawSeries.name.ifBlank { "Series" },
                    points = points,
                )
            }
        }
        .take(MAX_CHART_SERIES)

    if (series.isEmpty()) {
        return """
        <article class="chart-card">
          <header>
            <h3>${chart.title.ifBlank { "Chart" }.escapeHtml()}</h3>
            <span>${chart.type.ifBlank { "bar" }.escapeHtml()}</span>
          </header>
          <section class="notice">
            <strong>No numeric series</strong>
            <p>This chart did not include renderable numeric points.</p>
          </section>
        </article>
        """.trimIndent()
    }

    val svg = if (chart.type.contains("line", ignoreCase = true)) {
        renderLineChart(chart, series)
    } else {
        renderBarChart(chart, series)
    }

    return """
    <article class="chart-card">
      <header>
        <h3>${chart.title.ifBlank { "Chart" }.escapeHtml()}</h3>
        <span>${chart.type.ifBlank { "bar" }.escapeHtml()}</span>
      </header>
      $svg
      ${series.toLegend()}
    </article>
    """.trimIndent()
}

private fun renderBarChart(chart: ReportChart, series: List<NumericReportSeries>): String {
    val labels = series
        .flatMap { it.points.map { point -> point.label } }
        .distinct()
        .take(MAX_BAR_LABELS)
    val values = series.flatMap { it.points.map { point -> point.value } }
    val minValue = min(0.0, values.minOrNull() ?: 0.0)
    val maxValue = max(0.0, values.maxOrNull() ?: 0.0)
    val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0
    val zeroY = CHART_PLOT_Y + CHART_PLOT_HEIGHT - (((0.0 - minValue) / range) * CHART_PLOT_HEIGHT)
    val groupWidth = CHART_PLOT_WIDTH / labels.size.coerceAtLeast(1)
    val barWidth = ((groupWidth - 14.0) / series.size.coerceAtLeast(1)).coerceIn(8.0, 42.0)

    fun yFor(value: Double): Double =
        CHART_PLOT_Y + CHART_PLOT_HEIGHT - (((value - minValue) / range) * CHART_PLOT_HEIGHT)

    val bars = labels.joinToString("\n") { label ->
        series.mapIndexedNotNull { seriesIndex, item ->
            val value = item.points.firstOrNull { it.label == label }?.value ?: return@mapIndexedNotNull null
            val valueY = yFor(value)
            val x = CHART_PLOT_X + (labels.indexOf(label) * groupWidth) + 7.0 + (seriesIndex * barWidth)
            val y = min(valueY, zeroY)
            val height = abs(zeroY - valueY).coerceAtLeast(2.0)
            """
            <rect x="${x.svgNumber()}" y="${y.svgNumber()}" width="${barWidth.svgNumber()}" height="${height.svgNumber()}" rx="3" fill="${chartColor(seriesIndex)}">
              <title>${item.name.escapeHtml()} ${label.escapeHtml()}: ${value.displayNumber()} ${chart.unit.escapeHtml()}</title>
            </rect>
            """.trimIndent()
        }.joinToString("\n")
    }

    val labelText = labels.mapIndexed { index, label ->
        val x = CHART_PLOT_X + (index * groupWidth) + (groupWidth / 2.0)
        """<text x="${x.svgNumber()}" y="244" text-anchor="middle">${label.compactForUi(14).escapeHtml()}</text>"""
    }.joinToString("\n")

    return chartSvg(chart) {
        """
        ${chartAxes(chart, minValue, maxValue)}
        $bars
        $labelText
        """.trimIndent()
    }
}

private fun renderLineChart(chart: ReportChart, series: List<NumericReportSeries>): String {
    val values = series.flatMap { it.points.map { point -> point.value } }
    val minValue = values.minOrNull() ?: 0.0
    val maxValue = values.maxOrNull() ?: 1.0
    val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    fun xFor(index: Int, total: Int): Double =
        CHART_PLOT_X + if (total <= 1) 0.0 else (index.toDouble() / (total - 1).toDouble()) * CHART_PLOT_WIDTH

    fun yFor(value: Double): Double =
        CHART_PLOT_Y + CHART_PLOT_HEIGHT - (((value - minValue) / range) * CHART_PLOT_HEIGHT)

    val lineMarkup = series.mapIndexed { seriesIndex, item ->
        val points = item.points.take(MAX_CHART_POINTS)
        val coordinates = points.mapIndexed { index, point ->
            "${xFor(index, points.size).svgNumber()},${yFor(point.value).svgNumber()}"
        }.joinToString(" ")
        val circles = points.mapIndexed { index, point ->
            val x = xFor(index, points.size)
            val y = yFor(point.value)
            """
            <circle cx="${x.svgNumber()}" cy="${y.svgNumber()}" r="4" fill="${chartColor(seriesIndex)}">
              <title>${item.name.escapeHtml()} ${point.label.escapeHtml()}: ${point.value.displayNumber()} ${chart.unit.escapeHtml()}</title>
            </circle>
            """.trimIndent()
        }.joinToString("\n")
        """
        <polyline points="$coordinates" fill="none" stroke="${chartColor(seriesIndex)}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"></polyline>
        $circles
        """.trimIndent()
    }.joinToString("\n")

    val baseLabels = series.firstOrNull()?.points.orEmpty()
    val labelStep = max(1, (baseLabels.size + 4) / 5)
    val labels = baseLabels.mapIndexedNotNull { index, point ->
        if (index % labelStep != 0 && index != baseLabels.lastIndex) {
            null
        } else {
            """<text x="${xFor(index, baseLabels.size).svgNumber()}" y="244" text-anchor="middle">${point.label.compactForUi(12).escapeHtml()}</text>"""
        }
    }.joinToString("\n")

    return chartSvg(chart) {
        """
        ${chartAxes(chart, minValue, maxValue)}
        $lineMarkup
        $labels
        """.trimIndent()
    }
}

private fun chartSvg(chart: ReportChart, body: () -> String): String =
    """
    <svg class="chart-svg" viewBox="0 0 640 280" role="img" aria-label="${chart.title.ifBlank { "Report chart" }.escapeHtml()}">
      ${body()}
    </svg>
    """.trimIndent()

private fun chartAxes(chart: ReportChart, minValue: Double, maxValue: Double): String =
    """
    <line x1="${CHART_PLOT_X.svgNumber()}" y1="${CHART_PLOT_Y.svgNumber()}" x2="${CHART_PLOT_X.svgNumber()}" y2="${(CHART_PLOT_Y + CHART_PLOT_HEIGHT).svgNumber()}" class="chart-axis"></line>
    <line x1="${CHART_PLOT_X.svgNumber()}" y1="${(CHART_PLOT_Y + CHART_PLOT_HEIGHT).svgNumber()}" x2="${(CHART_PLOT_X + CHART_PLOT_WIDTH).svgNumber()}" y2="${(CHART_PLOT_Y + CHART_PLOT_HEIGHT).svgNumber()}" class="chart-axis"></line>
    <text x="58" y="${(CHART_PLOT_Y + 4).svgNumber()}" text-anchor="end" class="chart-axis-value">${maxValue.displayNumber()}</text>
    <text x="58" y="${(CHART_PLOT_Y + CHART_PLOT_HEIGHT).svgNumber()}" text-anchor="end" class="chart-axis-value">${minValue.displayNumber()}</text>
    <text x="320" y="270" text-anchor="middle" class="chart-axis-title">${chart.xAxisLabel.escapeHtml()}</text>
    <text x="18" y="132" text-anchor="middle" class="chart-axis-title" transform="rotate(-90 18 132)">${chart.yAxisLabel.ifBlank { chart.unit }.escapeHtml()}</text>
    """.trimIndent()

private fun List<NumericReportSeries>.toLegend(): String =
    """
    <ol class="chart-legend">
      ${mapIndexed { index, series ->
        """
        <li>
          <span style="--legend-color: ${chartColor(index)}"></span>
          ${series.name.escapeHtml()}
        </li>
        """.trimIndent()
    }.joinToString("\n")}
    </ol>
    """.trimIndent()

private fun PublishedReport.toReportDataText(): String =
    buildString {
        appendLine("Title: $title")
        appendLine("Request: $request")
        appendLine("Generated: $generatedAt")
        appendLine()
        appendLine("Metrics:")
        if (metrics.isEmpty()) {
            appendLine("- none")
        } else {
            metrics.forEach { metric ->
                appendLine("- ${metric.label}: ${metric.value} ${metric.unit}".trim())
                if (metric.detail.isNotBlank()) {
                    appendLine("  ${metric.detail}")
                }
            }
        }
        appendLine()
        appendLine("Charts:")
        if (charts.isEmpty()) {
            appendLine("- none")
        } else {
            charts.forEach { chart ->
                appendLine("- ${chart.title} (${chart.type})")
                chart.series.forEach { series ->
                    appendLine("  ${series.name}:")
                    series.points.forEach { point ->
                        appendLine("    ${point.label}: ${point.value}")
                    }
                }
            }
        }
        appendLine()
        appendLine("Source notes:")
        if (sourceNotes.isEmpty()) {
            appendLine("- none")
        } else {
            sourceNotes.forEach { appendLine("- $it") }
        }
    }

private data class NumericReportSeries(
    val name: String,
    val points: List<ReportPoint>,
)

private const val MAX_CHART_SERIES = 4
private const val MAX_CHART_POINTS = 12
private const val MAX_BAR_LABELS = 8
private const val CHART_PLOT_X = 66.0
private const val CHART_PLOT_Y = 24.0
private const val CHART_PLOT_WIDTH = 526.0
private const val CHART_PLOT_HEIGHT = 196.0

private val CHART_COLORS = listOf(
    "#0f766e",
    "#2563eb",
    "#b45309",
    "#7c3aed",
)

private fun chartColor(index: Int): String =
    CHART_COLORS[index % CHART_COLORS.size]

private fun Double.svgNumber(): String =
    String.format(Locale.US, "%.2f", this)

private fun Double.displayNumber(): String =
    String.format(Locale.US, if (abs(this) >= 100.0 || this % 1.0 == 0.0) "%.0f" else "%.2f", this)

private fun Double.toPercent(): String =
    String.format(Locale.US, "%.0f%%", coerceIn(0.0, 1.0) * 100.0)

private fun Double.toScoreLabel(): String =
    String.format(Locale.US, "%.2f", coerceIn(0.0, 1.0))

private fun String.safeCssToken(): String =
    lowercase(Locale.US)
        .replace(Regex("[^a-z0-9-]+"), "-")
        .trim('-')
        .ifBlank { "unknown" }

private fun String.goalLabel(): String =
    when (this) {
        "active" -> "Now"
        "complete" -> "Done"
        "error" -> "Issue"
        else -> "Queued"
    }

private fun EssayRun.domId(suffix: String): String =
    "run-$id-$suffix"

private fun ReportRun.domId(suffix: String): String =
    "report-run-$id-$suffix"

private fun String.escapeHtml(): String =
    HtmlUtils.htmlEscape(this)
