import { useEffect, useState } from "react";
import { getChannels, createChannel } from "../api";

export default function ChannelSidebar({ onSelect }) {
  const [channels, setChannels] = useState([]);
  const [name, setName] = useState("");

  useEffect(() => {
    load();
  }, []);

  const load = () => {
    getChannels().then(setChannels);
  };

  const submit = async () => {
    if (!name.trim()) return;
    await createChannel(name);
    setName("");
    load();
  };

  return (
    <div className="sidebar">
      <h3>Channels</h3>

      <ul>
        {channels.map(ch => (
          <li key={ch.id} onClick={() => onSelect(ch.id)}>
            {ch.name}
          </li>
        ))}
      </ul>

      <div className="create-channel">
        <input
          value={name}
          onChange={e => setName(e.target.value)}
          placeholder="New channel name"
        />
        <button onClick={submit}>Create</button>
      </div>
    </div>
  );
}