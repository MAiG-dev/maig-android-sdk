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
 * ## Versioning
 *
 * Prompts use semantic versioning (major.minor). A **major bump** (e.g. v1.0 → v2.0)
 * means a new `{{VARIABLE}}` was added — your app code must handle it. A **minor bump**
 * (e.g. v1.0 → v1.1) is a safe content update with no new variables.
 *
 * ## Version pinning
 *
 * Pin a prompt to a major version so the SDK only ever receives minor updates within
 * that major. The SDK will never download a version beyond the pinned major until you
 * explicitly update the pin — protecting your app from breaking changes until you are
 * ready to handle the new variable.
 *
 * ```kotlin
 * // Pin via JSON config file (maig-prompts.json in your assets folder):
 * // { "pinned": { "support-bot": 1, "onboarding": 2 } }
 * val store = PromptStore(apiKey = "maig_...", context = applicationContext,
 *                         configFile = "maig-prompts.json")
 *
 * // Or pin at runtime (overrides the JSON file):
 * store.pin("support-bot", majorVersion = 1)
 * ```
 *
 * Prompt names are immutable after creation on the server. Renaming a prompt
 * is a delete + create operation; clients receive the deletion on next sync.
 *
 * @param apiKey     Your project API key (starts with `maig_`).
 * @param context    Android [Context] used to resolve the internal files directory.
 * @param baseUrl    Override the default gateway URL — useful for testing.
 * @param configFile Asset filename for pinned version config. Defaults to `"maig-prompts.json"`,
 *                   so the SDK automatically loads that file from your `assets/` folder if it
 *                   exists. Pass `null` to disable config file loading entirely.
 *                   Format: `{ "pinned": { "my-prompt": 1 } }`.
 *                   Runtime [pin] calls override values from this file.
 */
class PromptStore @JvmOverloads constructor(
    private val apiKey: String,
    private val context: Context,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val configFile: String? = DEFAULT_CONFIG_FILE,
) {
    private val gson = Gson()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** In-memory cache: prompt name → [CachedPromptSet] */
    private var cache: MutableMap<String, CachedPromptSet> = mutableMapOf()

    /** Pinned major versions: prompt name → major version number */
    private val pinnedMajors: MutableMap<String, Int> = mutableMapOf()

    private val cacheFile: File by lazy {
        var hash = -3750763034362895579L
        for (c in apiKey) {
            hash = hash xor c.code.toLong()
            hash *= 1099511628211L
        }
        val suffix = "%016x".format(hash)
        File(context.filesDir, "maig_prompts_$suffix.json")
    }

    init {
        loadCacheFromDisk()
        loadConfigFromAssets()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Pins a prompt to a major version so the SDK only receives minor updates within
     * that major. Call this before [sync] for the pin to take effect on the next sync.
     *
     * A major version bump (e.g. v1 → v2) indicates a new `{{VARIABLE}}` was added.
     * Pinning to v1 ensures your app never receives v2+ content until you are ready
     * to update your code to handle the new variable and bump the pin to 2.
     *
     * Runtime pins override values loaded from the JSON config file.
     *
     * @param name         The prompt name to pin.
     * @param majorVersion The major version to pin to (e.g. `1` for all v1.x updates).
     */
    fun pin(name: String, majorVersion: Int) {
        pinnedMajors[name] = majorVersion
    }

    /**
     * Fetches changed prompts from the server and updates the local cache.
     *
     * Only prompts whose content has changed since the last sync are transmitted.
     * Pinned prompts are only updated within their pinned major version.
     *
     * Safe to call at app launch or whenever a refresh is desired.
     *
     * @throws AIGatewayError on authentication failure, server error, or network error.
     */
    suspend fun sync() = withContext(Dispatchers.IO) {
        val bodyMap = mutableMapOf<String, Any>()
        if (cache.isNotEmpty()) {
            bodyMap["hashes"] = cache.mapValues { it.value.contentHash }
        }
        if (pinnedMajors.isNotEmpty()) {
            bodyMap["pinned"] = pinnedMajors.toMap()
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
                val responseText = response.body?.string().orEmpty()
                when {
                    response.code == 401 -> throw AIGatewayError.AuthFailure()
                    !response.isSuccessful -> throw AIGatewayError.ServerError(response.code, responseText.takeIf { it.isNotBlank() })
                    else -> responseText
                }
            }
        } catch (e: AIGatewayError) {
            throw e
        } catch (e: Exception) {
            throw AIGatewayError.NetworkError(e)
        }

        val syncResponse = gson.fromJson(responseBody, SyncResponse::class.java)

        for (payload in syncResponse.prompts) {
            cache[payload.name] = CachedPromptSet(
                name = payload.name,
                majorVersion = payload.majorVersion,
                minorVersion = payload.minorVersion,
                contentHash = payload.contentHash,
                messages = payload.messages,
            )
        }

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
            val loaded: MutableMap<String, CachedPromptSet>? = gson.fromJson(json, type)
            // Migrate legacy entries that have `version` but no `majorVersion`.
            cache = loaded?.mapValues { (_, v) ->
                if (v.majorVersion == 0 && v.legacyVersion != null) {
                    v.copy(majorVersion = v.legacyVersion, minorVersion = 0)
                } else v
            }?.toMutableMap() ?: mutableMapOf()
        } catch (_: Exception) {
            // Corrupted cache — start fresh on next sync.
        }
    }

    private fun loadConfigFromAssets() {
        val file = configFile ?: return
        try {
            val json = context.assets.open(file).bufferedReader().use { it.readText() }
            val config = gson.fromJson(json, PromptsConfig::class.java)
            config?.pinned?.forEach { (name, major) -> pinnedMajors.putIfAbsent(name, major) }
        } catch (_: Exception) {
            // Config file not found or malformed — pinning relies on runtime calls only.
        }
    }

    private fun persistCacheToDisk() {
        val json = gson.toJson(cache)
        val tmp = File(cacheFile.parent, ".${cacheFile.name}.tmp")
        tmp.writeText(json)
        tmp.renameTo(cacheFile)
    }

    // ── Internal models ───────────────────────────────────────────────────────

    private data class CachedPromptSet(
        val name: String,
        @SerializedName("majorVersion") val majorVersion: Int = 1,
        @SerializedName("minorVersion") val minorVersion: Int = 0,
        /** Legacy field — only present in caches written before semver. */
        @SerializedName("version") val legacyVersion: Int? = null,
        @SerializedName("contentHash") val contentHash: String,
        val messages: List<Message>,
    )

    private data class SyncResponse(
        val prompts: List<PromptPayload>,
        val deletedNames: List<String>,
    )

    private data class PromptPayload(
        val name: String,
        val majorVersion: Int,
        val minorVersion: Int,
        val contentHash: String,
        val messages: List<Message>,
    )

    private data class PromptsConfig(
        val pinned: Map<String, Int>?,
    )

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.maig.dev"
        const val DEFAULT_CONFIG_FILE = "maig-prompts.json"
    }
}
