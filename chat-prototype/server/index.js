const express = require("express");
const http = require("http");
const cors = require("cors");
const { Server } = require("socket.io");

const userRoutes = require("./routes/userRoutes");
const { getMessagesByChannel, createMessage, getMessageById } = require("./db/messageModel");
const chatGateway = require("./socket/chatGateway");

const app = express();
app.use(cors());
app.use(express.json());

// USER ROUTES
app.use("/users", userRoutes);

// GET MESSAGES FOR A CHANNEL
app.get("/channels/:channelId/messages", async (req, res) => {
  try {
    const messages = await getMessagesByChannel(req.params.channelId);
    res.json(messages);
  } catch (err) {
    console.error("Error fetching messages:", err);
    res.status(500).json({ error: "Failed to fetch messages" });
  }
});

// CREATE MESSAGE FOR A CHANNEL (REST API version)
app.post("/channels/:channelId/messages", async (req, res) => {
  try {
    const channelId = req.params.channelId;
    const { userId, content, parentId } = req.body;

    if (!userId || !content) {
      return res.status(400).json({ error: "userId and content are required" });
    }

    // Insert message correctly
    const inserted = await createMessage(channelId, userId, content, parentId);

    // Fetch full message with username
    const fullMessage = await getMessageById(inserted.id);

    // Emit to socket listeners
    io.to(channelId).emit("message_created", fullMessage);

    res.status(201).json(fullMessage);
  } catch (err) {
    console.error("Error creating message:", err);
    res.status(500).json({ error: "Failed to create message" });
  }
});

const server = http.createServer(app);

// SOCKET.IO CONFIG
const io = new Server(server, {
  cors: {
    origin: ["http://localhost:5173", "http://127.0.0.1:5173"],
    methods: ["GET", "POST"],
    credentials: true
  },
  transports: ["websocket", "polling"],
  allowEIO3: true,
  path: "/socket.io"
});

// SOCKET GATEWAY
chatGateway(io);

const PORT = 4000;

server.listen(PORT, () => {
  console.log(`Chat server listening on ${PORT}`);
});