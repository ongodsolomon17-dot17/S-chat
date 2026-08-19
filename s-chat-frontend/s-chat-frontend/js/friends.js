// Friend/contact request API wrapper, shared by Settings and Home pages.
window.SChatFriends = {
  async sendRequest(identifier, viaId) {
    return SChat.apiFetch("/friends/request", {
      method: "POST",
      body: JSON.stringify({ identifier, viaId })
    });
  },
  async accept(requestId) {
    return SChat.apiFetch(`/friends/requests/${requestId}/accept`, { method: "POST" });
  },
  async decline(requestId) {
    return SChat.apiFetch(`/friends/requests/${requestId}/decline`, { method: "POST" });
  },
  async incoming() {
    return SChat.apiFetch("/friends/requests/incoming");
  },
  async myFriends() {
    return SChat.apiFetch("/friends");
  },
  async myChats() {
    return SChat.apiFetch("/chat/list");
  }
};
