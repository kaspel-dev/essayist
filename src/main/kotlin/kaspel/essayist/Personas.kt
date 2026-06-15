package kaspel.essayist

import com.embabel.agent.prompt.persona.RoleGoalBackstory
import com.embabel.common.ai.prompt.PromptContributor

object Personas {

    val JSON_OUTPUT: PromptContributor = PromptContributor.fixed(
        """
        IMPORTANT: Your response will be parsed as strict JSON.
        Return only one JSON object. Do not wrap it in Markdown fences.
        Escape every double quote inside string values with a backslash.
        Escape every backslash inside string values as a double backslash.
        Escape newlines inside string values as \n.
        If a string value contains Markdown code examples, prefer single-quoted examples when possible.
        Example: {"content":"She said \"hello\".\nThen she left."}
        """.trimIndent()
    )

    val WRITER = RoleGoalBackstory(
        "Software Developer and Educator",
        "Write practical, beginner-friendly essays",
        "Experienced developer who loves teaching through clear, simple writing",
    )

    val REVIEWER = RoleGoalBackstory(
        "Technical Editor",
        "Review and polish technical essays",
        "Seasoned editor focused on clarity, accuracy, and tight writing",
    )
}
