// Apply the saved theme before first paint to avoid a flash. Lives in its own file because the
// Content-Security-Policy (script-src 'self') blocks inline <script> blocks.
try { var t = localStorage.getItem('schat_theme'); if (t) document.documentElement.setAttribute('data-theme', t); } catch (e) {}
