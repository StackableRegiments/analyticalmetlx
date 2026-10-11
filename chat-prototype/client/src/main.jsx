import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import "./chat.css";

// Ask for username once and store it
let username = localStorage.getItem("username");

if (!username) {
  username = prompt("Enter your username:");
  localStorage.setItem("username", username);
}

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <App username={username} />
  </React.StrictMode>
);