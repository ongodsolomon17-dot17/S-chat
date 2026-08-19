# S-Chat verification checklist

Run from `s-chat-backend`:

```bash
mvn -DskipTests package
```

Then test these flows with two different S-Chat accounts:

1. Send a text message.
2. Send an image/video without text.
3. Send a voice note.
4. Reply to a message by swiping on mobile.
5. Reply using the message action menu.
6. Add/remove reactions.
7. Delete for me.
8. Delete for everyone and confirm the other user sees the deleted placeholder.
9. Confirm chat list order changes to the newest message.
10. Open Status and swipe left/right through multiple updates from the same friend.
11. At the last update from one friend, swipe left to the next friend.
12. At the first update from a friend, swipe right to the previous friend.
13. Reply to a friend's status.
14. Start a voice call.
15. Start a video call.
16. Accept an incoming call from the second account.
17. Decline an incoming call.
18. End an active call.
19. Confirm the Calls page records the call.
20. Leave an incoming call unanswered for more than 90 seconds and confirm it becomes MISSED.

## Calling note

The implementation includes public STUN servers and optional TURN configuration in:

`S-chat-frontend/js/config.js`

For reliable calls across restrictive mobile networks, configure a real TURN relay before treating calling as production-ready.
