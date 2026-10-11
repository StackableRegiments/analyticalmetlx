const db = require("./index");

// CREATE MESSAGE WITH USERNAME SUPPORT
const { v4: uuidv4 } = require("uuid");

async function createMessage({ channelId, authorId, username, content, parentId }) {
  const id = uuidv4();
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

// GET SINGLE MESSAGE BY ID
async function getMessageById(id) {
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
     WHERE id = $1`,
    [id]
  );

  return result.rows[0];
}

module.exports = {
  createMessage,
  getMessagesByChannel,
  getMessageById
};