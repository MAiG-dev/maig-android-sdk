package com.maig.sdk

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The result of a [PromptStore.getPrompt] call with a variables map.
 *
 * @property messages         Messages with matched `{{VAR}}` placeholders replaced.
 *                            Unmatched placeholders are left as-is.
 * @property missingVariables Placeholder names found in the template that were not
 *                            supplied in the variables map.
 * @property extraVariables   Keys in the variables map that did not appear in any
 *                            template placeholder.
 */
data class PromptResult(
    val messages: List<Message>,
    val missingVariables: List<String>,
    val extraVariables: List<String>,
)

/**
 * Manages a local cache of server-side prompt sets for a project.
 *
 * Create a [PromptStore] with your project API key, call [sync] at app launch
 * to fetch any changed prompts, then use [getPrompt] at inference time to retrieve
 * the cached messages — no network call required.
 *
 * Prompt names are immutable after creation on the server. Renaming a prompt
 * is a delete + create operation; clients receive the deletion on next sync.
 *
 * ```kotlin
 * val store = PromptStore(apiKey = "maig_...", context = applicationContext)
 *
 * // At app launch (in a coroutine)
 * store.sync()
 *
 * // At inference time
 * val storedMessages = store.getPrompt("support-bot-system") ?: emptyList()
 * val messages = storedMessages + Message(role = "user", content = "Hi")
 * val result = client.generateText(messages = messages)
 * ```
 *
 * @param apiKey  Your project API key (starts with `maig_`).
 * @param context Android [Context] used to resolve the internal files directory.
 * @param baseUrl Override the default gateway URL — useful for testing.
 */
class PromptStore @JvmOverloads constructor(
    private val apiKey: String,
    private val context: Context,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val gson = Gson()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** In-memory cache: prompt name → [CachedPromptSet] */
    private var cache: MutableMap<String, CachedPromptSet> = mutableMapOf()

    private val cacheFile: File by lazy {
        // Derive an opaque filename from a simple hash of the API key so the
        // file name does not leak the key value.
        var hash = -3750763034362895579L
        for (c in apiKey) {
            hash = hash xor c.code.toLong()
            hash *= 1099511628211L
        }
        val suffix = "%016x".format(hash)
        File(context.filesDir, "maig_prompts_$suffix.json")
    }

    init {
        // Load persisted cache synchronously so getPrompt() works immediately.
        loadCacheFromDisk()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches changed prompts from the server and updates the local cache.
     *
     * Safe to call at app launch or whenever a refresh is desired.
     * Only prompts whose content has changed since the last sync are transmitted.
     *
     * @throws AIGatewayError on authentication failure, server error, or network error.
     */
    suspend fun sync() = withContext(Dispatchers.IO) {
        val bodyMap: Map<String, Any> = if (cache.isEmpty()) {
            emptyMap()
        } else {
            mapOf("hashes" to cache.mapValues { it.value.contentHash })
        }
        val json = gson.toJson(bodyMap)
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/v1/prompts/sync")
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val responseBody = try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 401 -> throw AIGatewayError.AuthFailure()
                    !response.isSuccessful -> throw AIGatewayError.ServerError(response.code, body.takeIf { it.isNotBlank() })
                    else -> body
                }
            }
        } catch (e: AIGatewayError) {
            throw e
        } catch (e: Exception) {
            throw AIGatewayError.NetworkError(e)
        }

        val syncResponse = gson.fromJson(responseBody, SyncResponse::class.java)

        // Merge returned prompts (upsert by name).
        for (payload in syncResponse.prompts) {
            cache[payload.name] = CachedPromptSet(
                name = payload.name,
                version = payload.version,
                contentHash = payload.contentHash,
                messages = payload.messages,
            )
        }

        // Remove deleted prompts.
        for (name in syncResponse.deletedNames) {
            cache.remove(name)
        }

        persistCacheToDisk()
    }

    /**
     * Returns the cached messages for a prompt by name, or `null` if the prompt
     * has never been synced (i.e. [sync] has not yet run successfully, or the
     * named prompt does not exist on the server).
     */
    fun getPrompt(name: String): List<Message>? = cache[name]?.messages

    /**
     * Returns the cached messages for a prompt with `{{VARIABLE}}` placeholders replaced,
     * along with diagnostics about any mismatches.
     *
     * - Placeholders whose key is absent from [variables] are left unchanged in the content.
     * - Keys in [variables] that do not appear in any placeholder are reported in
     *   [PromptResult.extraVariables].
     *
     * @param name      The prompt name.
     * @param variables Map of variable name → replacement value (case-sensitive).
     * @return [PromptResult] with substituted messages and mismatch lists,
     *         or `null` if the prompt has never been synced or does not exist.
     */
    fun getPrompt(name: String, variables: Map<String, String>): PromptResult? {
        val cached = cache[name] ?: return null

        val pattern = Regex("""\{\{([A-Za-z_][A-Za-z0-9_]*)\}\}""")
        val templateVars = cached.messages
            .flatMap { pattern.findAll(it.content).map { m -> m.groupValues[1] } }
            .toSet()

        val suppliedKeys = variables.keys
        val missing = (templateVars - suppliedKeys).sorted()
        val extra = (suppliedKeys - templateVars).sorted()

        val substituted = cached.messages.map { msg ->
            var content = msg.content
            for ((key, value) in variables) {
                content = content.replace("{{$key}}", value)
            }
            Message(role = msg.role, content = content)
        }

        return PromptResult(messages = substituted, missingVariables = missing, extraVariables = extra)
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun loadCacheFromDisk() {
        if (!cacheFile.exists()) return
        try {
            val json = cacheFile.readText()
            val type = object : TypeToken<MutableMap<String, CachedPromptSet>>() {}.type
            cache = gson.fromJson(json, type) ?: mutableMapOf()
        } catch (_: Exception) {
            // Corrupted cache — start fresh on next sync.
        }
    }

    private fun persistCacheToDisk() {
        val json = gson.toJson(cache)
        // Atomic write: write to a temp file, then rename.
        val tmp = File(cacheFile.parent, ".${cacheFile.name}.tmp")
        tmp.writeText(json)
        tmp.renameTo(cacheFile)
    }

    // ── Internal models ───────────────────────────────────────────────────────

    private data class CachedPromptSet(
        val name: String,
        val version: Int,
        @SerializedName("contentHash") val contentHash: String,
        val messages: List<Message>,
    )

    private data class SyncResponse(
        val prompts: List<PromptPayload>,
        val deletedNames: List<String>,
    )

    private data class PromptPayload(
        val name: String,
        val version: Int,
        val contentHash: String,
        val messages: List<Message>,
    )

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.maig.dev"
    }
}
