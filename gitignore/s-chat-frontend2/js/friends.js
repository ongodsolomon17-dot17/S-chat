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
  },
  async profile(friendId) {
    return SChat.apiFetch(`/friends/${encodeURIComponent(friendId)}/profile`);
  },
  async remove(friendId) {
    return SChat.apiFetch(`/friends/${encodeURIComponent(friendId)}`, { method: "DELETE" });
  },
  async block(friendId) {
    return SChat.apiFetch(`/friends/${encodeURIComponent(friendId)}/block`, { method: "POST" });
  },
  async unblock(friendId) {
    return SChat.apiFetch(`/friends/${encodeURIComponent(friendId)}/block`, { method: "DELETE" });
  }
};


window.SChatGroups = {
  async list() { return SChat.apiFetch("/chat/groups"); },
  async create(name, memberIds, avatarUrl) {
    return SChat.apiFetch("/chat/groups", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name, memberIds, avatarUrl: avatarUrl || null }) });
  },
  async details(groupId) { return SChat.apiFetch(`/chat/groups/${encodeURIComponent(groupId)}`); },
  async messages(groupId, page = 0, size = 50) { return SChat.apiFetch(`/chat/groups/${encodeURIComponent(groupId)}/messages?page=${page}&size=${size}`); },
  async send(groupId, content, attachmentUrl) {
    return SChat.apiFetch(`/chat/groups/${encodeURIComponent(groupId)}/messages`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ content, attachmentUrl: attachmentUrl || null }) });
  },
  async addMember(groupId, userId) { return SChat.apiFetch(`/chat/groups/${encodeURIComponent(groupId)}/members`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userId }) }); },
  async removeMember(groupId, userId) { return SChat.apiFetch(`/chat/groups/${encodeURIComponent(groupId)}/members/${encodeURIComponent(userId)}`, { method: "DELETE" }); },
  async promote(groupId, userId) { return SChat.apiFetch(`/chat/groups/${encodeURIComponent(groupId)}/members/${encodeURIComponent(userId)}/admin`, { method: "POST" }); },
  async demote(groupId, userId) { return SChat.apiFetch(`/chat/groups/${encodeURIComponent(groupId)}/members/${encodeURIComponent(userId)}/admin`, { method: "DELETE" }); },
  async leave(groupId) { return SChat.apiFetch(`/chat/groups/${encodeURIComponent(groupId)}/me`, { method: "DELETE" }); },
  async transferOwnership(groupId, userId) { return SChat.apiFetch(`/chat/groups/${encodeURIComponent(groupId)}/ownership/${encodeURIComponent(userId)}`, { method: "POST" }); }
};
