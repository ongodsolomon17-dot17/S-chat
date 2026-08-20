// S-Chat WebRTC calling layer.
// Signalling uses the existing authenticated WebSocket; audio/video uses WebRTC.
(function () {
  if (window.SChatCalls) return;

  const params = new URLSearchParams(window.location.search);
  const friendId = params.get("friend");
  const friendName = params.get("name") || "Friend";
  const currentUser = SChat.Auth.getUser();

  let initialized = false;
  let activeCallId = null;
  let activeCallType = null;
  let activePeerId = null;
  let activePeerName = "Friend";
  let isCaller = false;
  let pc = null;
  let localStream = null;
  let remoteStream = null;
  let callTimer = null;
  let callTimeout = null;
  let ending = false;
  let pendingIceCandidates = [];

  function ensureUi() {
    if (document.getElementById("schat-call-overlay")) return;
    const wrapper = document.createElement("div");
    wrapper.id = "schat-call-overlay";
    wrapper.innerHTML = `
      <div class="schat-call-backdrop"></div>
      <div class="schat-call-panel" role="dialog" aria-modal="true" aria-label="S-Chat call">
        <div class="schat-call-top">
          <div>
            <div class="schat-call-status" id="schat-call-status">Calling…</div>
            <div class="schat-call-name" id="schat-call-name">Friend</div>
          </div>
          <button type="button" class="schat-call-close" id="schat-call-minimize" aria-label="Minimize call">−</button>
        </div>
        <div class="schat-call-media">
          <video id="schat-call-remote-video" autoplay playsinline></video>
          <video id="schat-call-local-video" autoplay muted playsinline></video>
          <audio id="schat-call-remote-audio" autoplay></audio>
          <div class="schat-call-avatar" id="schat-call-avatar">👤</div>
        </div>
        <div class="schat-call-incoming" id="schat-call-incoming">
          <div class="schat-call-incoming-text">Incoming call</div>
          <div class="schat-call-incoming-actions">
            <button type="button" class="schat-call-action reject" id="schat-call-reject">Decline</button>
            <button type="button" class="schat-call-action accept" id="schat-call-accept">Accept</button>
          </div>
        </div>
        <div class="schat-call-controls" id="schat-call-controls">
          <button type="button" class="schat-call-control" id="schat-call-mute">🎙️<span>Mute</span></button>
          <button type="button" class="schat-call-control" id="schat-call-camera">📹<span>Camera</span></button>
          <button type="button" class="schat-call-control end" id="schat-call-end">☎<span>End</span></button>
        </div>
      </div>`;
    document.body.appendChild(wrapper);

    document.getElementById("schat-call-accept").addEventListener("click", acceptIncoming);
    document.getElementById("schat-call-reject").addEventListener("click", rejectIncoming);
    document.getElementById("schat-call-end").addEventListener("click", endCall);
    document.getElementById("schat-call-mute").addEventListener("click", toggleMute);
    document.getElementById("schat-call-camera").addEventListener("click", toggleCamera);
    document.getElementById("schat-call-minimize").addEventListener("click", () => {
      wrapper.classList.toggle("minimized");
    });
  }

  function showOverlay() {
    ensureUi();
    document.getElementById("schat-call-overlay").classList.add("show");
  }

  function hideOverlay() {
    const overlay = document.getElementById("schat-call-overlay");
    if (overlay) overlay.classList.remove("show", "minimized");
  }

  function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
  }

  function setMode(type) {
    activeCallType = type;
    const overlay = document.getElementById("schat-call-overlay");
    if (overlay) {
      overlay.classList.toggle("video-mode", type === "VIDEO");
      overlay.classList.toggle("voice-mode", type === "VOICE");
    }
  }

  function setIncoming(incoming) {
    document.getElementById("schat-call-incoming").style.display = incoming ? "block" : "none";
    document.getElementById("schat-call-controls").style.display = incoming ? "none" : "flex";
  }

  function startTimer() {
    clearInterval(callTimer);
    const started = Date.now();
    callTimer = setInterval(() => {
      const seconds = Math.floor((Date.now() - started) / 1000);
      setText("schat-call-status", `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`);
    }, 1000);
  }

  function stopMedia() {
    localStream?.getTracks().forEach(t => t.stop());
    remoteStream?.getTracks().forEach(t => t.stop());
    localStream = null;
    remoteStream = null;
    const local = document.getElementById("schat-call-local-video");
    const remote = document.getElementById("schat-call-remote-video");
    const audio = document.getElementById("schat-call-remote-audio");
    if (local) local.srcObject = null;
    if (remote) remote.srcObject = null;
    if (audio) audio.srcObject = null;
  }

  function closePeer() {
    if (pc) {
      try { pc.close(); } catch {}
    }
    pc = null;
  }

  function cleanup() {
    clearTimeout(callTimeout);
    clearInterval(callTimer);
    callTimeout = null;
    callTimer = null;
    closePeer();
    stopMedia();
    activeCallId = null;
    activeCallType = null;
    activePeerId = null;
    activePeerName = "Friend";
    isCaller = false;
    pendingIceCandidates = [];
    ending = false;
    hideOverlay();
  }

  function signal(frame) {
    if (typeof SChatWS === "undefined" || typeof SChatWS.sendSignal !== "function") return false;
    return SChatWS.sendSignal(frame);
  }

  async function getMedia(type) {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error("Calling is not supported by this browser.");
    }
    localStream = await navigator.mediaDevices.getUserMedia(
      type === "VIDEO"
        ? { audio: true, video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } } }
        : { audio: true, video: false }
    );
    const local = document.getElementById("schat-call-local-video");
    if (local) local.srcObject = localStream;
  }

  let iceServerCache = null;
  let iceServerCacheExpiresAt = 0;

  async function getIceServers() {
    if (iceServerCache && Date.now() < iceServerCacheExpiresAt - 30_000) return iceServerCache;
    const data = await SChat.apiFetch("/calls/ice-servers");
    const servers = Array.isArray(data?.iceServers) ? data.iceServers : [];
    if (!servers.length) throw new Error("Call networking is not configured.");
    iceServerCache = servers;
    iceServerCacheExpiresAt = data.expiresAt ? Date.parse(data.expiresAt) : Date.now() + 300_000;
    return servers;
  }

  async function createPeer() {
    closePeer();
    remoteStream = new MediaStream();
    const iceServers = await getIceServers();
    pc = new RTCPeerConnection({ iceServers, iceCandidatePoolSize: 4 });
    localStream?.getTracks().forEach(track => pc.addTrack(track, localStream));

    pc.onicecandidate = e => {
      if (e.candidate && activeCallId) {
        signal({ type: "ice_candidate", callId: activeCallId, candidate: e.candidate });
      }
    };

    pc.ontrack = e => {
      e.streams[0]?.getTracks().forEach(track => remoteStream.addTrack(track));
      const video = document.getElementById("schat-call-remote-video");
      const audio = document.getElementById("schat-call-remote-audio");
      if (video) video.srcObject = remoteStream;
      if (audio) audio.srcObject = remoteStream;
    };

    pc.onconnectionstatechange = () => {
      if (!pc) return;
      if (pc.connectionState === "connected") {
        setText("schat-call-status", "Connected");
        startTimer();
      } else if (pc.connectionState === "failed") {
        setText("schat-call-status", "Connection failed — TURN may be required");
      } else if (pc.connectionState === "disconnected") {
        setText("schat-call-status", "Reconnecting…");
      }
    };
    pc.oniceconnectionstatechange = () => {
      if (!pc) return;
      if (pc.iceConnectionState === "failed") {
        setText("schat-call-status", "Network path failed — check TURN configuration");
      }
    };
  }

  async function startCall(type) {
    if (!friendId || params.get("ai") === "1" || activeCallId) return;
    try {
      activePeerId = friendId;
      activePeerName = friendName;
      isCaller = true;
      await getMedia(type);
      await createPeer();
      setText("schat-call-name", activePeerName);
      setText("schat-call-status", "Calling…");
      setMode(type);
      setIncoming(false);
      showOverlay();

      if (!signal({
        type: "call_invite",
        to: activePeerId,
        callType: type,
        fromName: currentUser?.username || "Friend"
      })) {
        cleanup();
        SChat.showToast("Call connection is unavailable.", "error");
        return;
      }

      callTimeout = setTimeout(() => { if (activeCallId) endCall(); }, 45000);
    } catch (err) {
      cleanup();
      SChat.showToast(err.message || "Could not start the call.", "error");
    }
  }

  async function acceptIncoming() {
    if (!activeCallId || !activePeerId) return;
    try {
      await getMedia(activeCallType);
      await createPeer();
      setIncoming(false);
      setText("schat-call-status", "Connecting…");
      signal({ type: "call_accept", callId: activeCallId });
    } catch {
      SChat.showToast("Microphone/camera permission is required.", "error");
      rejectIncoming();
    }
  }

  function rejectIncoming() {
    if (activeCallId) signal({ type: "call_reject", callId: activeCallId });
    cleanup();
  }

  async function onAccepted() {
    if (!isCaller || !pc || !activeCallId) return;
    clearTimeout(callTimeout);
    setText("schat-call-status", "Connecting…");
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    signal({ type: "webrtc_offer", callId: activeCallId, description: pc.localDescription });
  }

  async function onOffer(data) {
    if (!pc || !activeCallId) return;
    await pc.setRemoteDescription(new RTCSessionDescription(data.description));
    for (const candidate of pendingIceCandidates.splice(0)) {
      try { await pc.addIceCandidate(new RTCIceCandidate(candidate)); } catch (e) { console.warn("Queued ICE candidate failed", e); }
    }
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    signal({ type: "webrtc_answer", callId: activeCallId, description: pc.localDescription });
  }

  async function onAnswer(data) {
    if (!pc || !activeCallId) return;
    await pc.setRemoteDescription(new RTCSessionDescription(data.description));
    for (const candidate of pendingIceCandidates.splice(0)) {
      try { await pc.addIceCandidate(new RTCIceCandidate(candidate)); } catch (e) { console.warn("Queued ICE candidate failed", e); }
    }
  }

  async function onIce(data) {
    if (!data.candidate || !activeCallId) return;
    if (!pc || !pc.remoteDescription) {
      pendingIceCandidates.push(data.candidate);
      return;
    }
    try { await pc.addIceCandidate(new RTCIceCandidate(data.candidate)); } catch (e) {
      console.warn("S-Chat ICE candidate error", e);
    }
  }

  function endCall() {
    if (!activeCallId || ending) return;
    ending = true;
    signal({ type: "call_end", callId: activeCallId });
    cleanup();
  }

  function toggleMute() {
    const track = localStream?.getAudioTracks()[0];
    if (!track) return;
    track.enabled = !track.enabled;
    document.getElementById("schat-call-mute")?.classList.toggle("off", !track.enabled);
  }

  function toggleCamera() {
    const track = localStream?.getVideoTracks()[0];
    if (!track) return;
    track.enabled = !track.enabled;
    document.getElementById("schat-call-camera")?.classList.toggle("off", !track.enabled);
  }

  function handleFrame(data) {
    if (!data?.type) return;

    if (data.type === "call_started") {
      activeCallId = data.callId;
      activePeerId = data.to;
      return;
    }

    if (data.type === "call_invite") {
      if (activeCallId) {
        signal({ type: "call_reject", callId: data.callId });
        return;
      }
      activeCallId = data.callId;
      activePeerId = data.from;
      activePeerName = data.fromName || "Friend";
      isCaller = false;
      setText("schat-call-name", activePeerName);
      setText("schat-call-status", `Incoming ${String(data.callType).toLowerCase()} call`);
      setMode(data.callType);
      setIncoming(true);
      showOverlay();
      return;
    }

    if (data.callId !== activeCallId) return;

    if (data.type === "call_accept") {
      onAccepted().catch(() => { SChat.showToast("Could not establish the call.", "error"); endCall(); });
    } else if (data.type === "call_reject") {
      SChat.showToast("Call declined.", "error");
      cleanup();
    } else if (data.type === "webrtc_offer") {
      onOffer(data).catch(() => endCall());
    } else if (data.type === "webrtc_answer") {
      onAnswer(data).catch(() => endCall());
    } else if (data.type === "ice_candidate") {
      onIce(data);
    } else if (data.type === "call_end") {
      cleanup();
    } else if (data.type === "error" && activeCallId) {
      SChat.showToast(data.message, "error");
      cleanup();
    }
  }

  function addChatButtons() {
    if (!friendId || params.get("ai") === "1") return;
    const header = document.querySelector(".titleandprofile");
    if (!header || document.getElementById("schat-call-buttons")) return;

    const group = document.createElement("div");
    group.id = "schat-call-buttons";
    group.className = "schat-call-buttons";
    group.innerHTML = `
      <button type="button" class="schat-header-call-btn" id="schat-voice-call-btn" aria-label="Voice call">📞</button>
      <button type="button" class="schat-header-call-btn" id="schat-video-call-btn" aria-label="Video call">📹</button>`;
    header.appendChild(group);
    document.getElementById("schat-voice-call-btn").addEventListener("click", () => startCall("VOICE"));
    document.getElementById("schat-video-call-btn").addEventListener("click", () => startCall("VIDEO"));
  }

  function init() {
    if (initialized) return;
    initialized = true;
    ensureUi();
    addChatButtons();
    if (typeof SChatWS === "undefined") return;
    SChatWS.connect();
    SChatWS.onMessage(handleFrame);
  }

  window.SChatCalls = {
    init,
    startVoiceCall: () => startCall("VOICE"),
    startVideoCall: () => startCall("VIDEO"),
    endCall
  };
  init();
})();
