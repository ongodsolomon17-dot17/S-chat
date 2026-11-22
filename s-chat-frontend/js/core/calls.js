// WebRTC voice/video calls over the existing WebSocket signalling (call_invite / call_accept / webrtc_offer ...).
import { api } from "./api.js";
import { state, bus } from "./state.js";
import { send } from "./ws.js";
import { toast, ICONS } from "./ui.js";
import { esc, avatar, fmtDuration } from "./util.js";

let pc = null, local = null, callId = null, peerId = null, peerName = "", type = "VOICE", isCaller = false;
let pendingIce = [], timeoutId = null, tickId = null, connectedAt = 0, overlay = null, ringing = false, iceCache = null;

async function iceServers() {
  if (iceCache && Date.now() < iceCache.exp - 30000) return iceCache.servers;
  const d = await api("/calls/ice-servers");
  iceCache = { servers: d.iceServers || [], exp: d.expiresAt ? Date.parse(d.expiresAt) : Date.now() + 300000 };
  return iceCache.servers;
}
const busy = () => !!callId;

function render(status, { incoming = false } = {}) {
  if (!overlay) { overlay = document.createElement("div"); overlay.className = "call-overlay"; overlay.setAttribute("role", "dialog"); overlay.setAttribute("aria-label", "Call"); document.body.appendChild(overlay); }
  const video = type === "VIDEO";
  const user = state.chats.get(peerId)?.user || { username: peerName };
  const muted = local && !local.getAudioTracks()[0]?.enabled;
  const camOff = local && video && !local.getVideoTracks()[0]?.enabled;
  overlay.className = "call-overlay" + (video && connectedAt ? " compact" : "");
  const prevRemote = overlay.querySelector(".remote"), prevLocal = overlay.querySelector("video.local");
  const remoteStream = prevRemote?.srcObject, localStream = prevLocal?.srcObject;
  overlay.innerHTML = `
    <div class="call-stage">
      ${video && !incoming ? `<video class="remote" autoplay playsinline></video><video class="local" autoplay playsinline muted></video>` : `<audio class="remote" autoplay></audio>`}
      <div class="call-info">${avatar(user, 112).s}<h2>${esc(peerName || user.username)}</h2><div id="call-status">${esc(status)}</div></div>
    </div>
    <div class="call-bar">
      ${incoming ? `<button class="call-btn end" data-act="reject" aria-label="Decline">${ICONS.phoneend}</button><button class="call-btn accept" data-act="accept" aria-label="Accept">${ICONS.phone}</button>` : `
        <button class="call-btn ${muted ? "off" : ""}" data-act="mute" aria-label="${muted ? "Unmute" : "Mute"}">${muted ? ICONS.micoff : ICONS.mic}</button>
        ${video ? `<button class="call-btn ${camOff ? "off" : ""}" data-act="cam" aria-label="Toggle camera">${camOff ? ICONS.videooff : ICONS.video}</button>` : ""}
        <button class="call-btn end" data-act="end" aria-label="End call">${ICONS.phoneend}</button>`}
    </div>`;
  const r = overlay.querySelector(".remote"), l = overlay.querySelector("video.local");
  if (r && remoteStream) r.srcObject = remoteStream;
  if (l) l.srcObject = localStream || local;
  overlay.onclick = (e) => {
    const b = e.target.closest("[data-act]"); if (!b) return;
    ({ accept: accept, reject: reject, end: end, mute: toggleMute, cam: toggleCam })[b.dataset.act]?.();
  };
}
const setStatus = (t) => { const el = overlay?.querySelector("#call-status"); if (el) el.textContent = t; };

async function media() {
  if (!navigator.mediaDevices?.getUserMedia) throw new Error("Calls need a secure (HTTPS) connection and a supported browser.");
  try { local = await navigator.mediaDevices.getUserMedia({ audio: true, video: type === "VIDEO" ? { facingMode: "user" } : false }); }
  catch { throw new Error("Allow microphone" + (type === "VIDEO" ? " and camera" : "") + " access to make calls."); }
}
async function peer() {
  pc = new RTCPeerConnection({ iceServers: await iceServers(), iceCandidatePoolSize: 4 });
  local.getTracks().forEach((t) => pc.addTrack(t, local));
  pc.onicecandidate = (e) => { if (e.candidate && callId) send({ type: "ice_candidate", callId, candidate: e.candidate }); };
  pc.ontrack = (e) => {
    const r = overlay?.querySelector(".remote"); if (!r) return;
    r.srcObject = e.streams[0];
    r.play?.().catch(() => {});
  };
  pc.onconnectionstatechange = () => {
    if (pc?.connectionState === "connected") { connectedAt = Date.now(); if (type === "VIDEO") overlay?.classList.add("compact"); clearTimeout(timeoutId); tickId = setInterval(() => setStatus(fmtDuration((Date.now() - connectedAt) / 1000)), 1000); setStatus("Connected"); }
    if (pc?.connectionState === "failed") { toast("The call connection failed.", "error"); end(); }
  };
}

