package com.example.feature.drop

object DropWebPortalHtml {

    fun render(deviceName: String): String {
        val safeName = deviceName
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>A-Kit Drop — $safeName</title>
<style>
  :root {
    --bg: #05070a;
    --surface: #0e131b;
    --surface-hi: #161e2a;
    --border: #243043;
    --text: #f1f5f9;
    --muted: #94a3b8;
    --accent: #5ce1e6;
    --mint: #4ade80;
    --amber: #fbbf24;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    background: radial-gradient(circle at 90% 0%, rgba(92,225,230,0.12), transparent 42%), var(--bg);
    color: var(--text);
    min-height: 100vh;
    padding: 20px 16px 48px;
  }
  .wrap { max-width: 760px; margin: 0 auto; display: flex; flex-direction: column; gap: 18px; }
  .header {
    display: flex; align-items: center; justify-content: space-between; gap: 12px;
    background: var(--surface); border: 1px solid var(--border); border-radius: 24px; padding: 18px 20px;
  }
  .brand { display: flex; flex-direction: column; gap: 4px; }
  .brand h1 { font-size: 20px; letter-spacing: -0.3px; }
  .brand p { font-size: 13px; color: var(--muted); }
  .pill {
    display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; border-radius: 999px;
    background: rgba(74, 222, 128, 0.14); border: 1px solid rgba(74, 222, 128, 0.35);
    color: var(--mint); font-size: 11px; font-weight: 700; letter-spacing: 0.6px;
  }
  .dot { width: 7px; height: 7px; border-radius: 50%; background: var(--mint); }
  .card {
    background: var(--surface); border: 1px solid var(--border); border-radius: 24px;
    padding: 18px; display: flex; flex-direction: column; gap: 14px;
  }
  .card-title { display: flex; align-items: center; justify-content: space-between; }
  .card-title h2 { font-size: 16px; font-weight: 700; }
  .badge { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11px; color: var(--muted); }
  textarea {
    width: 100%; min-height: 86px; resize: vertical; border-radius: 16px;
    border: 1px solid var(--border); background: var(--surface-hi); color: var(--text);
    padding: 12px 14px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px;
    outline: none;
  }
  textarea:focus { border-color: var(--accent); }
  .btn-row { display: flex; gap: 10px; flex-wrap: wrap; }
  button, .btn-link {
    border: none; border-radius: 14px; padding: 11px 16px; font-size: 13px; font-weight: 700;
    cursor: pointer; text-decoration: none; display: inline-flex; align-items: center; justify-content: center; gap: 6px;
    transition: opacity .15s, transform .1s;
  }
  button:active, .btn-link:active { transform: scale(0.98); }
  .btn-primary { background: var(--accent); color: #041d20; }
  .btn-secondary { background: var(--surface-hi); color: var(--text); border: 1px solid var(--border); }
  .dropzone {
    border: 1.5px dashed rgba(92,225,230,0.45); border-radius: 18px; padding: 24px 16px;
    text-align: center; background: rgba(92,225,230,0.05); cursor: pointer;
  }
  .dropzone.drag { background: rgba(92,225,230,0.14); border-color: var(--accent); }
  .dropzone p { font-size: 14px; font-weight: 600; margin-bottom: 4px; }
  .dropzone span { font-size: 12px; color: var(--muted); }
  .progress-wrap { display: none; flex-direction: column; gap: 6px; }
  .progress-bar { width: 100%; height: 8px; border-radius: 999px; background: var(--surface-hi); overflow: hidden; }
  .progress-fill { height: 100%; width: 0%; background: linear-gradient(90deg, var(--accent), var(--mint)); transition: width .15s; }
  .list { display: flex; flex-direction: column; gap: 10px; }
  .item {
    background: var(--surface-hi); border: 1px solid var(--border); border-radius: 16px;
    padding: 12px 14px; display: flex; flex-direction: column; gap: 8px;
  }
  .item-top { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
  .item-meta { font-size: 11px; color: var(--muted); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
  .snippet-body {
    white-space: pre-wrap; word-break: break-word; font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 13px; color: var(--text); background: rgba(0,0,0,0.28); padding: 10px; border-radius: 12px;
  }
  .file-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .file-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
  .thumb { width: 46px; height: 46px; border-radius: 10px; object-fit: cover; border: 1px solid var(--border); flex-shrink: 0; }
  .file-name { font-size: 13.5px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .empty { font-size: 13px; color: var(--muted); text-align: center; padding: 12px 0; }
  .toast {
    position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%);
    background: #10262b; border: 1px solid var(--accent); color: var(--accent);
    padding: 10px 16px; border-radius: 999px; font-size: 13px; font-weight: 600; display: none; z-index: 99;
  }
</style>
</head>
<body>
<div class="wrap">
  <header class="header">
    <div class="brand">
      <h1>A-Kit Local Drop</h1>
      <p>Connected to $safeName • 100% Offline LAN</p>
    </div>
    <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;justify-content:flex-end;">
      <a id="openInAppBtn" class="btn-link btn-secondary" style="padding:7px 12px;font-size:11px;border-radius:999px;" href="#">Open in A-Kit App</a>
      <div class="pill"><span class="dot"></span>LOCAL P2P</div>
    </div>
  </header>

  <section class="card">
    <div class="card-title">
      <h2>Quick Text &amp; Code Snippet</h2>
      <span class="badge">LIVE SYNC</span>
    </div>
    <textarea id="snippetInput" placeholder="Paste text, link, or code snippet to send instantly to $safeName..."></textarea>
    <div class="btn-row">
      <button class="btn-primary" id="sendSnippetBtn">Send to Phone</button>
    </div>
    <div id="snippetsList" class="list"></div>
  </section>

  <section class="card">
    <div class="card-title">
      <h2>Upload Media &amp; Files</h2>
      <span class="badge">SAVES TO DOWNLOADS/A-KIT</span>
    </div>
    <input type="file" id="fileInput" multiple hidden />
    <div class="dropzone" id="dropzone">
      <p>Tap to Choose Files or Drag &amp; Drop Here</p>
      <span>Images, screenshots, PDFs, or any file — full local Wi-Fi speed</span>
    </div>
    <div class="progress-wrap" id="progressWrap">
      <span class="badge" id="progressLabel">Uploading...</span>
      <div class="progress-bar"><div class="progress-fill" id="progressFill"></div></div>
    </div>
  </section>

  <section class="card">
    <div class="card-title">
      <h2>Shared Media &amp; Files</h2>
      <span class="badge" id="filesCountBadge">0 FILES</span>
    </div>
    <div id="filesList" class="list"></div>
  </section>
</div>
<div class="toast" id="toast"></div>

<script>
(function() {
  const snippetInput = document.getElementById('snippetInput');
  const sendSnippetBtn = document.getElementById('sendSnippetBtn');
  const snippetsList = document.getElementById('snippetsList');
  const filesList = document.getElementById('filesList');
  const filesCountBadge = document.getElementById('filesCountBadge');
  const fileInput = document.getElementById('fileInput');
  const dropzone = document.getElementById('dropzone');
  const progressWrap = document.getElementById('progressWrap');
  const progressFill = document.getElementById('progressFill');
  const progressLabel = document.getElementById('progressLabel');
  const toastEl = document.getElementById('toast');
  const openInAppBtn = document.getElementById('openInAppBtn');
  if (openInAppBtn) {
    openInAppBtn.href = 'akit://drop?url=' + encodeURIComponent(window.location.origin);
  }

  function showToast(msg) {
    toastEl.textContent = msg;
    toastEl.style.display = 'block';
    clearTimeout(window.__akitToastTimer);
    window.__akitToastTimer = setTimeout(() => { toastEl.style.display = 'none'; }, 2400);
  }

  function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text).then(() => showToast('Copied to clipboard'));
    } else {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); showToast('Copied to clipboard'); } catch (_) {}
      document.body.removeChild(ta);
    }
  }

  async function sendSnippet() {
    const text = snippetInput.value.trim();
    if (!text) return;
    sendSnippetBtn.disabled = true;
    try {
      const res = await fetch('/api/snippets', {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain; charset=utf-8' },
        body: text
      });
      if (res.ok) {
        snippetInput.value = '';
        showToast('Sent to phone');
        fetchState();
      }
    } finally {
      sendSnippetBtn.disabled = false;
    }
  }

  sendSnippetBtn.addEventListener('click', sendSnippet);

  dropzone.addEventListener('click', () => fileInput.click());
  dropzone.addEventListener('dragover', (e) => { e.preventDefault(); dropzone.classList.add('drag'); });
  dropzone.addEventListener('dragleave', () => dropzone.classList.remove('drag'));
  dropzone.addEventListener('drop', (e) => {
    e.preventDefault();
    dropzone.classList.remove('drag');
    if (e.dataTransfer && e.dataTransfer.files.length) uploadFiles(e.dataTransfer.files);
  });
  fileInput.addEventListener('change', () => {
    if (fileInput.files.length) uploadFiles(fileInput.files);
    fileInput.value = '';
  });

  async function uploadFiles(fileList) {
    let okCount = 0;
    for (let i = 0; i < fileList.length; i++) {
      const ok = await uploadSingleFile(fileList[i], i + 1, fileList.length);
      if (ok) okCount++;
    }
    progressWrap.style.display = 'none';
    if (okCount === fileList.length) {
      showToast('Saved ' + okCount + ' file(s) to Downloads/A-Kit');
    } else if (okCount > 0) {
      showToast('Saved ' + okCount + '/' + fileList.length + ' file(s)');
    } else {
      showToast('Upload failed — check connection and retry');
    }
    fetchState();
  }

  function uploadSingleFile(file, idx, total) {
    return new Promise((resolve) => {
      progressWrap.style.display = 'flex';
      progressFill.style.width = '0%';
      progressLabel.textContent = 'Uploading (' + idx + '/' + total + '): ' + file.name;
      const xhr = new XMLHttpRequest();
      xhr.open('POST', '/api/upload');
      xhr.timeout = 0;
      xhr.setRequestHeader('X-File-Name', encodeURIComponent(file.name));
      xhr.setRequestHeader('X-Mime-Type', file.type || 'application/octet-stream');
      xhr.upload.onprogress = (ev) => {
        if (ev.lengthComputable) {
          const pct = Math.round((ev.loaded / ev.total) * 100);
          progressFill.style.width = pct + '%';
          progressLabel.textContent = file.name + ' (' + pct + '%)';
        }
      };
      xhr.onload = () => resolve(xhr.status >= 200 && xhr.status < 300);
      xhr.onerror = () => resolve(false);
      xhr.onabort = () => resolve(false);
      xhr.send(file);
    });
  }

  function renderSnippets(items) {
    snippetsList.innerHTML = '';
    if (!items.length) {
      const empty = document.createElement('p');
      empty.className = 'empty';
      empty.textContent = 'No snippets yet. Send text from this browser or from A-Kit on your phone.';
      snippetsList.appendChild(empty);
      return;
    }
    items.forEach((s) => {
      const card = document.createElement('div');
      card.className = 'item';

      const top = document.createElement('div');
      top.className = 'item-top';

      const meta = document.createElement('span');
      meta.className = 'item-meta';
      meta.textContent = s.sender + ' • ' + s.time;

      const copyBtn = document.createElement('button');
      copyBtn.className = 'btn-secondary';
      copyBtn.textContent = 'Copy';
      copyBtn.addEventListener('click', () => copyText(s.text));

      top.appendChild(meta);
      top.appendChild(copyBtn);

      const body = document.createElement('div');
      body.className = 'snippet-body';
      body.textContent = s.text;

      card.appendChild(top);
      card.appendChild(body);
      snippetsList.appendChild(card);
    });
  }

  function renderFiles(files) {
    filesCountBadge.textContent = files.length + (files.length === 1 ? ' FILE' : ' FILES');
    filesList.innerHTML = '';
    if (!files.length) {
      const empty = document.createElement('p');
      empty.className = 'empty';
      empty.textContent = 'No files shared in this session yet.';
      filesList.appendChild(empty);
      return;
    }
    files.forEach((f) => {
      const card = document.createElement('div');
      card.className = 'item';

      const row = document.createElement('div');
      row.className = 'file-row';

      const left = document.createElement('div');
      left.className = 'file-left';

      if (f.isImage) {
        const img = document.createElement('img');
        img.className = 'thumb';
        img.src = '/api/files/' + encodeURIComponent(f.id);
        img.alt = f.fileName;
        left.appendChild(img);
      }

      const info = document.createElement('div');
      info.style.minWidth = '0';

      const name = document.createElement('div');
      name.className = 'file-name';
      name.textContent = f.fileName;

      const meta = document.createElement('div');
      meta.className = 'item-meta';
      meta.textContent = f.sizeLabel + ' • ' + f.sourceLabel;

      info.appendChild(name);
      info.appendChild(meta);
      left.appendChild(info);

      const dl = document.createElement('a');
      dl.className = 'btn-link btn-primary';
      dl.href = '/api/files/' + encodeURIComponent(f.id);
      dl.setAttribute('download', f.fileName);
      dl.textContent = 'Download';

      row.appendChild(left);
      row.appendChild(dl);
      card.appendChild(row);
      filesList.appendChild(card);
    });
  }

  async function fetchState() {
    try {
      const res = await fetch('/api/state', { cache: 'no-store' });
      if (!res.ok) return;
      const data = await res.json();
      renderSnippets(data.snippets || []);
      renderFiles(data.files || []);
    } catch (_) {}
  }

  fetchState();
  setInterval(fetchState, 2500);
})();
</script>
</body>
</html>
        """.trimIndent()
    }
}
