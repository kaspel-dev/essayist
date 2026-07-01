package kaspel.essayist

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class SkillsJarPackagingTests {

    @Test
    fun `essay review skill is packaged under the SkillsJars metadata path`() {
        val resource = ClassPathResource("META-INF/skills/kaspel/essayist/essay-review/SKILL.md")

        assertThat(resource.exists()).isTrue()
        assertThat(resource.inputStream.bufferedReader().use { it.readText() })
            .contains("# Essay Review Skill")
            .contains("DICE context capsule")
            .contains("Dokimos evaluation report")
    }
}
