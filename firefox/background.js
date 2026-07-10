// Smriti — minimal capture addon.
// Sends the selected text (+ page url/title) to the Smriti Android app via the
// `smriti://save` deep link. Protocol: ../docs/save-protocol.md

function saveToSmriti(text, url, title) {
  const t = (text || "").trim();
  if (!t) return;
  const deep =
    "smriti://save?text=" + encodeURIComponent(t) +
    "&url=" + encodeURIComponent(url || "") +
    "&title=" + encodeURIComponent(title || "") +
    "&source=Firefox";
  // Navigating to the custom scheme hands the intent to Android -> Smriti.
  browser.tabs.update({ url: deep });
}

// 1) Selection context menu — the natural "select text -> Save to Smriti" flow.
browser.contextMenus.create({
  id: "smriti-save",
  title: "Save to Smriti",
  contexts: ["selection"],
});
browser.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === "smriti-save") {
    saveToSmriti(info.selectionText, tab && tab.url, tab && tab.title);
  }
});

// 2) Toolbar/menu button fallback — reads the current selection itself
//    (useful if the selection context menu isn't available on this build).
browser.browserAction.onClicked.addListener(async (tab) => {
  let text = "";
  try {
    const r = await browser.tabs.executeScript(tab.id, {
      code: "(window.getSelection && window.getSelection().toString()) || ''",
    });
    text = r && r[0] ? r[0] : "";
  } catch (e) {
    // executeScript is blocked on privileged pages; ignore.
  }
  saveToSmriti(text, tab.url, tab.title);
});
