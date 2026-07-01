package kaspel.essayist

import org.springframework.stereotype.Component
import java.util.Locale
import kotlin.math.abs

@Component
class ReportChartPlanner {

    fun plan(data: ReportData): ChartPlan {
        val rows = data.rows
            .filter { row -> row.label.isNotBlank() && row.values.isNotEmpty() }
            .take(MAX_CHART_ROWS)

        if (rows.isEmpty()) {
            return ChartPlan(
                title = data.title.ifBlank { "Reporting agent output" },
                executiveSummary = data.sourceSummary.ifBlank { "No chartable numeric rows were returned by the MCP query." },
                findings = data.metrics.take(MAX_FINDINGS).map { it.toFinding() },
                charts = emptyList(),
                notes = listOf("No chart was generated because the MCP result did not include chartable rows."),
            )
        }

        val metricKeys = rows
            .flatMap { it.values.keys }
            .distinct()
            .take(MAX_SERIES)

        val chart = ReportChart(
            title = data.title.ifBlank { "MCP result" },
            type = rows.chartType(),
            unit = data.metrics.firstOrNull { it.unit.isNotBlank() }?.unit.orEmpty(),
            xAxisLabel = rows.firstOrNull()?.category?.ifBlank { null } ?: "Category",
            yAxisLabel = metricKeys.firstOrNull()?.toDisplayLabel() ?: "Value",
            series = metricKeys.map { metricKey ->
                ReportSeries(
                    name = metricKey.toDisplayLabel(),
                    points = rows.mapNotNull { row ->
                        row.values[metricKey]
                            ?.takeIf { it.isFinite() }
                            ?.let { ReportPoint(label = row.label.toShortLabel(), value = it) }
                    },
                )
            }.filter { it.points.isNotEmpty() },
        )

        return ChartPlan(
            title = data.title.ifBlank { "Reporting agent output" },
            executiveSummary = data.sourceSummary.ifBlank { summarizeRows(rows, metricKeys) },
            findings = buildFindings(data, rows, metricKeys),
            charts = listOf(chart).filter { it.series.isNotEmpty() },
            notes = listOf("Chart plan was generated locally from MCP result rows to keep the report demo fast."),
        )
    }

    private fun buildFindings(data: ReportData, rows: List<ReportDataRow>, metricKeys: List<String>): List<String> {
        val metricFindings = data.metrics
            .filter { it.label.isNotBlank() || it.value.isNotBlank() }
            .take(MAX_FINDINGS)
            .map { it.toFinding() }

        val rowFindings = metricKeys.take(2).mapNotNull { key ->
            val values = rows.mapNotNull { row -> row.values[key]?.takeIf { it.isFinite() }?.let { row.label to it } }
            val max = values.maxByOrNull { it.second }
            val min = values.minByOrNull { it.second }
            if (max == null || min == null || abs(max.second - min.second) < 0.000001) {
                null
            } else {
                "${max.first} leads ${key.toDisplayLabel()} at ${max.second.toCompactNumber()}, while ${min.first} is lowest at ${min.second.toCompactNumber()}."
            }
        }

        return (metricFindings + rowFindings)
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_FINDINGS)
            .ifEmpty { listOf(summarizeRows(rows, metricKeys)) }
    }

    private fun ReportMetric.toFinding(): String {
        val label = label.ifBlank { "Metric" }
        val renderedValue = listOf(value, unit)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { detail }
        return "$label: $renderedValue".trimEnd(':', ' ')
    }

    private fun summarizeRows(rows: List<ReportDataRow>, metricKeys: List<String>): String {
        val rowCount = rows.size
        val seriesCount = metricKeys.size
        return "The MCP result returned $rowCount chartable row(s) across $seriesCount numeric series."
    }

    private fun List<ReportDataRow>.chartType(): String {
        val labels = map { it.label.lowercase(Locale.US) }
        return if (labels.any { MONTH_LABELS.any(it::contains) } || size > 6) "line" else "bar"
    }

    private fun String.toDisplayLabel(): String =
        replace(Regex("[_\\-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            .ifBlank { "Value" }

    private fun String.toShortLabel(): String =
        replace(Regex("\\s+"), " ")
            .trim()
            .let { if (it.length <= 24) it else "${it.take(21)}..." }

    private fun Double.toCompactNumber(): String =
        when {
            abs(this) >= 1_000_000 -> "%.2fM".format(Locale.US, this / 1_000_000.0)
            abs(this) >= 1_000 -> "%.1fK".format(Locale.US, this / 1_000.0)
            else -> "%.2f".format(Locale.US, this)
        }

    companion object {
        private const val MAX_CHART_ROWS = 8
        private const val MAX_SERIES = 4
        private const val MAX_FINDINGS = 4
        private val MONTH_LABELS = setOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    }
}
