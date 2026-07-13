package com.nishparadox.smriti.search

import android.content.Context
import android.util.Log
import androidx.appsearch.app.AppSearchSchema
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.GenericDocument
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.RemoveByDocumentIdRequest
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.localstorage.LocalStorage
import androidx.concurrent.futures.await
import com.nishparadox.smriti.notes.SnipStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ranked full-text search over the local smarans, backed by **Jetpack AppSearch** — Google's
 * first-party on-device search engine (Icing). BM25F relevance ranking and per-field weights are
 * built in, so we write no ranking code, and it scales as the corpus grows.
 *
 * The jsonl store stays the source of truth; the AppSearch index is a **derived mirror** we re-sync
 * from [SnipStore] whenever it changes ([SnipStore.version]). This is the first (local) search
 * source — external stores (Logseq via GitHub / its local API) are delegated to, not indexed here.
 */
object SearchIndex {
    private const val TAG = "SMRITI"
    private const val DB = "smriti-search"
    private const val NAMESPACE = "smarans"
    private const val TYPE = "Smaran"

    private lateinit var appContext: Context
    private val mutex = Mutex()                       // AppSearch sessions aren't thread-safe
    private var session: AppSearchSession? = null
    private var syncedVersion = -1
    private var syncedIds = emptySet<Long>()

    fun init(ctx: Context) { appContext = ctx.applicationContext }

    /**
     * Ranked smaran ids, best match first. `null` = no searchable tokens (blank / punctuation-only)
     * → caller keeps newest-first order. Empty = a real query that matched nothing. All tokens must
     * match (AND); the final token is a prefix (search-as-you-type).
     */
    suspend fun search(query: String): List<Long>? {
        val match = buildMatch(query) ?: return null
        return mutex.withLock {
            runCatching {
                val s = ensureSession()
                sync(s)
                val spec = SearchSpec.Builder()
                    .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)  // BM25F
                    .setListFilterQueryLanguageEnabled(true)  // enables the `term*` prefix operator
                    .setPropertyWeights(
                        TYPE,
                        // album often holds the *book* name for audio snips (title = chapter/track)
                        mapOf("title" to 3.0, "album" to 3.0, "artist" to 2.0, "text" to 1.0, "source" to 2.0, "url" to 1.0),
                    )
                    .setResultCountPerPage(100)
                    .build()
                val results = s.search(match, spec)
                results.nextPageAsync.await().map { it.genericDocument.id.toLong() }
            }.getOrElse { Log.w(TAG, "search failed for '$query' — degrading to no matches", it); emptyList() }
        }
    }

    private suspend fun ensureSession(): AppSearchSession {
        session?.let { return it }
        val s = LocalStorage.createSearchSessionAsync(
            LocalStorage.SearchContext.Builder(appContext, DB).build()
        ).await()
        val schema = AppSearchSchema.Builder(TYPE)
            .addProperty(stringProp("text"))
            .addProperty(stringProp("title"))
            .addProperty(stringProp("album"))
            .addProperty(stringProp("artist"))
            .addProperty(stringProp("source"))
            .addProperty(stringProp("url"))
            .build()
        s.setSchemaAsync(SetSchemaRequest.Builder().addSchemas(schema).setForceOverride(true).build()).await()
        syncedVersion = -1; syncedIds = emptySet()   // schema (re)created → force a full re-put
        session = s
        return s
    }

    private fun stringProp(name: String) =
        AppSearchSchema.StringPropertyConfig.Builder(name)
            .setCardinality(AppSearchSchema.StringPropertyConfig.CARDINALITY_OPTIONAL)
            .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
            .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
            .build()

    /** Mirror [SnipStore] into the index if it changed since the last sync (upsert + prune). */
    private suspend fun sync(s: AppSearchSession) {
        if (syncedVersion == SnipStore.version) return
        val snaps = SnipStore.snips.toList()
        val docs = snaps.map { sn ->
            GenericDocument.Builder<GenericDocument.Builder<*>>(NAMESPACE, sn.id.toString(), TYPE)
                .setPropertyString("text", sn.text)
                .setPropertyString("title", sn.metadata["title"] ?: "")
                .setPropertyString("album", sn.metadata["album"] ?: "")
                .setPropertyString("artist", sn.metadata["artist"] ?: "")
                .setPropertyString("source", sn.source)
                .setPropertyString("url", sn.metadata["url"] ?: "")
                .build()
        }
        if (docs.isNotEmpty()) {
            s.putAsync(PutDocumentsRequest.Builder().addGenericDocuments(docs).build()).await()
        }
        val currentIds = snaps.map { it.id }.toSet()
        val removed = syncedIds - currentIds
        if (removed.isNotEmpty()) {
            s.removeAsync(
                RemoveByDocumentIdRequest.Builder(NAMESPACE).addIds(removed.map { it.toString() }).build()
            ).await()
        }
        syncedIds = currentIds
        syncedVersion = SnipStore.version
    }

    /** Alphanumeric tokens AND-ed; final token as a prefix. Stripping to letters/digits also
     *  neutralises AppSearch query operators, so nothing can be injected. `null` = no real tokens. */
    private fun buildMatch(query: String): String? {
        val tokens = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return tokens.mapIndexed { i, t -> if (i == tokens.lastIndex) "$t*" else t }.joinToString(" ")
    }
}
