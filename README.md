# MAIG Android SDK

The official Android/JVM SDK for the [Mobile AI Gateway](https://app.maig.dev) — a single API that routes to the best available AI model (GPT-4o, Claude, Gemini, etc.) with built-in rate limiting, cost controls, and analytics.

## Features

- **One-shot generation** — `generateText()` with automatic retry + exponential backoff
- **Streaming** — `streamText()` returns a Kotlin `Flow<String>` that emits tokens as they arrive
- **Error model** — typed `AIGatewayError` sealed class (auth failure, server error, network error)
- **Testable** — `NetworkClient` interface makes unit testing straightforward without a live server
- **Minimal dependencies** — OkHttp, Gson, kotlinx-coroutines

## Requirements

- Kotlin 1.9+
- Java 17+
- Android API 26+ (or any JVM target)

## Installation

### Gradle (Maven Central — coming soon)

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.maig:sdk:0.1.0")
}
```

### Local build

Until the library is published to Maven Central, build and publish it to your local Maven repository:

```bash
git clone https://github.com/your-org/maig-android-sdk.git
cd maig-android-sdk
./gradlew :sdk:publishToMavenLocal
```

Then add `mavenLocal()` to your project's repository list and the dependency above.

## Quick Start

### One-shot

```kotlin
import com.maig.sdk.AIGatewayClient
import com.maig.sdk.GenerateOptions

val client = AIGatewayClient(apiKey = "maig_YOUR_KEY")

// suspend function — call from a coroutine or viewModelScope
val response = client.generateText(
    prompt = "Summarise the following article in three bullet points: ...",
    options = GenerateOptions(model = "gpt-4o", maxTokens = 512),
)
println(response)
```

### Streaming

```kotlin
client.streamText(prompt = "Write a haiku about coroutines")
    .collect { token -> print(token) }
```

### Android ViewModel example

```kotlin
class ChatViewModel : ViewModel() {
    private val client = AIGatewayClient(apiKey = BuildConfig.MAIG_API_KEY)

    val response = MutableStateFlow("")

    fun ask(prompt: String) {
        viewModelScope.launch {
            try {
                client.streamText(prompt).collect { token ->
                    response.value += token
                }
            } catch (e: AIGatewayError.AuthFailure) {
                // handle invalid key
            } catch (e: AIGatewayError.ServerError) {
                // handle server error (e.statusCode, e.body)
            } catch (e: AIGatewayError.NetworkError) {
                // handle connectivity issues
            }
        }
    }
}
```

## Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `apiKey` | — | Your project API key (`maig_...`) |
| `baseUrl` | `https://api.maig.dev` | Override for local/staging gateway |

### `GenerateOptions`

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `model` | `String?` | `"auto"` | Target model identifier |
| `userId` | `String?` | `null` | Stable end-user ID for rate limiting |
| `maxTokens` | `Int?` | `null` | Token budget for the response |

## Error Handling

```kotlin
try {
    client.generateText("Hello")
} catch (e: AIGatewayError.AuthFailure) {
    // HTTP 401 — bad or expired API key; not retried
} catch (e: AIGatewayError.ServerError) {
    println("HTTP ${e.statusCode}: ${e.body}")
} catch (e: AIGatewayError.NetworkError) {
    println("Network problem: ${e.cause?.message}")
}
```

`generateText` retries up to **2 times** with exponential backoff (1 s, 2 s) on transient server and network errors. Auth failures are never retried.

`streamText` does **not** retry; let the caller decide whether to restart the flow.

## Running Tests

```bash
./gradlew :sdk:test
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

[MIT](LICENSE)
