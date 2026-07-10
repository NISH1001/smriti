// Smriti — in-page capture basket for Firefox (works on Android, no menus needed).
//
// Highlight snippets as you read: ONE floating pill switches by context —
//   • text selected      -> "＋ Save to Smriti"  (adds it to the basket + yellow-highlights it)
//   • nothing selected    -> "⬆ Send N to Smriti" (fires the whole basket to the app)
// Send uses smriti://save?items=[…] (protocol: ../docs/save-protocol.md). Tap a yellow
// highlight to remove that snippet. Basket persists locally across pages; highlights are
// session-only (cleared on reload).
(function () {
  if (window.__smritiBasket) return;
  window.__smritiBasket = true;

  const Z = 2147483647;
  const pill = document.createElement("button");
  pill.style.cssText =
    "position:fixed;right:14px;bottom:24px;z-index:" + Z + ";padding:12px 18px;border:none;" +
    "border-radius:26px;font:600 15px system-ui,-apple-system,sans-serif;color:#0b1220;" +
    "box-shadow:0 3px 12px rgba(0,0,0,.4);cursor:pointer;display:none;";
  document.documentElement.appendChild(pill);

  let pending = "";          // last non-empty selection text
  let pendingRange = null;   // ...and its range, for the yellow highlight
  let clearTimer = null;     // debounce clearing `pending` so a tap-to-Save survives
  let count = 0;             // basket size (mirrored from storage for sync rendering)
  const shown = new Set();   // ids already highlighted on this page (avoid double-wrap)

  function render() {
    if (pending) {
      pill.textContent = "＋ Smaran";
      pill.style.background = "#89b4ff";
      pill.style.display = "block";
    } else if (count > 0) {
      pill.textContent = "⬆ Save " + count + " smaran";
      pill.style.background = "#a6e3a1";
      pill.style.display = "block";
    } else {
      pill.style.display = "none";
    }
  }

  function selText() {
    const s = window.getSelection && window.getSelection();
    return s ? String(s).trim() : "";
  }
  function selRange() {
    const s = window.getSelection && window.getSelection();
    return s && s.rangeCount ? s.getRangeAt(0).cloneRange() : null;
  }
  function onSel() {
    const t = selText();
    if (t) {
      pending = t;
      pendingRange = selRange();
      if (clearTimer) { clearTimeout(clearTimer); clearTimer = null; }
      render();
    } else if (pending) {
      // selection emptied — clear after a beat so tapping the pill (which clears it) still Saves
      if (clearTimer) clearTimeout(clearTimer);
      clearTimer = setTimeout(function () {
        pending = ""; pendingRange = null; clearTimer = null; render();
      }, 500);
    }
  }
  document.addEventListener("selectionchange", onSel);
  document.addEventListener("mouseup", onSel);
  document.addEventListener("touchend", onSel);

  async function getBasket() {
    const r = await browser.storage.local.get("basket");
    return Array.isArray(r.basket) ? r.basket : [];
  }
  async function setBasket(b) {
    await browser.storage.local.set({ basket: b });
    count = b.length;
    render();
  }

  function unwrap(mark) {
    const p = mark.parentNode;
    if (!p) return;
    while (mark.firstChild) p.insertBefore(mark.firstChild, mark);
    p.removeChild(mark);
    p.normalize();
  }
  // Yellow <mark> with dark text (readable on light OR dark pages). Tap to remove.
  function highlightRange(range, id) {
    if (shown.has(id)) return;
    const mark = document.createElement("mark");
    mark.className = "smriti-hl";
    mark.title = "Tap to remove from Smriti";
    mark.style.cssText =
      "background:#ffd54a;color:#1a1a1a;border-radius:2px;padding:0 1px;cursor:pointer;";
    mark.addEventListener("click", async function (ev) {
      ev.preventDefault();
      ev.stopPropagation();
      shown.delete(id);
      const b = (await getBasket()).filter(function (x) { return x.id !== id; });
      await setBasket(b);
      unwrap(mark);
    });
    try {
      range.surroundContents(mark);
      shown.add(id);
    } catch (_) {
      try { mark.appendChild(range.extractContents()); range.insertNode(mark); shown.add(id); } catch (__) {}
    }
  }

  pill.addEventListener("click", async function (e) {
    e.preventDefault();
    if (pending) {
      // ADD (highlight) the current selection to the basket
      const text = pending;
      const range = pendingRange;
      pending = "";
      pendingRange = null;
      if (clearTimer) { clearTimeout(clearTimer); clearTimer = null; }
      try { window.getSelection().removeAllRanges(); } catch (_) {}
      const b = await getBasket();
      if (b.some(function (x) { return x.text === text && x.url === location.href; })) {
        pill.textContent = "✓ Already saved"; // basket de-dup: same passage, same page
        setTimeout(render, 1000);
        return;
      }
      const id = Date.now() + "-" + Math.random().toString(36).slice(2, 8);
      if (range) highlightRange(range, id);
      b.push({ id: id, text: text, url: location.href, title: document.title, source: "Firefox" });
      await setBasket(b); // triggers render()
    } else if (count > 0) {
      // SEND: fire the whole basket
      const b = await getBasket();
      if (!b.length) return;
      const items = b.map(function (x) {
        return { text: x.text, url: x.url, title: x.title, source: x.source };
      });
      const deep = "smriti://save?items=" + encodeURIComponent(JSON.stringify(items));
      await setBasket([]);
      const a = document.createElement("a");
      a.href = deep;
      (document.body || document.documentElement).appendChild(a);
      a.click();
      a.remove();
    }
  });

  // Re-find a saved snippet's text in the current DOM -> a Range (text-quote anchoring).
  function findRange(needle) {
    if (!needle || !document.body) return null;
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: function (n) {
        const p = n.parentNode;
        if (!p) return NodeFilter.FILTER_REJECT;
        const tag = p.nodeName;
        if (tag === "SCRIPT" || tag === "STYLE" || tag === "NOSCRIPT") return NodeFilter.FILTER_REJECT;
        if (p.classList && p.classList.contains("smriti-hl")) return NodeFilter.FILTER_REJECT;
        return NodeFilter.FILTER_ACCEPT;
      },
    });
    const segs = [];
    let full = "";
    let node;
    while ((node = walker.nextNode())) {
      segs.push({ node: node, start: full.length });
      full += node.nodeValue;
    }
    const idx = full.indexOf(needle);
    if (idx < 0) return null;
    const end = idx + needle.length;
    let sNode = null, sOff = 0, eNode = null, eOff = 0;
    for (let i = 0; i < segs.length; i++) {
      const seg = segs[i];
      const segEnd = seg.start + seg.node.nodeValue.length;
      if (!sNode && idx >= seg.start && idx < segEnd) { sNode = seg.node; sOff = idx - seg.start; }
      if (!eNode && end > seg.start && end <= segEnd) { eNode = seg.node; eOff = end - seg.start; }
      if (sNode && eNode) break;
    }
    if (!sNode || !eNode) return null;
    try {
      const r = document.createRange();
      r.setStart(sNode, sOff);
      r.setEnd(eNode, eOff);
      return r;
    } catch (_) { return null; }
  }

  // On load: restore the count + re-highlight this page's saved snippets (basic text match).
  async function restoreHighlights() {
    const b = await getBasket();
    count = b.length;
    render();
    b.forEach(function (item) {
      if (item.url !== location.href || shown.has(item.id)) return;
      const r = findRange(item.text);
      if (r) highlightRange(r, item.id);
    });
  }
  restoreHighlights();
  setTimeout(restoreHighlights, 1200); // one retry for late-loading content
})();
