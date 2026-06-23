package kaspel.essayist.guardrails

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.AdvisorChain
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor
import org.springframework.core.Ordered
import org.springframework.stereotype.Component

/**
 * Spring AI advisor variant of the essay style guardrail.
 *
 * Embabel actions use EssayStyleGuardRail directly, while this advisor keeps the
 * same policy available to any direct ChatClient path.
 */
@Component
class EssayStyleAdvisor : BaseAdvisor {

    private val log = LoggerFactory.getLogger(EssayStyleAdvisor::class.java)

    override fun getName(): String = "EssayStyleAdvisor"

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 100

    override fun before(chatClientRequest: ChatClientRequest, advisorChain: AdvisorChain): ChatClientRequest {
        val userText = chatClientRequest.prompt().userMessages.joinToString("\n") { it.text }
        val promptViolations = EssayStylePolicy.promptViolations(userText)
        if (promptViolations.isNotEmpty()) {
            throw IllegalArgumentException(promptViolations.joinToString("; "))
        }

        log.debug("Applying {} to ChatClient request", getName())
        val guardedPrompt = chatClientRequest.prompt()
            .augmentUserMessage("${EssayStylePolicy.STYLE_INSTRUCTIONS}\n\n$userText")

        return chatClientRequest.mutate()
            .prompt(guardedPrompt)
            .context("essay.style.guardrail", getName())
            .build()
    }

    override fun after(chatClientResponse: ChatClientResponse, advisorChain: AdvisorChain): ChatClientResponse =
        chatClientResponse
}
