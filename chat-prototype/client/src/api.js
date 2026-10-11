const API_URL = "http://localhost:4000";

// GET MESSAGES FOR A CHANNEL
export async function getMessages(channelId) {
  const res = await fetch(`${API_URL}/channels/${channelId}/messages`);
  if (!res.ok) {
    throw new Error(`Failed to fetch messages: ${res.status}`);
  }
  return res.json();
}

// SEND A MESSAGE TO A CHANNEL
export async function sendMessage(channelId, userId, content) {
  const res = await fetch(`${API_URL}/channels/${channelId}/messages`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId, content })
  });

  if (!res.ok) {
    throw new Error(`Failed to send message: ${res.status}`);
  }

  return res.json();
}