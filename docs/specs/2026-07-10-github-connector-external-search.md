# GitHub connector & external search — design (android/v0.6.0)

## Goal

Search *beyond* your captured smarans — over your own GitHub-synced knowledge (primarily a
**Logseq** markdown graph, but any repo) — without copying that content into Smriti. External hits
are **found, not owned**: they surface in search results and link out to their source; they never
enter the local `smarans.jsonl` or the Drive sync.

This extends v0.5.0's search. It is **opt-in, read-only, and degrades gracefully**: with no GitHub
connection, nothing about the app changes.

## Why GitHub code search (recap of the decision)

We *delegate* external search to whoever already indexed the content rather than indexing large
external corpora ourselves. GitHub already indexes the repos you sync your graph to, so we query
its **code-search API**. (On a future macOS client, the same `SearchSource` seam can instead hit
Logseq's local HTTP API — the interface is the same; only the backend differs.)

Known constraints of `GET /search/code` (shape the UX below):
- **10 requests/minute** (its own stricter bucket) → external search is **on-demand**, never per-keystroke.
- **Default branch only**; files **< 384 KB** (Logseq pages are tiny — fine).
- **Token-match, not BM25** → external results are "find," not relevance-ranked like local.
- **Indexing lag** (a note pushed minutes ago may not be searchable yet) and occasional private-repo flakiness.
- Requires an authenticated token with access to the repos.

## Architecture — the `SearchSource` seam

v0.5.0 deliberately deferred a multi-source interface "until there's a second source to shape it."
Now there is, so we formalize it:

```
interface SearchSource {
    val id: String            // "local", "github:owner/repo"
    val label: String         // "Logseq", repo name — shown as the card's source badge
    suspend fun search(query: String): List<ExternalHit>   // or local ids for the local source
}
```

- **LocalSource** — wraps the existing AppSearch index; instant, BM25-ranked, editable results.
- **GitHubSource** — one instance covers all selected repos; queries the code-search API on demand.

`ExternalHit` = `{ sourceLabel, title/path, snippet, url }` — enough to render a read-only card that
links out. External hits are transient (from the API), never persisted.

## Connections (Settings)

A **"Connections"** section, distinguishing the two *kinds* of connection (they are not the same
thing — one syncs your data, one is a read-only search source):

- **Sync** — Google Drive (existing; where your smarans live).
- **Search sources** — GitHub (new).

**GitHub connect flow:**
1. Paste a **personal access token** (needs repo read scope).
2. **Verify** — `GET /user` confirms the token; show the account name.
3. **Pick repos** — `GET /user/repos` (paginated) → user selects **N** repos to search.
4. **Confirm** — store token + selected repo list.

**Token security (non-negotiable):**
- Encrypted with an **Android Keystore AES/GCM key** (Jetpack Security's `EncryptedSharedPreferences`
  is deprecated, so we use the Keystore directly — no dependency), never logged.
- **Never** written to `smarans.jsonl` or synced to Drive — it stays device-local.
- "Disconnect" clears the token + repo selection.

## Search UI

Local search is unchanged (instant, BM25, as-you-type). External is layered on:

- When GitHub is connected **and** the query is non-empty, a footer appears:
  **`🌐 more smarans in <source> →`**. No connection → no footer.
- **Tap** → one code-search request across all selected repos
  (`q=<terms> repo:a/b repo:c/d …` — multiple `repo:` qualifiers OR in a single request, so N repos
  cost **one** request and stay under the rate limit). `Accept: application/vnd.github.text-match+json`
  for snippet highlights.
- Results **append below** the local results as **smaran-style cards** — the **source badge**
  (e.g. "Logseq") is what marks them external. We do **not** interleave them into the local BM25
  order (different ranking + timing; merging would be dishonest and would reorder as async lands).

**External cards are a read-only card variant:**
- **Tap → opens the source** (the GitHub file URL now; the real Logseq page later via macOS) — *not*
  the editable detail dialog.
- **No swipe-to-delete, no edit** (not owned).
- A small **↗** + source badge is the only visual difference from an owned smaran card.

Mental model:
```
[your smarans]              instant · editable · swipe-delete · BM25-ranked
🌐 more smarans in Logseq → tap to fetch
[external cards]            source="Logseq" ↗ · read-only · appended · links out
```

## Errors / degradation

- No token / not connected → footer absent; core app unaffected.
- API error or rate-limit → inline message under the footer ("Couldn't reach GitHub — try again");
  never crashes, never blocks local search.
- Empty external result → "No matches in <source>."

## Out of scope (MVP)

- **"Save to Smriti"** on an external hit (pull one in as a real *owned* smaran) — nice future
  nicety, the one case an external match becomes a stored smaran. Not now.
- **macOS Logseq local-API source** — same seam, later, with the macOS client.
- **Non-GitHub sources** — the interface allows them; none built yet.

## Verification (on the Pixel, per CLAUDE.md)

- Connect flow: bad token rejected; good token lists repos; selection persists across restart.
- Token stored encrypted; confirm it is **absent** from `smarans.jsonl` and the Drive file.
- Search: footer fires one request for N repos; external cards render with source badge + ↗; tap
  opens the source; local search stays instant and unaffected.
- Disconnect clears token + repos; footer disappears.
