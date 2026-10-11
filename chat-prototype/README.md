# MeTL Chat Modernization Prototype  
Philips Johnson  
Saint Leo University – COM-430  

## Overview
This project is a modernization prototype of the MeTL chat system.  
It includes a React-based client, a Node.js/Express backend, and a PostgreSQL database layer.  
The goal is to demonstrate socket-based real‑time messaging, channel organization, and message persistence.

## Features Implemented
- Real-time messaging using Socket.IO  
- Channel-based message loading  
- Message reactions, edits, deletes, and typing indicators  
- Safe frontend rendering (no crashes even with backend errors)  
- Modular component structure (Chat, MessageList, ChannelSidebar, MessageInput)  
- Backend message model using PostgreSQL

## Known Issues / Constraints
- Backend `/channels/:id/messages` may return `500` if the database has no messages or invalid channel IDs.  
- Channel IDs must match UUIDs in the database.  
- Some UI components were temporarily disabled to stabilize the application for submission.

## How to Run

1. Install dependencies
```bash
npm install

2. Start the backend
bash
cd server
npm run dev

3. Start the client
bash
cd client
npm run dev

4. Access the app
Open:

Code
http://localhost:5173
Project Structure
Code
chat-prototype/
 ├── client/
 │   ├── src/
 │   │   ├── Chat.jsx
 │   │   ├── components/
 │   │   │   ├── MessageList.jsx
 │   │   │   ├── ChannelSidebar.jsx
 │   │   │   └── MessageInput.jsx
 ├── server/
 │   ├── routes/
 │   │   └── messages.js
 │   ├── db/
 │   │   ├── index.js
 │   │   ├── messageModel.js
 │   │   └── channelModel.js