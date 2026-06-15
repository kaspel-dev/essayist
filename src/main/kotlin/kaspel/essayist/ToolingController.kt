package kaspel.essayist

import com.embabel.agent.core.ToolGroup
import org.springframework.core.env.Environment
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ToolingController(
    private val toolGroups: List<ToolGroup>,
    private val environment: Environment,
) {

    @GetMapping("/tools", produces = [MediaType.TEXT_HTML_VALUE])
    fun toolsPage(): String =
        HtmlFragments.toolingPage(toolingOverview())

    @GetMapping("/tools.json", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listTools(): ToolingOverview =
        toolingOverview()

    private fun toolingOverview(): ToolingOverview {
        val groups = toolGroups
            .sortedWith(compareBy<ToolGroup> { it.metadata.role }.thenBy { it.metadata.provider })
            .map { group ->
                ToolGroupOverview(
                    role = group.metadata.role,
                    provider = group.metadata.provider,
                    description = group.metadata.description,
                    tools = runCatching {
                        group.tools.map { tool ->
                            ToolOverview(
                                name = tool.definition.name,
                                description = tool.definition.description,
                            )
                        }.sortedBy { it.name }
                    }.getOrDefault(emptyList()),
                )
            }

        return ToolingOverview(
            groups = groups,
            superMcp = superMcpOverview(groups),
        )
    }

    private fun superMcpOverview(groups: List<ToolGroupOverview>): SuperMcpOverview {
        val group = groups.firstOrNull { it.role == ReportingToolGroups.SUPER_MCP_REPORTING }
        val baseUrl = environment.getProperty(SUPER_MCP_URL_PROPERTY)
            ?: SUPER_MCP_DEFAULT_URL
        val endpoint = environment.getProperty(SUPER_MCP_ENDPOINT_PROPERTY)
            ?: SUPER_MCP_DEFAULT_ENDPOINT

        return SuperMcpOverview(
            profile = SUPER_MCP_PROFILE,
            provider = group?.provider ?: SUPER_MCP_PROVIDER,
            role = ReportingToolGroups.SUPER_MCP_REPORTING,
            url = baseUrl,
            endpoint = endpoint,
            fullEndpoint = baseUrl.trimEnd('/') + "/" + endpoint.trimStart('/'),
            profileActive = environment.activeProfiles.contains(SUPER_MCP_PROFILE),
            registered = group != null,
            loadedTools = group?.tools?.size ?: 0,
            description = group?.description ?: SUPER_MCP_DESCRIPTION,
        )
    }

    companion object {
        private const val SUPER_MCP_PROFILE = "mcp-super"
        private const val SUPER_MCP_PROVIDER = "Superset MCP"
        private const val SUPER_MCP_DEFAULT_URL = "http://localhost:5008"
        private const val SUPER_MCP_DEFAULT_ENDPOINT = "/mcp"
        private const val SUPER_MCP_DESCRIPTION = "Reporting tools exposed by the Superset MCP server."
        private const val SUPER_MCP_URL_PROPERTY = "spring.ai.mcp.client.streamable-http.connections.super-mcp.url"
        private const val SUPER_MCP_ENDPOINT_PROPERTY = "spring.ai.mcp.client.streamable-http.connections.super-mcp.endpoint"
    }
}

data class ToolingOverview(
    val groups: List<ToolGroupOverview>,
    val superMcp: SuperMcpOverview,
) {
    val totalGroups: Int = groups.size
    val totalTools: Int = groups.sumOf { it.tools.size }
}

data class ToolGroupOverview(
    val role: String,
    val provider: String,
    val description: String,
    val tools: List<ToolOverview>,
)

data class ToolOverview(
    val name: String,
    val description: String,
)

data class SuperMcpOverview(
    val profile: String,
    val provider: String,
    val role: String,
    val url: String,
    val endpoint: String,
    val fullEndpoint: String,
    val profileActive: Boolean,
    val registered: Boolean,
    val loadedTools: Int,
    val description: String,
)
