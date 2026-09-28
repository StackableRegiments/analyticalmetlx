const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const jwt = require('jsonwebtoken');

const PORT = process.env.PORT || 4000;
const SECRET = process.env.CHAT_SECRET || 'dev-secret';

const app = express();
app.use(cors());
app.use(express.json());

// simple in-memory message store
const messages = [];
let nextId = 1;

function verifyToken(token) {
  try {
    return jwt.verify(token, SECRET);
  } catch {
    return null;
  }
}

app.post('/login', (req, res) => {
  const { user } = req.body || {};
  if (!user) return res.status(400).json({ error: 'user required' });
  const token = jwt.sign({ user }, SECRET, { expiresIn: '8h' });
  res.json({ token, user });
});

app.get('/messages', (req, res) => {
  res.json(messages);
});

app.post('/messages', (req, res) => {
  const auth = req.headers.authorization || '';
  const token = auth.replace(/^Bearer\s+/i, '');
  const payload = verifyToken(token);
  if (!payload) return res.status(401).json({ error: 'unauthorized' });

  const { text } = req.body || {};
  if (!text) return res.status(400).json({ error: 'text required' });

  const msg = {
    id: nextId++,
    user: payload.user,
    text,
    created_at: new Date().toISOString(),
    edited: 0
  };
  messages.push(msg);
  io.to('global').emit('message:new', msg);
  res.json(msg);
});

const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*' } });

io.use((socket, next) => {
  const token = socket.handshake.auth?.token;
  const payload = verifyToken(token);
  if (!payload) return next(new Error('unauthorized'));
  socket.user = payload.user;
  next();
});

io.on('connection', (socket) => {
  const user = socket.user;
  socket.join('global');
  io.to('global').emit('presence', { user, status: 'online' });

  socket.on('typing', (payload) => {
    socket.to('global').emit('typing', { user, ...payload });
  });

  socket.on('message:create', (payload, ack) => {
    const msg = {
      id: nextId++,
      user,
      text: payload.text,
      created_at: new Date().toISOString(),
      edited: 0
    };
    messages.push(msg);
    io.to('global').emit('message:new', msg);
    if (ack) ack(msg);
  });

  socket.on('disconnect', () => {
    io.to('global').emit('presence', { user, status: 'offline' });
  });
});

server.listen(PORT, () => {
  console.log(`Chat server listening on ${PORT}`);
});