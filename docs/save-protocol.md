# Smriti Save Protocol (`smriti://save`)

How any app or link hands text into Smriti as a note (*smaran*) on Android, via a deep link.
No network, no API — a plain Android intent. The Firefox addon is the first caller, but it's
**not web-specific**: anything that can open a URI can use it.

Added in **android/v0.1.0** (single note) and **android/v0.2.0** (bulk `items`).

## Scheme

```
smriti://save?<params>
```

Registered by `SaveLinkActivity` (a no-UI activity) via a `VIEW` + `BROWSABLE` intent-filter
for scheme `smriti`, host `save`. Firing it saves the note(s) locally and pushes them to the
Drive folder (one debounced write). Nothing is transcribed — these are `WEB` smarans, born
`done`.

## Single note

```
smriti://save?text=<text>&url=<url>&title=<title>&source=<source>
```

| param    | required | meaning |
|----------|----------|---------|
| `text`   | **yes**  | the note body (the highlighted / selected text) |
| `url`    | no       | source page URL → stored in `metadata.url` |
| `title`  | no       | source page title → stored in `metadata.title` |
| `source` | no       | short display label. Omitted → the URL's host (e.g. `en.wikipedia.org`) → else `"Web"` |

All values **must be percent-encoded** (`encodeURIComponent`). If `text` is empty/absent,
nothing is saved.

Example (adb — single-quote the URI so the device shell keeps the `&`):

```bash
adb shell am start -a android.intent.action.VIEW \
  -d 'smriti://save?text=Hello%20world&url=https%3A%2F%2Fexample.com&title=Example&source=Firefox'
```

## Bulk (many notes, one intent)

```
smriti://save?items=<url-encoded JSON array>
```

Each element has the same fields as the single form:

```json
[
  { "text": "…", "url": "…", "title": "…", "source": "…" },
  { "text": "…", "url": "…" },
  { "text": "…" }
]
```

- Each element → one `WEB` smaran, with **per-item** provenance (highlights from different
  pages batch together fine).
- One debounced Drive push, one "Saved N to Smriti" toast.
- `text` is required per item; items with empty `text` are skipped.
- The whole JSON array is the `items` value and **must be percent-encoded**.
- If both `items` and `text` are present, `items` wins.

## Size limits

A `smriti://` URI is an Android intent, so:

- **Hard ceiling:** the intent must fit the Binder transaction buffer (~**1 MB** total).
- **Practical safe zone:** keep the whole URI under **a few KB** — some browsers silently
  truncate a long custom-scheme URL before it ever reaches the app.

For large batches the **caller** should split into several `?items=` sends (e.g. ≤ 20 items
each). Do **not** use the clipboard. Chunking stays entirely on the caller side and is
invisible to the user.

## Result

Each saved note is a smaran of `type: "web"`, `status: "done"`, with `text`, `source`, and a
`metadata` object holding `url` / `title` when provided (full record schema in
[../ROADMAP.md](../ROADMAP.md)). It appears in the app's Recent list and syncs to
`…/smriti/snips/<device>.jsonl`.

## Caller sketch (Firefox addon)

1. On a selection, stash `{ text: selection, url: tab.url, title: tab.title }` in the addon's
   own storage (a "basket") and show a badge count.
2. On **Send N to Smriti**, build `smriti://save?items=<encoded JSON>` and open it
   (`browser.tabs.create({ url })` / navigation). Chunk if the batch is large.
3. Clear the basket.
