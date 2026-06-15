package kaspel.essayist

import java.time.Instant

data class ReportData(
    val title: String = "",
    val request: String = "",
    val sourceSummary: String = "",
    val metrics: List<ReportMetric> = emptyList(),
    val rows: List<ReportDataRow> = emptyList(),
    val sourceNotes: List<String> = emptyList(),
)

data class ReportMetric(
    val label: String = "",
    val value: String = "",
    val unit: String = "",
    val detail: String = "",
    val trend: String = "unknown",
)

data class ReportDataRow(
    val label: String = "",
    val category: String = "",
    val values: Map<String, Double> = emptyMap(),
)

data class ChartPlan(
    val title: String = "",
    val executiveSummary: String = "",
    val findings: List<String> = emptyList(),
    val charts: List<ReportChart> = emptyList(),
    val notes: List<String> = emptyList(),
)

data class ReportChart(
    val title: String = "",
    val type: String = "bar",
    val unit: String = "",
    val xAxisLabel: String = "",
    val yAxisLabel: String = "",
    val series: List<ReportSeries> = emptyList(),
)

data class ReportSeries(
    val name: String = "",
    val points: List<ReportPoint> = emptyList(),
)

data class ReportPoint(
    val label: String = "",
    val value: Double = 0.0,
)

data class PublishedReport(
    val title: String = "",
    val request: String = "",
    val executiveSummary: String = "",
    val metrics: List<ReportMetric> = emptyList(),
    val findings: List<String> = emptyList(),
    val charts: List<ReportChart> = emptyList(),
    val sourceNotes: List<String> = emptyList(),
    val generatedAt: Instant = Instant.now(),
)
