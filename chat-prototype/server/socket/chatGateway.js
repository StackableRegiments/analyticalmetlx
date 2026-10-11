const { createMessage, getMessageById } = require("../db/messageModel");

module.exports = function chatGateway(io) {
  io.on("connection", (socket) => {
    console.log("User connected:", socket.id);

    // JOIN CHANNEL
    socket.on("join_channel", (channelId) => {
      socket.join(channelId);
      console.log(`Socket ${socket.id} joined channel ${channelId}`);
      socket.to(channelId).emit("user_joined", { user: socket.id });
    });

    // SEND MESSAGE (socket version)
    socket.on("send_message", async (payload) => {
      console.log("Incoming message payload:", payload);

      try {
        const { channelId, authorId, content, parentId } = payload;

        // Insert message correctly
        const inserted = await createMessage(channelId, authorId, content, parentId);

        // Fetch full message with username
        const fullMessage = await getMessageById(inserted.id);

        // Emit full message to channel
        io.to(channelId).emit("message_created", fullMessage);
      } catch (err) {
        console.error("Error creating message:", err);
      }
    });

    // TYPING INDICATOR
    socket.on("typing", ({ channelId, user }) => {
      socket.to(channelId).emit("user_typing", { user });
    });

    // REACTIONS
    socket.on("react_message", ({ channelId, messageId, emoji, user }) => {
      io.to(channelId).emit("message_reacted", { messageId, emoji, user });
    });

    // EDIT MESSAGE
    socket.on("edit_message", ({ channelId, messageId, newContent }) => {
      io.to(channelId).emit("message_edited", { messageId, newContent });
    });

    // DELETE MESSAGE
    socket.on("delete_message", ({ channelId, messageId }) => {
      io.to(channelId).emit("message_deleted", { messageId });
    });

    // PIN MESSAGE
    socket.on("pin_message", ({ channelId, messageId }) => {
      io.to(channelId).emit("message_pinned", { messageId });
    });

    // USER DISCONNECTS
    socket.on("disconnect", () => {
      console.log("User disconnected:", socket.id);
      io.emit("user_left", { user: socket.id });
    });
  });
};