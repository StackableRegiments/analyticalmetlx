import React from "react";
import Chat from "./Chat";

export default function App({ username }) {
  // REAL UUID for authorId
  const token = crypto.randomUUID();

  // REAL CHANNEL UUID
  const channelId = "44db8bd5-59b3-435f-af7b-262d45cee1e0";

  return (
    <div style={{ height: "100vh" }}>
      <Chat
        token={token}
        username={username}
        channelId={channelId}
      />
    </div>
  );
}