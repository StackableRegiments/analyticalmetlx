import React, { useState, useEffect, useRef } from 'react';
import { io } from 'socket.io-client';
import axios from 'axios';

const SERVER = import.meta.env.VITE_SERVER_URL || 'http://localhost:4000';

export default function Chat() {
  const [user, setUser] = useState('');
  const [token, setToken] = useState(null);
  const [messages, setMessages] = useState([]);
  const [text, setText] = useState('');
  const socketRef = useRef(null);

  useEffect(() => {
    if (!token) return;
    const socket = io(SERVER, { auth: { token } });
    socketRef.current = socket;
    socket.on('message:new', (m) => setMessages((s) => [...s, m]));
    return () => socket.disconnect();
  }, [token]);

  useEffect(() => {
    if (!token) return;
    axios.get(`${SERVER}/messages`).then(r => setMessages(r.data)).catch(() => {});
  }, [token]);

  const login = async () => {
    if (!user) return;
    const res = await axios.post(`${SERVER}/login`, { user });
    setToken(res.data.token);
  };

  const send = () => {
    if (!text || !socketRef.current) return;
    socketRef.current.emit('message:create', { text });
    setText('');
  };

  return (
    <div style={{ maxWidth: 700, margin: 24, fontFamily: 'system-ui, Arial' }}>
      {!token ? (
        <div style={{ display: 'flex', gap: 8 }}>
          <input value={user} onChange={e => setUser(e.target.value)} placeholder="Your name" />
          <button onClick={login} disabled={!user}>Login</button>
        </div>
      ) : (
        <>
          <div style={{ border: '1px solid #ddd', padding: 12, height: 360, overflow: 'auto', marginBottom: 8 }}>
            {messages.map(m => <div key={m.id}><strong>{m.user}:</strong> {m.text}</div>)}
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <input style={{ flex: 1 }} value={text} onChange={e => setText(e.target.value)} placeholder="Message" />
            <button onClick={send} disabled={!text}>Send</button>
          </div>
        </>
      )}
    </div>
  );
}