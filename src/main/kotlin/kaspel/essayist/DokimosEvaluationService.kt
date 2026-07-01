package kaspel.essayist

import dev.dokimos.core.Dataset
import dev.dokimos.core.EvalResult
import dev.dokimos.core.Example
import dev.dokimos.core.evaluators.RegexEvaluator
import org.springframework.stereotype.Service

@Service
class DokimosEvaluationService {

    fun evaluateDraft(
        draft: DraftEssay,
        research: ResearchedTopic,
        context: EssayContextCapsule,
    ): DokimosEvalReport {
        val definitions = dokimosCheckDefinitions(context, research)
        val examples = definitions.map { it.toExample() }
        val dataset = Dataset.builder()
            .name("Essay draft quality gates")
            .description("Deterministic Dokimos checks for draft structure, topic anchoring, and DICE context use.")
            .addExamples(examples)
            .build()

        val checks = dataset.examples().zip(definitions).map { (example, definition) ->
            val testCase = example.toTestCase(draft.content)
            RegexEvaluator.builder()
                .name(definition.name)
                .pattern(definition.pattern)
                .ignoreCase(true)
                .threshold(1.0)
                .build()
                .evaluate(testCase)
                .toDokimosCheck(input = definition.input)
        }

        return DokimosEvalReport(
            datasetName = dataset.name(),
            checks = checks,
        )
    }

    private fun dokimosCheckDefinitions(
        context: EssayContextCapsule,
        research: ResearchedTopic,
    ): List<DokimosCheckDefinition> {
        val topicTerms = context.topic
            .importantTerms()
            .take(3)
            .ifEmpty { listOf(context.topic.trim()).filter { it.isNotBlank() } }

        val propositionTerms = context.propositions
            .flatMap { listOf(it.subject, it.target, it.text.bestAnchorTerm()) }
            .map { it.trim() }
            .filter { it.length >= MIN_ANCHOR_LENGTH }
            .distinct()
            .take(5)

        val researchTerms = research.research
            .importantTerms(RESEARCH_STOP_WORDS)
            .take(8)
            .ifEmpty { topicTerms + propositionTerms }

        return listOf(
            DokimosCheckDefinition(
                name = "Topic anchor",
                input = "Draft should include the main topic terms: ${topicTerms.joinToString(", ")}.",
                pattern = containsAllPattern(topicTerms),
            ),
            DokimosCheckDefinition(
                name = "DICE proposition anchor",
                input = "Draft should carry at least one proposition or entity from the DICE context.",
                pattern = containsAnyPattern(propositionTerms.ifEmpty { topicTerms }),
            ),
            DokimosCheckDefinition(
                name = "Research brief anchor",
                input = "Draft should reuse at least one term from the generated research brief.",
                pattern = containsAnyPattern(researchTerms.ifEmpty { topicTerms }),
            ),
            DokimosCheckDefinition(
                name = "Markdown structure",
                input = "Draft should include section headings in Markdown.",
                pattern = "(?m)^#{2,3}\\s+\\S+",
            ),
        )
    }

    private fun DokimosCheckDefinition.toExample(): Example =
        Example.builder()
            .input("input", input)
            .expectedOutput("output", pattern)
            .metadata("framework", "Dokimos")
            .metadata("evaluator", "RegexEvaluator")
            .build()

    private fun EvalResult.toDokimosCheck(input: String): DokimosEvalCheck =
        DokimosEvalCheck(
            name = name(),
            passed = success(),
            score = score(),
            threshold = threshold() ?: 1.0,
            reason = reason().orEmpty(),
            input = input,
        )

    private fun containsAllPattern(terms: List<String>): String {
        val normalized = terms
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalized.isEmpty()) {
            return "(?s).+"
        }
        return normalized.joinToString(prefix = "(?s)", separator = "") { "(?=.*${Regex.escape(it)})" } + ".*"
    }

    private fun containsAnyPattern(terms: List<String>): String {
        val normalized = terms
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalized.isEmpty()) {
            return "(?s).+"
        }
        return "(?s).*(${normalized.joinToString("|") { Regex.escape(it) }}).*"
    }

    private fun String.importantTerms(stopWords: Set<String> = STOP_WORDS): List<String> =
        split(Regex("[^A-Za-z0-9+#.]+"))
            .map { it.trim() }
            .filter { it.length >= MIN_ANCHOR_LENGTH }
            .filterNot { it.lowercase() in stopWords }
            .distinctBy { it.lowercase() }

    private fun String.bestAnchorTerm(): String =
        importantTerms()
            .maxByOrNull { it.length }
            .orEmpty()

    companion object {
        private const val MIN_ANCHOR_LENGTH = 4
        private val STOP_WORDS = setOf(
            "about",
            "after",
            "before",
            "with",
            "from",
            "into",
            "that",
            "this",
            "what",
            "which",
            "when",
            "where",
            "while",
            "should",
            "would",
            "could",
            "essay",
            "draft",
        )
        private val RESEARCH_STOP_WORDS = STOP_WORDS + setOf(
            "atomic",
            "brief",
            "capsule",
            "context",
            "dice",
            "duplicate",
            "generated",
            "hash",
            "metadata",
            "proposition",
            "propositions",
            "questions",
            "retrieval",
            "source",
        )
    }
}

private data class DokimosCheckDefinition(
    val name: String,
    val input: String,
    val pattern: String,
)
