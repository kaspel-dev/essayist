package kaspel.essayist

import com.embabel.agent.api.annotation.LlmTool
import com.embabel.agent.api.common.support.SelfToolGroup
import com.embabel.agent.core.CoreToolGroups
import com.embabel.agent.core.ToolGroupDescription
import com.embabel.agent.core.ToolGroupPermission
import com.embabel.common.core.types.Semver
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class WebTools(
    private val objectMapper: ObjectMapper,
) : SelfToolGroup {

    override val description: ToolGroupDescription = ToolGroupDescription(
        description = "No-key web tools for fetching readable pages and searching Wikipedia.",
        role = CoreToolGroups.WEB,
    )
    override val provider: String = "Kaspel Essayist"
    override val version: Semver = Semver(0, 1, 0)
    override val permissions: Set<ToolGroupPermission> = setOf(ToolGroupPermission.INTERNET_ACCESS)

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    @LlmTool(
        name = "fetch_readable",
        description = "Fetch an HTTP or HTTPS URL and return readable page text suitable for research."
    )
    fun fetchReadable(
        @LlmTool.Param(description = "The HTTP or HTTPS URL to fetch")
        url: String,
        @LlmTool.Param(description = "Maximum number of characters to return", required = false)
        maxLength: Int = DEFAULT_MAX_LENGTH,
    ): String =
        runCatching {
            val uri = url.toHttpUri()
            val response = client.send(buildGetRequest(uri), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            if (response.statusCode() !in 200..299) {
                return@runCatching "ERROR: Failed to fetch $url. HTTP ${response.statusCode()}."
            }

            val contentType = response.headers().firstValue("content-type").orElse("")
            val body = response.body()
            val readable = if (contentType.contains("html", ignoreCase = true)) {
                htmlToReadableText(body)
            } else {
                body
            }
            readable.limitForTool(maxLength)
        }.getOrElse { e ->
            "ERROR: Failed to fetch $url. ${e.message ?: e::class.simpleName}"
        }

    @LlmTool(
        name = "search_wikipedia",
        description = "Search Wikipedia and return top matching article titles, URLs, and snippets."
    )
    fun searchWikipedia(
        @LlmTool.Param(description = "Search query")
        query: String,
        @LlmTool.Param(description = "Maximum number of search results to return", required = false)
        limit: Int = 5,
    ): String =
        runCatching {
            val trimmedQuery = query.trim()
            if (trimmedQuery.isBlank()) {
                return@runCatching "ERROR: Search query must not be blank."
            }

            val safeLimit = limit.coerceIn(1, 10)
            val uri = URI(
                "https://en.wikipedia.org/w/api.php" +
                    "?action=query&list=search&format=json&origin=*&srlimit=$safeLimit" +
                    "&srsearch=${trimmedQuery.urlEncode()}"
            )
            val response = client.send(buildGetRequest(uri), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            if (response.statusCode() !in 200..299) {
                return@runCatching "ERROR: Wikipedia search failed. HTTP ${response.statusCode()}."
            }

            val results = objectMapper.readTree(response.body()).path("query").path("search")
            if (!results.isArray || results.isEmpty) {
                return@runCatching "No Wikipedia results found for \"$trimmedQuery\"."
            }

            results.mapIndexed { index, result ->
                val title = result.path("title").asText()
                val snippet = htmlToReadableText(result.path("snippet").asText())
                val articleUrl = "https://en.wikipedia.org/wiki/${title.replace(' ', '_').urlEncode()}"
                "${index + 1}. $title\n$articleUrl\n$snippet"
            }.joinToString("\n\n")
        }.getOrElse { e ->
            "ERROR: Wikipedia search failed. ${e.message ?: e::class.simpleName}"
        }

    private fun buildGetRequest(uri: URI): HttpRequest =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "text/html,application/json,text/plain;q=0.9,*/*;q=0.8")
            .header("User-Agent", "kaspel-essayist/0.1")
            .GET()
            .build()

    private fun String.toHttpUri(): URI {
        val uri = URI(this.trim())
        require(uri.scheme == "http" || uri.scheme == "https") {
            "Only HTTP and HTTPS URLs are supported."
        }
        return uri
    }

    private fun htmlToReadableText(html: String): String =
        HtmlUtils.htmlUnescape(
            html
                .replace(Regex("""(?is)<(script|style|noscript|svg|header|footer|nav)[^>]*>.*?</\1>"""), " ")
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("""(?i)</(p|div|section|article|main|h[1-6]|li|tr)>"""), "\n")
                .replace(Regex("""(?s)<[^>]+>"""), " ")
        )
            .lines()
            .map { line -> line.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    private fun String.limitForTool(maxLength: Int): String {
        val safeLimit = maxLength.coerceIn(1_000, 40_000)
        return if (length <= safeLimit) {
            this
        } else {
            take(safeLimit) + "\n\n[truncated at $safeLimit characters]"
        }
    }

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8).replace("+", "%20")

    companion object {
        private const val DEFAULT_MAX_LENGTH = 12_000
    }
}
