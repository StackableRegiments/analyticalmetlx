const db = require("./index");

// CREATE MESSAGE WITH USERNAME SUPPORT
async function createMessage({ id, channelId, authorId, username, content, parentId }) {
  const result = await db.query(
    `INSERT INTO messages (
        id,
        channel_id,
        author_id,
        username,
        content,
        parent_message_id
     )
     VALUES ($1, $2, $3, $4, $5, $6)
     RETURNING *`,
    [id, channelId, authorId, username, content, parentId]
  );

  return result.rows[0];
}

// GET ALL MESSAGES FOR A CHANNEL
async function getMessagesByChannel(channelId) {
  const result = await db.query(
    `SELECT
        id,
        channel_id,
        author_id,
        username,
        content,
        parent_message_id,
        created_at
     FROM messages
     WHERE channel_id = $1
     ORDER BY created_at ASC`,
    [channelId]
  );

  return result.rows;
}

module.exports = {
  createMessage,
  getMessagesByChannel
};