const db = require('./index');

async function createChannel({ id, name, description }) {
  const result = await db.query(
    `INSERT INTO channels (id, name, description)
     VALUES ($1, $2, $3)
     RETURNING *`,
    [id, name, description]
  );
  return result.rows[0];
}

async function getChannels() {
  const result = await db.query(`SELECT * FROM channels ORDER BY name ASC`);
  return result.rows;
}

module.exports = {
  createChannel,
  getChannels
};