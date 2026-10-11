const db = require("./index");

async function getUserByUsername(username) {
  const { rows } = await db.query(
    "SELECT id, username FROM users WHERE username = $1",
    [username]
  );
  return rows[0] || null;
}

async function createUser(username) {
  const { rows } = await db.query(
    "INSERT INTO users (username) VALUES ($1) RETURNING id, username",
    [username]
  );
  return rows[0];
}

module.exports = { getUserByUsername, createUser };