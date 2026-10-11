import React, { useState, useEffect } from "react";
import MessageList from "./components/MessageList";
import { socket } from "./socket";
import { getMessages } from "./api";

export default function Chat({ token, username, channelId }) {
  const [messages, setMessages] = useState([]);
  const [message, setMessage] = useState("");
  const [search, setSearch] = useState("");
  const [replyTo, setReplyTo] = useState(null);
  const [typingUsers, setTypingUsers] = useState([]);

  const user = { id: token, username };

  // Load messages once
  useEffect(() => {
    async function load() {
      const data = await getMessages(channelId);
      setMessages(data);
    }
    load();
  }, []);

  // Socket listeners + join channel
  useEffect(() => {
    // Prevent double join from React Strict Mode
    if (!socket._joinedChannel) {
      socket.emit("join_channel", channelId);
      socket._joinedChannel = true;
    }

    // Receive full message instantly
    socket.on("message_created", (msg) => {
      setMessages((prev) => [...prev, msg]);
    });

    socket.on("message_reacted", ({ messageId, emoji, user }) => {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === messageId
            ? { ...m, reactions: [...(m.reactions || []), { emoji, user }] }
            : m
        )
      );
    });

    socket.on("user_typing", ({ user }) => {
      setTypingUsers((prev) => {
        if (!prev.includes(user)) return [...prev, user];
        return prev;
      });

      setTimeout(() => {
        setTypingUsers((prev) => prev.filter((id) => id !== user));
      }, 1500);
    });

    return () => {
      socket.off("message_created");
      socket.off("message_reacted");
      socket.off("user_typing");
    };
  }, [channelId]);

  // Auto-scroll
  useEffect(() => {
    const el = document.querySelector(".messages");
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages]);

  const handleTyping = () => {
    socket.emit("typing", { channelId, user: user.id });
  };

  const sendMessage = () => {
    if (!message.trim()) return;

    socket.emit("send_message", {
      channelId,
      authorId: user.id,
      username: user.username,
      content: message,
      parentId: replyTo?.id || null
    });

    setMessage("");
    setReplyTo(null);
  };

  const safeMessages = Array.isArray(messages) ? messages : [];
  const filteredMessages = safeMessages.filter((m) =>
    m.content?.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        height: "100vh",
        background: "#111",
        color: "white"
      }}
    >
      {/* Search bar */}
      <div style={{ padding: "10px", borderBottom: "1px solid #333" }}>
        <input
          placeholder="Search messages..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{
            width: "100%",
            padding: "10px",
            borderRadius: "8px",
            border: "1px solid #444",
            background: "#1e1e1e",
            color: "white"
          }}
        />
      </div>

      {/* Reply banner */}
      {replyTo && (
        <div
          style={{
            background: "#333",
            padding: "10px",
            borderLeft: "4px solid #888",
            margin: "10px",
            borderRadius: "8px"
          }}
        >
          Replying to: <strong>{replyTo.content}</strong>
          <button
            onClick={() => setReplyTo(null)}
            style={{
              marginLeft: "10px",
              background: "#444",
              color: "white",
              border: "none",
              padding: "4px 8px",
              borderRadius: "6px",
              cursor: "pointer"
            }}
          >
            Cancel
          </button>
        </div>
      )}

      {/* Scrollable message list */}
      <div
        className="messages"
        style={{
          flex: 1,
          overflowY: "auto",
          padding: "10px"
        }}
      >
        <MessageList
          messages={filteredMessages}
          onReply={(msg) => setReplyTo(msg)}
          channelId={channelId}
          userId={user.id}
        />
      </div>

      {/* Typing indicator */}
      {typingUsers.length > 0 && (
        <div style={{ padding: "6px 12px", opacity: 0.7 }}>
          Someone is typing...
        </div>
      )}

      {/* Bottom input bar */}
      <div
        style={{
          display: "flex",
          padding: "10px",
          borderTop: "1px solid #333",
          background: "#1e1e1e"
        }}
      >
        <input
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          onKeyDown={(e) => {
          handleTyping(); // keep your typing indicator

          if (e.key === "Enter") {
            e.preventDefault();
            sendMessage();
          }
       }}
       placeholder="Type a message..."
       style={{
         flex: 1,
         padding: "10px",
         borderRadius: "8px",
         border: "1px solid #444",
         background: "#2a2a2a",
         color: "white"
       }}
     />

        <button
          onClick={sendMessage}
          style={{
            marginLeft: "10px",
            padding: "10px 18px",
            background: "#444",
            color: "white",
            border: "none",
            borderRadius: "8px",
            cursor: "pointer"
          }}
        >
          Send
        </button>
      </div>
    </div>
  );
}