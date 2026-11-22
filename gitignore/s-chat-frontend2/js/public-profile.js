(function () {
  const params = new URLSearchParams(window.location.search);
  const username = params.get("username");
  const name = document.getElementById("public-username");
  const id = document.getElementById("public-id");
  const message = document.getElementById("profile-message");
  const avatar = document.getElementById("public-avatar");
  const connectButton = document.getElementById("connect-user");
  let profile = null;

  async function load() {
    if (!username) {
      name.textContent = "Profile unavailable";
      message.textContent = "This S-Chat link is missing a username.";
      return;
    }
    try {
      const base = window.S_CHAT_CONFIG?.API_BASE_URL;
      if (!base) throw new Error("S-Chat is not configured yet.");
      const response = await fetch(base + "/users/public/" + encodeURIComponent(username));
      const body = await response.json().catch(() => null);
      if (!response.ok) throw new Error(body?.messages?.[0] || "This profile could not be found.");
      profile = body;
      name.textContent = "@" + body.username;
      id.textContent = body.publicId || "";
      if (body.profilePictureUrl) avatar.src = body.profilePictureUrl;
      document.title = "@" + body.username + " — S-Chat";
      message.textContent = "Connect with this user to start a conversation.";
    } catch (error) {
      name.textContent = "Profile unavailable";
      message.textContent = error.message;
    }
  }
  connectButton?.addEventListener("click", async () => {
    if (!profile) return;
    if (!window.SChat?.Auth?.isLoggedIn()) {
      const next = "public-profile.html?username=" + encodeURIComponent(username || "");
      window.location.href = "S-chat-log-in.html?next=" + encodeURIComponent(next);
      return;
    }
    connectButton.disabled = true;
    connectButton.textContent = "Sending…";
    try {
      const result = await SChat.apiFetch("/friends/request", {
        method: "POST",
        body: JSON.stringify({ identifier: profile.publicId, viaId: true })
      });
      message.textContent = result.status === "ACCEPTED"
        ? "You're connected. Open your chats to start messaging."
        : "Connection request sent. You'll be able to chat when it is accepted.";
      connectButton.textContent = result.status === "ACCEPTED" ? "Connected" : "Request sent";
    } catch (error) {
      message.textContent = error.message;
      connectButton.textContent = "Try again";
      connectButton.disabled = false;
    }
  });

  load();
})();
