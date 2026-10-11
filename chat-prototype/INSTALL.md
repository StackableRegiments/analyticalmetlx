# Installation Guide

## Requirements
- Node.js 18+  
- PostgreSQL 14+  
- pgAdmin 4  
- Git  
- VS Code  

---

## Backend Setup
cd chat-prototype/server
npm install

### Configure Database
1. Create a PostgreSQL database (e.g., `chatdb`)
2. Run migrations:

npm run migrate

### Start Server

---

## Frontend Setup

cd chat-prototype/client
npm install
npm start

The client will open at:

http://localhost:5173

---

## Troubleshooting
- If messages do not send, ensure the server is running  
- If database errors occur, verify your connection string  
- If sockets do not connect, check firewall or port conflicts  