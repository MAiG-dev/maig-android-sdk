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

### Gradle (JitPack)

```kotlin
// In your project-level build.gradle.kts
repositories {
    maven { url = uri("https://jitpack.io") }
}

// In your app-level build.gradle.kts
dependencies {
    implementation("com.github.maig-dev:maig-android-sdk:0.1.0")
}
```

### Local build

To build and publish to your local Maven repository instead:

```bash
git clone https://github.com/your-org/maig-android-sdk.git
cd maig-android-sdk
./gradlew :sdk:publishToMavenLocal
```

Then add `mavenLocal()` to your project's repository list and reference `com.github.maig-dev:maig-android-sdk:0.1.0` as the dependency.

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

## Prompt Management

`PromptStore` lets you define prompts on the server in your MAIG dashboard and deliver them to your app without a new release. At app startup, call `sync()` once to pull any changed prompts into a local cache. Every subsequent call to `getPrompt()` reads directly from that cache — no network call at inference time.

Prompts are defined and managed at [docs.maig.dev/prompt-management](https://docs.maig.dev/prompt-management).

### Initialization

```kotlin
val store = PromptStore(apiKey = "maig_your_key", context = applicationContext)
```

### Sync at startup

`sync()` is a suspend function. Call it once from a coroutine early in your app lifecycle — for example in `Application.onCreate()` or a `ViewModel` init block:

```kotlin
viewModelScope.launch {
    try {
        store.sync()
    } catch (e: AIGatewayError.AuthFailure) {
        // handle bad key
    } catch (e: AIGatewayError.NetworkError) {
        // handle connectivity issues — cached prompts still available
    }
}
```

Only prompts whose content has changed since the last sync are transmitted, so the request is lightweight on subsequent launches.

### Retrieving a prompt

```kotlin
// Returns List<Message>?, or null if the prompt hasn't been synced yet
val messages = store.getPrompt("welcome") ?: emptyList()
```

### Variable substitution

Prompts can contain `{{VARIABLE}}` placeholders defined on the server. Supply values at retrieval time:

```kotlin
val result = store.getPrompt("support", mapOf("userName" to "Alice"))
    ?: return

// result.messages — List<Message> with {{userName}} replaced by "Alice"
// result.missingVariables — placeholders in the template not supplied in the map
// result.extraVariables — map keys that did not correspond to any placeholder
val messages = result.messages
```

Use `missingVariables` and `extraVariables` during development to catch template/call-site mismatches early.

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
