import { useState } from "react";
import Picker from "@emoji-mart/react";

export default function MessageInput({ socket, channelId, parentMessage, user }) {
  const [text, setText] = useState("");
  const [showPicker, setShowPicker] = useState(false);

  const send = () => {
    if (!text.trim()) return;

    socket.emit("send_message", {
      id: crypto.randomUUID(),
      channelId,
      authorId: user,                     
      content: text,
      mentions: [],
      parentId: parentMessage ? parentMessage.id : null
    });

    setText("");
  };

  return (
    <div className="composer">

      {parentMessage && (
        <div className="reply-banner">
          Replying to: {parentMessage.content.slice(0, 40)}…
        </div>
      )}

      <textarea
        value={text}
        onChange={e => {
          setText(e.target.value);

          // Emit typing event with REAL username
          socket.emit("typing", {
            channelId,
            user
          });
        }}
        placeholder="Write a message..."
      />

      <button onClick={() => setShowPicker(!showPicker)}>😀</button>

      {showPicker && (
        <Picker onEmojiSelect={emoji => setText(t => t + emoji.native)} />
      )}

      <button onClick={send}>Send</button>
    </div>
  );
}