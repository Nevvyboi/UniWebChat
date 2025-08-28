// ===== helpers =====
const appBase = () => {
  const p = window.location.pathname; // e.g. /UniConnectLiveChat/conversations.html
  const i = p.indexOf('/', 1);
  return i > 0 ? p.slice(0, i) : '';
};
const wsScheme = () => (location.protocol === 'https:' ? 'wss://' : 'ws://');
const WS_URL = () => `${wsScheme()}${location.host}${appBase()}/ws/chat`;

const $ = (id) => document.getElementById(id);
const clock = () => {
  const d = new Date();
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
};

// Render one WhatsApp-like message row
function addBubble({ me=false, name='User', avatar=null, time=clock(), text='' }) {
  const thread = $('chat-thread');

  const row = document.createElement('div');
  row.className = 'msg-row' + (me ? ' me' : ' other');

  const img = document.createElement('img');
  img.className = 'avatar';
  img.alt = '';
  img.src = avatar || 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw=='; // transparent 1x1
  img.onerror = () => {
    img.style.display = 'none';
    const initial = name.charAt(0).toUpperCase();
    const initialDiv = document.createElement('div');
    initialDiv.className = 'contact-avatar';
    initialDiv.textContent = initial;
    row.prepend(initialDiv);
  };

  const bubble = document.createElement('div');
  bubble.className = 'bubble';

  const meta = document.createElement('div');
  meta.className = 'meta-line';

  const nm = document.createElement('span');
  nm.className = 'name';
  nm.textContent = name;

  const tm = document.createElement('span');
  tm.className = 'time';
  tm.textContent = time;

  meta.append(nm, tm);

  const body = document.createElement('div');
  body.className = 'text';
  body.textContent = text;

  bubble.append(meta, body);
  row.append(img, bubble);
  thread.appendChild(row);
  thread.scrollTop = thread.scrollHeight;
}

function addSystem(text) {
  const thread = $('chat-thread');
  const d = document.createElement('div');
  d.className = 'sys-line';
  d.textContent = text;
  thread.appendChild(d);
  thread.scrollTop = thread.scrollHeight;
}

// ===== Who am I? (so we know which side is "me") =====
let ME = { username: 'You', avatar: null };
let currentRoom = 'ITEJA3-33';
let lastSentText = '';
let lastSentAt = 0;

(async () => {
  try {
    const r = await fetch('whoami', { credentials: 'include' });
    if (r.ok) {
      const j = await r.json();
      if (j.username || j.user || j.name) ME.username = j.username || j.user || j.name;
      if (j.avatar || j.photo) ME.avatar = j.avatar || j.photo;
    }
  } catch {}
})();

// ===== WebSocket and history =====
let ws;
let hasConnectedOnce = false;
let reconnectTimer = null;
let reconnectDelay = 800;
function scheduleReconnect(){
  if (reconnectTimer) clearTimeout(reconnectTimer);
  reconnectTimer = setTimeout(() => { reconnectTimer = null; connect(); reconnectDelay = Math.min(reconnectDelay * 1.5, 8000); }, reconnectDelay);
}
function connect() {
  if (hasConnectedOnce && ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return;
  hasConnectedOnce = true;
  ws = new WebSocket(WS_URL());

  ws.onopen = (e) => { reconnectDelay = 800;
    console.log('WS connected', e);
    addSystem('Connected.');
    const joinMsg = { type: 'join', room: currentRoom };
    ws.send(JSON.stringify(joinMsg));
  };

  ws.onmessage = (e) => {
    let msg = null;
    try { msg = JSON.parse(e.data); } catch {}

    if (msg && msg.type === 'sys') {
      addSystem(msg.text || '');
      return;
    }

    if (msg && (msg.type === 'chat' || msg.text)) {
      const from = msg.from || 'User';
      const me = from === ME.username || !!msg.me;
      addBubble({
        me,
        name: me ? ME.username : from,
        avatar: me ? ME.avatar : (msg.avatar || null),
        time: msg.time || clock(),
        text: msg.text || ''
      });
      return;
    }
    addBubble({ me:false, name:'User', text:String(e.data), time:clock() });
  };

  ws.onclose = () => {
    addSystem('Disconnected. Reconnecting…');
    setTimeout(connect, 1200);
  };
}
connect();

// Load chat history for the current room
async function loadHistory(room) {
  $('chat-thread').innerHTML = '';
  addSystem('Loading history...');
  try {
    const r = await fetch(`/api/history?mode=room&room=${encodeURIComponent(room)}`);
    if (r.ok) {
      const data = await r.json();
      data.messages.forEach(msg => {
        addBubble({
          me: msg.sender === ME.username,
          name: msg.sender,
          avatar: msg.avatar,
          time: new Date(msg.createdAt).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}),
          text: msg.content
        });
      });
      addSystem('History loaded.');
    }
  } catch (e) {
    addSystem('Failed to load history.');
    console.error(e);
  }
}

// Handle room selection
function selectRoom(room) {
  if (room === currentRoom) return;
  currentRoom = room;
  $('chatTitle').textContent = room;
  // TODO: send a WebSocket message to join the new room if needed
  loadHistory(room);
}

ws.onclose = () => { addSystem('Disconnected. Reconnecting...'); scheduleReconnect(); };
ws.onerror  = () => { /* let onclose handle retry */ };

// Initial load
document.addEventListener('DOMContentLoaded', () => {
  const pinnedContact = $('room-ITEJA3-33');
  if (pinnedContact) {
    pinnedContact.addEventListener('click', () => selectRoom(pinnedContact.dataset.room));
  }
  loadHistory(currentRoom);
});

// ===== send =====
function send() {
  const input = $('messageInput');
  const text = input.value.trim();
  const now = Date.now();
  if (text === lastSentText && (now - lastSentAt) < 800) return;
  if (!text || !ws || ws.readyState !== WebSocket.OPEN) return;

  const payload = {
    type: 'room',
    room: currentRoom,
    from: ME.username,
    avatar: ME.avatar,
    time: clock(),
    text
  };
  ws.send(JSON.stringify(payload));
  input.value = ''; // Clear the input field
}

$('sendBtn').addEventListener('click', send);
$('messageInput').addEventListener('keypress', (e) => {
  if (e.key === 'Enter') {
    e.preventDefault();
    send();
  }
});