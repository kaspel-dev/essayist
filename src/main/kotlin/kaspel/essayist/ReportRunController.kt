package kaspel.essayist

import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Controller
class ReportingPageController {

    @GetMapping("/reports")
    fun reports(): String = "forward:/reports.html"
}

@RestController
@RequestMapping("/report-runs")
class ReportRunController(
    private val reportRunService: ReportRunService,
) {

    @PostMapping(
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.TEXT_HTML_VALUE],
    )
    fun createRun(
        @RequestParam prompt: String,
        @RequestParam(defaultValue = "false") demo: Boolean,
    ): String {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isBlank()) {
            return HtmlFragments.inlineError(
                title = "Unable to run the reporter",
                message = "Enter a report brief before starting the reporting agent.",
            )
        }
        if (!demo) reportRunService.readinessError()?.let { message ->
            return HtmlFragments.inlineError(
                title = "Unable to run the reporter",
                message = message,
            )
        }
        return HtmlFragments.reportRunShell(reportRunService.createRun(trimmedPrompt, demo))
    }

    @GetMapping("/{id}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamRun(@PathVariable id: String): SseEmitter =
        reportRunService.streamRun(id)
}
