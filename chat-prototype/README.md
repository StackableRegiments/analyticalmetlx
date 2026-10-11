# MeTL Chat Prototype
**Last Updated:** October 11, 2026  
**Author:** JBo (Philips Johnson)

## Overview
The MeTL Chat Prototype is a modernized real‑time messaging system designed to replace the legacy Scala chat module in the MeTL platform. This prototype demonstrates a fully functional chat environment using Node.js, Express, Socket.IO, React, and PostgreSQL.

This version validates all core chat features prior to integration into the MeTL whiteboard application.

---

## Features

### Real‑Time Messaging
- Instant message delivery using Socket.IO  
- Multi‑client synchronization (tested with dual browser sessions)  
- Auto‑scrolling message feed  

### Typing Indicators
- Live “Someone is typing…” feedback  
- Debounced user typing events  

### Threaded Replies
- Reply‑to‑message functionality  
- Parent/child message relationships stored in the database  

### Emoji Reactions
- Real‑time reactions appended to messages  
- Reaction events broadcast to all connected clients  

### Search Bar
- Client‑side message filtering  
- Case‑insensitive search  

### Database Integration
- PostgreSQL backend  
- pgAdmin dashboard shows live activity (TPS, tuples, block I/O)  
- Message history loaded on channel join  

---

## Tech Stack

### Frontend
- React (Vite)  
- JSX components  
- Socket.IO client  

### Backend
- Node.js  
- Express  
- Socket.IO server  
- PostgreSQL  
- Knex migrations  

### Tools
- VS Code  
- pgAdmin 4  
- GitHub version control  

---

## Project Structure
chat-prototype/
│
├── client/               # React frontend
│   └── src/
│       ├── Chat.jsx
│       ├── components/
│       └── socket.js
│
├── server/               # Node backend
│   ├── index.js
│   ├── socket/
│   │   └── chatGateway.js
│   └── db/
│       ├── migrations/
│       ├── channelModel.js
│       └── messageModel.js
│
└── README.md             # This file


---

## Running the Project

### Start the Server
cd chat-prototype/server
npm install
npm run dev

### Start the Client
cd chat-prototype/client
npm install
npm start

The client will open automatically at:
http://localhost:5173

---

## Pre‑Integration Validation
Screenshots captured and included in the Module 5 Update Report show:

- Two browser windows exchanging messages in real time  
- Typing indicators functioning  
- Emoji reactions updating instantly  
- pgAdmin dashboard showing active sessions and transaction spikes  
- Server and client terminals running concurrently  

This confirms the chat system is stable and ready for MeTL integration.

---

## Next Steps
- Integrate chat UI into MeTL’s main interface  
- Connect MeTL user authentication to chat author IDs  
- Link MeTL room/channel context to chat channels  
- Replace legacy Scala chat module  