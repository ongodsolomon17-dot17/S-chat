// Group-chat WebSocket helpers. The shared SChatWS connection carries both 1:1 and group frames.
window.SChatGroupWS = {
  send(groupId, content, attachmentUrl, clientMessageId) {
    return SChatWS.sendFrame({
      type: "group_chat",
      groupId,
      content,
      attachmentUrl: attachmentUrl || null,
      clientMessageId: clientMessageId || null
    });
  }
};
