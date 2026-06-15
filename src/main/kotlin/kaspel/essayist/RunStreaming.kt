package kaspel.essayist

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration

internal class HtmlStream(
    private val emitter: SseEmitter,
) {
    private var open = true

    @Synchronized
    fun fragment(html: String) {
        if (!open) {
            return
        }
        try {
            emitter.send(SseEmitter.event().name("fragment").data(html))
        } catch (e: Exception) {
            open = false
            throw e
        }
    }

    @Synchronized
    fun done() {
        if (!open) {
            return
        }
        open = false
        try {
            emitter.send(SseEmitter.event().name("done").data(""))
            emitter.complete()
        } catch (e: Exception) {
            emitter.completeWithError(e)
        }
    }
}

internal fun Duration.readableDuration(): String =
    when {
        toMillis() < 1_000 -> "${toMillis()} ms"
        toSeconds() < 60 -> "${toSeconds()} sec"
        else -> "${toMinutes()} min ${toSecondsPart()} sec"
    }

internal fun String.compactForUi(maxLength: Int = 180): String =
    replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else "${it.take(maxLength - 1)}..." }

internal fun pauseForDemo() {
    try {
        Thread.sleep(250)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}
