import { io }   from "socket.io-client";

export const socket = io("http://localhost:4000", {
  transports: ["websocket", "polling"],
  withCredentials: true
});

socket.on("connect", () => {
  console.log("SOCKET CONNECTED:", socket.id);
});

socket.on("connect_error", (err) => {
  console.error("SOCKET CONNECT ERROR:", err);
});
