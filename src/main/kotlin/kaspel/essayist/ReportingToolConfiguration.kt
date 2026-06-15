package kaspel.essayist

import com.embabel.agent.core.ToolGroup
import com.embabel.agent.core.ToolGroupDescription
import com.embabel.agent.core.ToolGroupPermission
import com.embabel.agent.tools.mcp.McpToolGroup
import io.modelcontextprotocol.client.McpSyncClient
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
            filter = { true },
        )
}
