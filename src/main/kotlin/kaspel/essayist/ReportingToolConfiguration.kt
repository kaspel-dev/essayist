package kaspel.essayist

import com.embabel.agent.core.ToolGroup
import com.embabel.agent.core.ToolGroupDescription
import com.embabel.agent.core.ToolGroupPermission
import com.embabel.agent.tools.mcp.McpToolGroup
import io.modelcontextprotocol.client.McpSyncClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

object ReportingToolGroups {
    const val SUPER_MCP_REPORTING = "super-mcp-reporting"
}

@Configuration
@ConditionalOnClass(McpSyncClient::class)
class ReportingToolConfiguration(
    private val mcpSyncClients: List<McpSyncClient>,
) {

    @Bean
    @ConditionalOnProperty(
        prefix = "spring.ai.mcp.client.streamable-http.connections.super-mcp",
        name = ["url"],
    )
    fun superMcpReportingToolGroup(): ToolGroup =
        McpToolGroup(
            description = ToolGroupDescription(
                description = "Reporting tools exposed by the Superset MCP server.",
                role = ReportingToolGroups.SUPER_MCP_REPORTING,
            ),
            name = ReportingToolGroups.SUPER_MCP_REPORTING,
            provider = "Superset MCP",
            permissions = setOf(ToolGroupPermission.INTERNET_ACCESS),
            clients = mcpSyncClients,
            filter = { callback ->
                val toolName = callback.toolDefinition.name()
                val include = ReportingToolFilters.isFastReportingTool(toolName)
                if (!include) {
                    log.debug("Skipping Superset MCP tool {} for fast report demo", toolName)
                }
                include
            },
        )

    companion object {
        private val log = LoggerFactory.getLogger(ReportingToolConfiguration::class.java)
    }
}

private object ReportingToolFilters {

    private val allowedSuffixes = setOf(
        "list_dashboards",
        "get_dashboard",
        "list_datasets",
        "get_dataset",
        "execute_sql",
    )

    fun isFastReportingTool(toolName: String): Boolean =
        allowedSuffixes.any { suffix -> toolName == suffix || toolName.endsWith("_$suffix") }
}
