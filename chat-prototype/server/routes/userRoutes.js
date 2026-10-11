const express = require("express");
const router = express.Router();

const { getUserByUsername, createUser } = require("../db/userModel");

router.post("/", async (req, res) => {
  try {
    const { username } = req.body;

    let user = await getUserByUsername(username);

    if (!user) {
      user = await createUser(username);
    }

    res.json(user);
  } catch (err) {
    console.error("Error in /users:", err);
    res.status(500).json({ error: "Failed to upsert user" });
  }
});

module.exports = router;