const express = require("express");
const router = express.Router();

// Load your DB model
const MessageModel = require("../db/messageModel");

// GET /channels/:channelId/messages
router.get("/channels/:channelId/messages", async (req, res) => {
  try {
    const channelId = parseInt(req.params.channelId, 10);

    if (isNaN(channelId)) {
      return res.status(400).json({ error: "Invalid channel ID" });
    }

    // Fetch messages from DB
    const messages = await MessageModel.getMessagesByChannel(channelId);

    // Ensure the result is an array
    if (!Array.isArray(messages)) {
      console.error("MessageModel returned invalid data:", messages);
      return res.status(500).json({
        error: "MessageModel returned invalid data",
        data: messages
      });
    }

    // Success
    res.json(messages);
  } catch (err) {
    console.error("Error fetching messages:", err);
    res.status(500).json({ error: "Internal Server Error" });
  }
});

module.exports = router;