export async function start(friendId, name, callType) {
  if (busy()) return toast("You are already in a call.", "error");
  if (!state.online.has(friendId)) return toast(`${name} is offline right now.`, "error");
  try {
    peerId = friendId; peerName = name; type = callType; isCaller = true;
    await media(); await peer(); render("Calling…");
    if (!send({ type: "call_invite", to: friendId, callType, fromName: state.me?.username })) { cleanup(); return toast("Not connected. Try again in a moment.", "error"); }
    callId = "pending"; // replaced by call_started
    timeoutId = setTimeout(() => { if (callId) { toast("No answer.", "error"); end(); } }, 45000);
  } catch (e) { cleanup(); toast(e.message || "Could not start the call.", "error"); }
}
async function accept() {
  try { await media(); await peer(); ringing = false; render("Connecting…"); send({ type: "call_accept", callId }); }
  catch (e) { toast(e.message, "error"); reject(); }
}
function reject() { if (callId && callId !== "pending") send({ type: "call_reject", callId }); cleanup(); }
function end() { if (callId && callId !== "pending") send({ type: "call_end", callId }); cleanup(); }
function toggleMute() { const t = local?.getAudioTracks()[0]; if (t) { t.enabled = !t.enabled; render(overlay.querySelector("#call-status")?.textContent || ""); } }
function toggleCam() { const t = local?.getVideoTracks()[0]; if (t) { t.enabled = !t.enabled; render(overlay.querySelector("#call-status")?.textContent || ""); } }

function cleanup() {
  clearTimeout(timeoutId); clearInterval(tickId);
  try { pc?.close(); } catch {}
  local?.getTracks().forEach((t) => t.stop());
  pc = null; local = null; callId = null; peerId = null; pendingIce = []; ringing = false; connectedAt = 0;
  overlay?.remove(); overlay = null; bus.emit("calls:changed");
}
async function flushIce() { for (const c of pendingIce.splice(0)) { try { await pc.addIceCandidate(new RTCIceCandidate(c)); } catch {} } }

export function handleFrame(f) {
  if (f.type === "call_started") { callId = f.callId; peerId = f.to; return; }
  if (f.type === "call_invite") {
    if (busy()) { send({ type: "call_reject", callId: f.callId }); return; }
    callId = f.callId; peerId = f.from; peerName = f.fromName || "Friend"; type = f.callType === "VIDEO" ? "VIDEO" : "VOICE"; isCaller = false; ringing = true;
    render(`Incoming ${type.toLowerCase()} call`, { incoming: true });
    return;
  }
  if (!callId || f.callId !== callId) return;
  if (f.type === "call_accept" && isCaller) {
    clearTimeout(timeoutId); setStatus("Connecting…");
    pc.createOffer().then((o) => pc.setLocalDescription(o)).then(() => send({ type: "webrtc_offer", callId, description: pc.localDescription })).catch(() => { toast("Could not establish the call.", "error"); end(); });
  } else if (f.type === "webrtc_offer") {
    pc.setRemoteDescription(new RTCSessionDescription(f.description)).then(flushIce).then(() => pc.createAnswer()).then((a) => pc.setLocalDescription(a)).then(() => send({ type: "webrtc_answer", callId, description: pc.localDescription })).catch(end);
  } else if (f.type === "webrtc_answer") {
    pc.setRemoteDescription(new RTCSessionDescription(f.description)).then(flushIce).catch(end);
  } else if (f.type === "ice_candidate" && f.candidate) {
    if (pc?.remoteDescription) pc.addIceCandidate(new RTCIceCandidate(f.candidate)).catch(() => {}); else pendingIce.push(f.candidate);
  } else if (f.type === "call_reject") { toast("Call declined.", "error"); cleanup(); }
  else if (f.type === "call_end") { if (ringing) toast("Missed call.", "error"); cleanup(); }
}
export const callHistory = () => api("/calls/history?size=50");
