package kaspel.essayist

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/runs")
class EssayRunController(
    private val essayRunService: EssayRunService,
) {

    @PostMapping(
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.TEXT_HTML_VALUE],
    )
    fun createRun(
        @RequestParam topic: String,
        @RequestParam(defaultValue = "false") demo: Boolean,
    ): String {
        val trimmedTopic = topic.trim()
        if (trimmedTopic.isBlank()) {
            return HtmlFragments.inlineError("Enter a topic before starting the writer.")
        }
        return HtmlFragments.runShell(essayRunService.createRun(trimmedTopic, demo))
    }

    @GetMapping("/{id}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamRun(@PathVariable id: String): SseEmitter =
        essayRunService.streamRun(id)
}
