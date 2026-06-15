package kaspel.essayist

import com.embabel.agent.api.annotation.LlmTool
import org.springframework.stereotype.Component
import kotlin.math.ceil
import kotlin.math.max

@Component
class ReadingStatsTool {

    @LlmTool(
        description = "Calculate word count and estimated reading time (in minutes) for a piece of text. Reading speed is assumed to be 200 words per minute."
    )
    fun calculateReadingStats(
        @LlmTool.Param(description = "The full text of the essay to analyze")
        text: String?,
    ): String {
        if (text.isNullOrBlank()) {
            return "0 words, 0 min read"
        }
        val words = text.trim().split(Regex("\\s+")).size
        val minutes = max(1, ceil(words / WORDS_PER_MINUTE.toDouble()).toInt())
        return "%d words, %d min read".format(words, minutes)
    }

    companion object {
        private const val WORDS_PER_MINUTE = 200
    }
}
