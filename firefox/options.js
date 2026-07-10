// Smriti addon settings — chunk size for bulk sends (stored in browser.storage.local).
const DEFAULT_CHUNK = 50;
const input = document.getElementById("chunk");
const status = document.getElementById("status");

browser.storage.local.get("chunkSize").then(function (r) {
  input.value = parseInt(r.chunkSize, 10) || DEFAULT_CHUNK;
});

document.getElementById("save").addEventListener("click", function () {
  const v = Math.max(1, Math.min(200, parseInt(input.value, 10) || DEFAULT_CHUNK));
  input.value = v;
  browser.storage.local.set({ chunkSize: v }).then(function () {
    status.textContent = "Saved ✓";
    setTimeout(function () { status.textContent = ""; }, 1500);
  });
});
