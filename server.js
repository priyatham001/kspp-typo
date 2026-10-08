const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3000;

const APK_PATHS = [
  path.join(__dirname, 'app/build/outputs/apk/debug/app-debug.apk'),
  path.join(__dirname, '.build-outputs/app-debug.apk')
];

function getApkPath() {
  for (const p of APK_PATHS) {
    if (fs.existsSync(p)) return p;
  }
  return null;
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = url.pathname;

  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  // Health checks
  if (pathname === '/health' || pathname === '/api/health') {
    const apkPath = getApkPath();
    const stats = apkPath ? fs.statSync(apkPath) : null;
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      app: 'replica_kspp',
      version: '1.0.0',
      apk_ready: !!apkPath,
      apk_size_bytes: stats ? stats.size : 0,
      timestamp: new Date().toISOString()
    }));
    return;
  }

  // APK Download
  if (pathname === '/download/app-debug.apk' || pathname === '/app-debug.apk') {
    const apkPath = getApkPath();
    if (!apkPath) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('APK not found. Please compile the app first.');
      return;
    }

    const stat = fs.statSync(apkPath);
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Disposition': 'attachment; filename="replica_kspp-debug.apk"',
      'Content-Length': stat.size
    });

    const stream = fs.createReadStream(apkPath);
    stream.pipe(res);
    return;
  }

  // Sphere Image Asset
  if (pathname === '/replica_sphere.jpg' || pathname === '/public/replica_sphere.jpg') {
    const imgPath = path.join(__dirname, 'public/replica_sphere.jpg');
    const fallbackPath = path.join(__dirname, 'app/src/main/res/drawable/replica_sphere.jpg');
    const target = fs.existsSync(imgPath) ? imgPath : (fs.existsSync(fallbackPath) ? fallbackPath : null);

    if (target) {
      res.writeHead(200, { 'Content-Type': 'image/jpeg', 'Cache-Control': 'public, max-age=86400' });
      fs.createReadStream(target).pipe(res);
      return;
    }
  }

  // Serve Main Web Page
  if (pathname === '/' || pathname === '/index.html') {
    const apkPath = getApkPath();
    let apkSizeMb = '0';
    if (apkPath) {
      try {
        const stats = fs.statSync(apkPath);
        apkSizeMb = (stats.size / (1024 * 1024)).toFixed(1);
      } catch (_) {}
    }

    const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>replica_kspp - I replicate keyboard</title>
  <meta name="description" content="replica_kspp - I replicate keyboard. Server-controlled Bluetooth HID keyboard and auto-typer with 8-hour access control.">
  <link rel="icon" href="/replica_sphere.jpg" type="image/jpeg">
  <style>
    :root {
      --bg: #090d16;
      --card-bg: #111827;
      --card-border: #1f293d;
      --primary: #3b82f6;
      --primary-hover: #2563eb;
      --text: #f3f4f6;
      --text-muted: #9ca3af;
      --success: #10b981;
      --warning: #f59e0b;
      --danger: #ef4444;
      --font-mono: 'Courier New', Courier, monospace;
    }

    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 24px 16px;
    }

    .container {
      max-width: 900px;
      width: 100%;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .header-card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 20px;
      padding: 32px 24px;
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.5);
      position: relative;
      overflow: hidden;
    }

    .header-card::before {
      content: '';
      position: absolute;
      top: -50px;
      left: 50%;
      transform: translateX(-50%);
      width: 280px;
      height: 280px;
      background: radial-gradient(circle, rgba(59, 130, 246, 0.15) 0%, transparent 70%);
      pointer-events: none;
    }

    .orb-wrapper {
      position: relative;
      width: 140px;
      height: 140px;
      margin-bottom: 20px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .orb-glow {
      position: absolute;
      width: 154px;
      height: 154px;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(59,130,246,0.5) 0%, rgba(99,102,241,0.2) 60%, transparent 80%);
      animation: pulse 3s ease-in-out infinite alternate;
    }

    .orb-img {
      width: 130px;
      height: 130px;
      border-radius: 50%;
      object-fit: cover;
      border: 2px solid rgba(59, 130, 246, 0.5);
      box-shadow: 0 0 30px rgba(59, 130, 246, 0.4);
      animation: float 4s ease-in-out infinite alternate;
      z-index: 1;
    }

    @keyframes float {
      0% { transform: translateY(-4px) scale(0.99); }
      100% { transform: translateY(6px) scale(1.02); }
    }

    @keyframes pulse {
      0% { transform: scale(0.95); opacity: 0.4; }
      100% { transform: scale(1.08); opacity: 0.8; }
    }

    h1 {
      font-size: 2.2rem;
      font-weight: 800;
      letter-spacing: 2px;
      color: #ffffff;
      margin-bottom: 6px;
    }

    .motto {
      font-family: var(--font-mono);
      font-size: 1.15rem;
      color: var(--primary);
      font-weight: 600;
      margin-bottom: 12px;
    }

    .desc {
      color: var(--text-muted);
      font-size: 0.95rem;
      max-width: 600px;
      line-height: 1.5;
      margin-bottom: 24px;
    }

    .badge-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      justify-content: center;
      margin-bottom: 24px;
    }

    .badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.1);
      padding: 6px 14px;
      border-radius: 20px;
      font-size: 0.8rem;
      font-weight: 600;
    }

    .badge.green { border-color: rgba(16, 185, 129, 0.4); color: var(--success); }
    .badge.blue { border-color: rgba(59, 130, 246, 0.4); color: var(--primary); }

    .btn-download {
      background: linear-gradient(135deg, #2563eb, #1d4ed8);
      color: white;
      text-decoration: none;
      padding: 14px 28px;
      border-radius: 12px;
      font-weight: 700;
      font-size: 1rem;
      display: inline-flex;
      align-items: center;
      gap: 10px;
      box-shadow: 0 4px 14px rgba(37, 99, 235, 0.4);
      transition: all 0.2s;
    }

    .btn-download:hover {
      background: linear-gradient(135deg, #1d4ed8, #1e40af);
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(37, 99, 235, 0.5);
    }

    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 16px;
    }

    .card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 16px;
      padding: 20px;
    }

    .card-title {
      font-size: 1.05rem;
      font-weight: 700;
      margin-bottom: 12px;
      display: flex;
      align-items: center;
      gap: 8px;
      color: #fff;
    }

    .card-body {
      font-size: 0.9rem;
      color: var(--text-muted);
      line-height: 1.5;
    }

    .simulator-box {
      margin-top: 14px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    textarea {
      width: 100%;
      height: 90px;
      background: #0b0f19;
      border: 1px solid var(--card-border);
      border-radius: 8px;
      padding: 10px;
      color: var(--text);
      font-family: var(--font-mono);
      font-size: 0.85rem;
      resize: vertical;
    }

    .control-row {
      display: flex;
      gap: 10px;
      align-items: center;
    }

    .btn-action {
      background: var(--primary);
      color: white;
      border: none;
      padding: 8px 16px;
      border-radius: 8px;
      font-weight: 600;
      cursor: pointer;
      font-size: 0.85rem;
    }

    .btn-action:hover { background: var(--primary-hover); }

    .terminal {
      background: #000;
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 8px;
      padding: 12px;
      font-family: var(--font-mono);
      font-size: 0.8rem;
      color: #34d399;
      min-height: 48px;
      word-break: break-all;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="header-card">
      <div class="orb-wrapper">
        <div class="orb-glow"></div>
        <img class="orb-img" src="/replica_sphere.jpg" alt="replica_kspp logo" onerror="this.style.display='none'">
      </div>

      <h1>replica_kspp</h1>
      <div class="motto">"I replicate keyboard"</div>
      <p class="desc">Server-Controlled Bluetooth HID Keyboard & Auto-Typer with 8-Hour Access Control, Super Admin Management, and Keystroke Transmission Engine.</p>

      <div class="badge-bar">
        <span class="badge green">● Service Status: ACTIVE</span>
        <span class="badge blue">8-Hour Access Window</span>
        <span class="badge">Bluetooth HID Standard</span>
        <span class="badge">APK Size: ${apkSizeMb} MB</span>
      </div>

      <a href="/download/app-debug.apk" class="btn-download">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
        Download replica_kspp APK
      </a>
    </div>

    <div class="grid">
      <div class="card">
        <div class="card-title">
          <span>⚡</span> Keystroke & Typing Engine
        </div>
        <div class="card-body">
          Emulates a full standard 101-key USB/Bluetooth HID Keyboard. Transforms arbitrary text and symbols into raw HID byte reports sent over standard RFCOMM Bluetooth HID profiles.
          <div class="simulator-box">
            <textarea id="simInput" placeholder="Enter text to simulate keystrokes...">Hello replica_kspp! Keystroke transmission ready.</textarea>
            <div class="control-row">
              <button class="btn-action" onclick="runSim()">Simulate Keystrokes</button>
              <span id="charCount" style="font-size: 0.8rem; color: var(--text-muted);">Chars: 47</span>
            </div>
            <div id="simTerminal" class="terminal">> Idle. Ready for keystroke transmission.</div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-title">
          <span>🔒</span> 8-Hour Access Control
        </div>
        <div class="card-body">
          <p><strong>Google Authentication:</strong> Real Google Sign-In with server-side identity validation.</p>
          <br>
          <p><strong>8-Hour Free Trial:</strong> Genuine new Google accounts automatically receive exactly 8 hours of free access. Expiration is enforced server-side.</p>
          <br>
          <p><strong>Super Admin Authority:</strong> Owners (<code>nani68629@gmail.com</code> / <code>pskcoll68629@gmail.com</code>) have permanent access and can grant 8h continuations, permanent access, or emergency wipe.</p>
        </div>
      </div>

      <div class="card">
        <div class="card-title">
          <span>⚙️</span> Service Modes
        </div>
        <div class="card-body">
          <p><strong>ACTIVE:</strong> Full operational mode for all approved accounts.</p>
          <br>
          <p><strong>MAINTENANCE:</strong> Displays <em>"REPLICA is currently under maintenance. Please try again later."</em> Admin panel remains accessible.</p>
          <br>
          <p><strong>DISABLED:</strong> Displays <em>"REPLICA service is currently unavailable."</em> All active typing sessions stop immediately.</p>
        </div>
      </div>

      <div class="card">
        <div class="card-title">
          <span>📝</span> Admin Comment Notes
        </div>
        <div class="card-body">
          Admins can attach operational notes and comments to any user profile (e.g. <em>"Approved for project testing"</em>). Notes appear in user details, management screens, and the user dashboard.
        </div>
      </div>
    </div>
  </div>

  <script>
    const simInput = document.getElementById('simInput');
    const charCount = document.getElementById('charCount');
    const simTerminal = document.getElementById('simTerminal');

    simInput.addEventListener('input', () => {
      charCount.textContent = 'Chars: ' + simInput.value.length;
    });

    let typingTimer = null;
    function runSim() {
      if (typingTimer) clearInterval(typingTimer);
      const text = simInput.value;
      if (!text) {
        simTerminal.textContent = '> Text is empty.';
        return;
      }

      simTerminal.textContent = '> Starting transmission...\\n';
      let idx = 0;
      typingTimer = setInterval(() => {
        if (idx >= text.length) {
          clearInterval(typingTimer);
          simTerminal.textContent += '\\n> [DONE] Transmitted ' + text.length + ' characters successfully.';
          return;
        }
        const char = text[idx++];
        simTerminal.textContent += char;
      }, 40);
    }
  </script>
</body>
</html>`;

    res.writeHead(200, {
      'Content-Type': 'text/html; charset=utf-8',
      'Content-Length': Buffer.byteLength(html)
    });
    res.end(html);
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('Not Found');
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[replica_kspp] Dev server listening on port ${PORT}`);
});
