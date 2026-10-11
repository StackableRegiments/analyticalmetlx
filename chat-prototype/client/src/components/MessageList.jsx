import React from "react";
import { socket } from "../socket";

export default function MessageList({ messages, onReply, channelId, userId }) {
  if (!Array.isArray(messages)) return null;

  // Normalize messages
  const normalized = messages.map((m) => ({
    id: m.id,
    author: m.username || m.author_id || "Unknown",
    content: m.content,
    parentId: m.parent_message_id || null,
    createdAt: m.created_at,
    reactions: m.reactions || []
  }));

  // Sort by timestamp
  const sorted = [...normalized].sort(
    (a, b) => new Date(a.createdAt) - new Date(b.createdAt)
  );

  // Build reply tree
  const map = {};
  sorted.forEach((m) => (map[m.id] = { ...m, replies: [] }));
  sorted.forEach((m) => {
    if (m.parentId && map[m.parentId]) {
      map[m.parentId].replies.push(map[m.id]);
    }
  });

  const roots = sorted.filter((m) => !m.parentId);

  // Reaction handler
  const react = (messageId, emoji) => {
    socket.emit("react_message", {
      channelId,
      messageId,
      emoji,
      user: userId
    });
  };

  const renderMessage = (msg, depth = 0) => (
    <div key={msg.id} style={{ marginLeft: depth * 25, marginBottom: "14px" }}>
      <div
        style={{
          padding: "10px 12px",
          background: depth === 0 ? "#1e1e1e" : "#2a2a2a",
          borderRadius: "10px",
          color: "orange",
          borderLeft: depth > 0 ? "3px solid #555" : "none"
        }}
      >
        <p style={{ margin: 0 }}>
          <strong>{msg.author}</strong>: {msg.content}
        </p>

        {/* Reply button */}
        <button
          style={{
            marginLeft: "10px",
            fontSize: "12px",
            background: "#444",
            color: "white",
            border: "none",
            padding: "4px 8px",
            borderRadius: "6px",
            cursor: "pointer"
          }}
          onClick={() => onReply(msg)}
        >
          Reply
        </button>

        {/* Reaction buttons */}
        <button
          style={{
            marginLeft: "10px",
            fontSize: "14px",
            background: "#444",
            color: "white",
            border: "none",
            padding: "4px 8px",
            borderRadius: "6px",
            cursor: "pointer"
          }}
          onClick={() => react(msg.id, "👍")}
        >
          👍
        </button>

        <button
          style={{
            marginLeft: "6px",
            fontSize: "14px",
            background: "#444",
            color: "white",
            border: "none",
            padding: "4px 8px",
            borderRadius: "6px",
            cursor: "pointer"
          }}
          onClick={() => react(msg.id, "🙂")}
        >
          🙂
        </button>

        <button
          style={{
            marginLeft: "6px",
            fontSize: "14px",
            background: "#444",
            color: "white",
            border: "none",
            padding: "4px 8px",
            borderRadius: "6px",
            cursor: "pointer"
          }}
          onClick={() => react(msg.id, "❤️")}
        >
          ❤️
        </button>

        {/* Timestamp */}
        <div style={{ fontSize: "11px", opacity: 0.6, marginTop: "6px" }}>
          {new Date(msg.createdAt).toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit"
          })}
        </div>

        {/* Show reactions */}
        {msg.reactions && msg.reactions.length > 0 && (
          <div style={{ marginTop: "6px", opacity: 0.9 }}>
            {msg.reactions.map((r, i) => (
              <span key={i} style={{ marginRight: "6px" }}>
                {r.emoji}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Render replies */}
      {msg.replies.map((r) => renderMessage(r, depth + 1))}
    </div>
  );

  return <div className="messages">{roots.map((m) => renderMessage(map[m.id]))}</div>;
}