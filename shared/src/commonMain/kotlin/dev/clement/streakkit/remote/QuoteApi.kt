package dev.clement.streakkit.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A motivational line shown above the habit list. The only networked feature in the app. */
data class Quote(
    val text: String,
    val author: String,
)

interface QuoteApi {
    suspend fun randomQuote(): Quote
}

/**
 * Talks to a keyless public endpoint so this repo needs no secrets and CI stays deterministic.
 * The wire shape is kept in a private DTO so the domain type does not inherit the API's naming.
 */
class HttpQuoteApi(
    private val client: HttpClient,
    private val endpoint: String = DEFAULT_ENDPOINT,
) : QuoteApi {
    override suspend fun randomQuote(): Quote {
        val dto: QuoteDto = client.get(endpoint).body()
        return Quote(text = dto.quote, author = dto.author)
    }

    @Serializable
    private data class QuoteDto(
        val id: Int = 0,
        val quote: String,
        val author: String,
    )

    companion object {
        const val DEFAULT_ENDPOINT: String = "https://dummyjson.com/quotes/random"
    }
}

/**
 * Shown when the network call fails. An offline app that says nothing looks broken; an offline app
 * that quietly falls back looks finished.
 */
object OfflineQuotes {
    private val canned =
        listOf(
            Quote("A habit is a vote for the person you want to become.", "James Clear"),
            Quote("Discipline is choosing between what you want now and what you want most.", "Abraham Lincoln"),
            Quote("We are what we repeatedly do.", "Will Durant"),
            Quote("Small deeds done are better than great deeds planned.", "Peter Marshall"),
        )

    fun pick(seed: Int): Quote = canned[(seed.mod(canned.size))]
}

internal fun HttpClientConfig<*>.installStreakKitDefaults() {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 8_000
        connectTimeoutMillis = 5_000
    }
}

/** OkHttp on Android, Darwin (NSURLSession) on iOS. */
internal expect fun createHttpClient(): HttpClient
