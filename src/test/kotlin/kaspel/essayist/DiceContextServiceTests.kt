package kaspel.essayist

import com.embabel.dice.common.support.Sha256ContentHasher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiceContextServiceTests {

    @Test
    fun `prepare request normalizes topic and detects duplicate source hashes`() {
        val service = DiceContextService(Sha256ContentHasher)

        val first = service.prepareRequest("  Spring   AI context engineering  ")
        val duplicate = service.prepareRequest("Spring AI context engineering")

        assertThat(first.topic).isEqualTo("Spring AI context engineering")
        assertThat(first.contextId).startsWith("essay-")
        assertThat(first.sourceHash).hasSize(64)
        assertThat(first.duplicateSource).isFalse()
        assertThat(duplicate.sourceHash).isEqualTo(first.sourceHash)
        assertThat(duplicate.duplicateSource).isTrue()
    }

    @Test
    fun `assemble sanitizes extracted context and provides fallback propositions`() {
        val service = DiceContextService(Sha256ContentHasher)
        val request = service.prepareRequest("Context engineering with DICE")

        val capsule = service.assemble(
            request = request,
            extraction = DiceContextExtraction(
                propositions = listOf(
                    DiceContextProposition(
                        text = "  Context engineering needs typed domain objects. ",
                        confidence = 1.3,
                        importance = -0.2,
                        decay = Double.NaN,
                    ),
                    DiceContextProposition(text = " "),
                ),
                entityMentions = listOf(
                    DiceEntityMention(span = " DICE ", type = " Library ", role = " subject "),
                    DiceEntityMention(span = "DICE", type = "Library", role = "SUBJECT"),
                ),
                constraints = listOf("  Use grounded claims.  ", "Use grounded claims."),
                retrievalQuestions = emptyList(),
            ),
        )

        assertThat(capsule.propositions).hasSize(1)
        assertThat(capsule.propositions.first().text).isEqualTo("Context engineering needs typed domain objects.")
        assertThat(capsule.propositions.first().confidence).isEqualTo(1.0)
        assertThat(capsule.propositions.first().importance).isEqualTo(0.0)
        assertThat(capsule.propositions.first().decay).isEqualTo(0.0)
        assertThat(capsule.entityMentions).containsExactly(
            DiceEntityMention(span = "DICE", type = "Library", role = "SUBJECT")
        )
        assertThat(capsule.constraints).containsExactly("Use grounded claims.")
        assertThat(capsule.retrievalQuestions).containsExactly("What current, verifiable sources clarify Context engineering with DICE?")
    }

    @Test
    fun `capsule for topic builds live dice context without model extraction`() {
        val service = DiceContextService(Sha256ContentHasher)

        val capsule = service.capsuleForTopic("Embabel DICE for Spring AI")

        assertThat(capsule.contextId).startsWith("essay-")
        assertThat(capsule.propositions).hasSize(3)
        assertThat(capsule.propositions.first().text).contains("Embabel DICE for Spring AI")
        assertThat(capsule.entityMentions.map { it.span }).contains("Embabel DICE for Spring AI", "Embabel", "DICE", "Spring")
        assertThat(capsule.retrievalQuestions).anyMatch { it.contains("Embabel DICE for Spring AI") }
    }

    @Test
    fun `prompt block exposes context capsule fields for downstream actions`() {
        val capsule = EssayContextCapsule(
            topic = "DICE for essays",
            contextId = "essay-abc",
            sourceHash = "abcdef",
            propositions = listOf(DiceContextProposition(text = "DICE structures essay context.", confidence = 0.9)),
            entityMentions = listOf(DiceEntityMention(span = "DICE", type = "Library", role = "SUBJECT")),
            constraints = listOf("Keep claims atomic."),
            retrievalQuestions = listOf("Which sources describe DICE?"),
        )

        val promptBlock = capsule.promptBlock()

        assertThat(promptBlock).contains("DICE context capsule")
        assertThat(promptBlock).contains("Context id: essay-abc")
        assertThat(promptBlock).contains("DICE structures essay context.")
        assertThat(promptBlock).contains("DICE [Library, SUBJECT]")
        assertThat(promptBlock).contains("Keep claims atomic.")
    }
}
