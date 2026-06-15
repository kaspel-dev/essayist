package kaspel.essayist

import com.embabel.agent.core.CoreToolGroups
import com.embabel.agent.core.ToolGroupRequirement
import com.embabel.agent.spi.ToolGroupResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class EssayistApplicationTests {

    @Autowired
    private lateinit var toolGroupResolver: ToolGroupResolver

    @Autowired
    private lateinit var essayQualityTool: EssayQualityTool

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun contextLoads() {
    }

    @Test
    fun webToolGroupProvidesRequiredResearchTools() {
        val webTools = toolGroupResolver
            .resolveToolGroup(
                ToolGroupRequirement(
                    role = CoreToolGroups.WEB,
                    requiredToolNames = setOf("fetch_readable", "search_wikipedia"),
                )
            )
            .resolvedToolGroup
            ?.tools
            ?.map { it.definition.name }

        assertThat(webTools).contains("fetch_readable", "search_wikipedia")
    }

    @Test
    fun essayQualityToolBuildsDeterministicChecklist() {
        val checklist = essayQualityTool.buildEssayQualityChecklist(
            title = "Building Agents",
            content = """
                ## Overview
                
                This essay explains agents.
                
                ```kotlin
                println("hello")
                ```
                
                Source: https://example.com
            """.trimIndent(),
        )

        assertThat(checklist).contains("Review checklist for \"Building Agents\"")
        assertThat(checklist).contains("Section headings: 1")
        assertThat(checklist).contains("Code examples: 1")
        assertThat(checklist).contains("Source links: 1")
    }

    @Test
    fun toolsEndpointRendersReadableRegistry() {
        mockMvc.get("/tools")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
                content { string(org.hamcrest.Matchers.containsString("Registered tools")) }
                content { string(org.hamcrest.Matchers.containsString("Active tool groups")) }
                content { string(org.hamcrest.Matchers.containsString("Superset MCP")) }
                content { string(org.hamcrest.Matchers.containsString("SPRING_PROFILES_ACTIVE=mcp-super ./gradlew bootRun")) }
            }
    }

    @Test
    fun toolsJsonEndpointListsRegisteredToolGroups() {
        mockMvc.get("/tools.json")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.totalGroups") { exists() }
                jsonPath("$.groups[*].role") { exists() }
                jsonPath("$.superMcp.fullEndpoint") { value("http://localhost:5008/mcp") }
            }
    }

    @Test
    fun reportsPageIsServed() {
        mockMvc.get("/reports")
            .andExpect {
                status { isOk() }
                forwardedUrl("/reports.html")
            }

        mockMvc.get("/reports.html")
            .andExpect {
                status { isOk() }
                content { string(org.hamcrest.Matchers.containsString("Reporting agent")) }
                content { string(org.hamcrest.Matchers.containsString("hx-post=\"/report-runs\"")) }
                content { string(org.hamcrest.Matchers.containsString("Demo mode")) }
            }
    }

    @Test
    fun reportRunRejectsBlankPrompt() {
        mockMvc.post("/report-runs") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("prompt", " ")
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("Unable to run the reporter")) }
        }
    }

    @Test
    fun reportRunRequiresSuperMcpProfile() {
        mockMvc.post("/report-runs") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("prompt", "Show revenue by month")
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("Start this app with the mcp-super profile")) }
            content { string(org.hamcrest.Matchers.containsString("http://localhost:5008/mcp")) }
        }
    }

    @Test
    fun reportRunDemoModeBypassesSuperMcpReadiness() {
        mockMvc.post("/report-runs") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("prompt", "Show revenue by month")
            param("demo", "true")
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("Reporting run")) }
            content { string(org.hamcrest.Matchers.containsString("/report-runs/")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Start this app with the mcp-super profile"))) }
        }
    }
}
