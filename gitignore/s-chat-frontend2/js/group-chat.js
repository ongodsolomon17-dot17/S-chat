// Group-chat WebSocket helpers.
// Uses the shared SChatWS connection for both 1:1 and group frames.

window.SChatGroupWS = {

  send(groupId, content, attachmentUrl, clientMessageId) {

    return SChatWS.sendFrame({
      type: "group_chat",
      groupId,
      content,
      attachmentUrl: attachmentUrl || null,
      clientMessageId: clientMessageId || null
    });

  },

  onMessage(handler) {

    return SChatWS.onMessage(handler);

  }

};