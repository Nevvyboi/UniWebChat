


(function () {
  
  function appCtx() {
    const p = window.location.pathname.split('/');
    return p.length > 1 ? '/' + p[1] : '';
  }
  function withCtx(path) {
    return appCtx() + (path.startsWith('/') ? path : '/' + path);
  }

  
  function getSessionId() {
    let sessionId = sessionStorage.getItem('sessionId');
    if (!sessionId) {
      sessionId = 'session_' + Math.random().toString(36).substr(2, 9);
      sessionStorage.setItem('sessionId', sessionId);
    }
    return sessionId;
  }

  
  const state = {
    username: 'Me',
    myAvatar: null,
    room: 'General',
    ws: null,
    connected: false,
    contactMap: {}, 
    sessionId: getSessionId()
  };

  
  const chatThread   = document.getElementById('chat-thread');
  const messageInput = document.getElementById('messageInput');
  const sendBtn      = document.getElementById('sendBtn');
  const contactsWrap = document.querySelector('.contacts');

  
  function initials(name) {
    if (!name) return 'U';
    return name.split(/\s+/).map(p => p[0]?.toUpperCase() || '').join('').slice(0,2) || 'U';
  }
  function esc(s){ return String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }

  // Avatar element
  function makeAvatar(username) {
    const url = (username && state.contactMap[username]) || null;
    const el = document.createElement('div');
    el.className = 'contact-avatar';
    if (url) {
      el.style.backgroundImage = `url('${url}')`;
      el.style.backgroundSize = 'cover';
      el.style.backgroundPosition = 'center';
      el.textContent = ''; // no initials if we have an image
    } else {
      el.textContent = initials(username);
      el.style.backgroundImage = 'none';
    }
    return el;
  }

  function renderMessage(msg) {
    const isMe = msg.sender === state.username;
    const row = document.createElement('div');
    row.className = `msg-row ${isMe ? 'me' : 'other'}`;

    // avatar (CSS hides for me, but we still create it for layout consistency)
    const avatarWrap = document.createElement('div');
    avatarWrap.className = 'avatar-container';
    avatarWrap.appendChild(makeAvatar(isMe ? state.username : msg.sender));
    row.appendChild(avatarWrap);

    // bubble
    const bubble = document.createElement('div');
    bubble.className = 'bubble';

    const hdr = document.createElement('div');
    hdr.className = 'meta';
    const who = document.createElement('span');
    who.className = 'who';
    who.textContent = isMe ? 'You' : msg.sender;
    const when = document.createElement('span');
    when.className = 'time';
    const d = msg.sentAt ? new Date(msg.sentAt) : new Date();
    when.textContent = d.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'});
    hdr.appendChild(who); hdr.appendChild(when);

    const body = document.createElement('div');
    body.className = 'body';
    body.textContent = msg.content || '';

    bubble.appendChild(hdr);
    bubble.appendChild(body);
    row.appendChild(bubble);

    chatThread.appendChild(row);
    chatThread.scrollTop = chatThread.scrollHeight;
  }

  function clearThread() {
    chatThread.innerHTML = '';
  }

  function setActiveContact(el) {
    document.querySelectorAll('.contact').forEach(c => c.classList.remove('active'));
    el?.classList.add('active');
  }

  // Room selection (uses data-room or text)
  function loadRoomFromContact(contact) {
    setActiveContact(contact);
    const roomId = contact?.dataset.roomId || contact?.dataset.room || contact?.textContent?.trim() || 'General';
    state.room = roomId;
    clearThread();
    // No history load — fresh conversation UI
  }

  // Send message
  function sendCurrentMessage() {
    const text = messageInput.value.trim();
    if (!text) return;
    const msg = {
      sender: state.username,
      room: state.room,
      content: text,
      sentAt: Date.now(),
      sessionId: state.sessionId
    };
    // optimistic render
    renderMessage(msg);
    messageInput.value = '';

    try {
      state.ws?.send(JSON.stringify({ type: 'message', ...msg }));
    } catch(e) {
      console.warn('WS send failed', e);
    }
  }

  // Build WebSocket URL (always includes sender and session)
  function wsUrlFor(room) {
    const loc = window.location;
    const scheme = (loc.protocol === 'https:') ? 'wss' : 'ws';
    const ctx = appCtx().replace(/^\/+/, '');
    const base = ctx ? ('/' + ctx) : '';
    const q = `?room=${encodeURIComponent(room)}&sender=${encodeURIComponent(state.username)}&sessionId=${encodeURIComponent(state.sessionId)}`;
    return `${scheme}://${loc.host}${base}/chat${q}`;
  }

  function connectWs() {
    try {
      state.ws = new WebSocket(wsUrlFor(state.room));
    } catch (e) {
      console.warn('WS create failed', e);
      return;
    }

    state.ws.addEventListener('open', () => { 
      state.connected = true;
      console.log('WebSocket connected for session:', state.sessionId);
    });
    
    state.ws.addEventListener('close', () => { 
      state.connected = false;
      console.log('WebSocket disconnected for session:', state.sessionId);
    });
    
    state.ws.addEventListener('error', (error) => { 
      console.error('WebSocket error for session:', state.sessionId, error);
      try{state.ws.close();}catch{} 
    });

    state.ws.addEventListener('message', (ev) => {
      let data;
      try { data = JSON.parse(ev.data); } catch { data = null; }
      if (!data || !data.content) return;

      // avoid double-render for my own echoes
      if (data.sender === state.username && data.content) return;

      renderMessage({
        sender: data.sender || 'Someone',
        content: data.content || '',
        sentAt: data.sentAt || Date.now(),
        room: data.room || state.room
      });
    });
  }

  // Contacts + whoami -> avatars and my name
  async function loadContacts() {
    try {
      const res = await fetch(withCtx('/api/contacts'), { 
        credentials: 'include',
        headers: {
          'X-Session-Id': state.sessionId
        }
      });
      if (!res.ok) return;
      const json = await res.json(); // { users: [{username, avatar}] }
      if (json && Array.isArray(json.users)) {
        state.contactMap = {};
        json.users.forEach(u => {
          state.contactMap[u.username] = u.avatar || null;
        });
        // Update the left list avatars if present
        document.querySelectorAll('.contact').forEach(c => {
          const name = c.dataset.username || c.textContent.trim();
          const av = c.querySelector('.contact-avatar');
          if (av) {
            const url = state.contactMap[name] || null;
            if (url) {
              av.style.backgroundImage = `url('${url}')`;
              av.textContent = '';
            } else {
              av.style.backgroundImage = 'none';
              av.textContent = initials(name);
            }
          }
        });
      }
    } catch(e) {
      console.error('Error loading contacts:', e);
    }
  }

  async function loadMe() {
    try {
      const res = await fetch(withCtx('/whoami'), { 
        credentials: 'include',
        headers: {
          'X-Session-Id': state.sessionId
        }
      });
      
      if (!res.ok) {
        console.error('Whoami request failed:', res.status);
        return;
      }
      
      const text = (await res.text()).trim();
      let me = null;
      try { me = JSON.parse(text); } catch (e) {
        console.error('Error parsing whoami response:', e, 'Response:', text);
      }
      
      if (me && typeof me === 'object') {
        if (me.username) {
          state.username = String(me.username).trim();
          console.log('Set username from whoami response:', state.username);
        }
        if (me.avatar) state.myAvatar = me.avatar;
      } else if (text) {
        state.username = text;
        console.log('Set username from whoami text response:', state.username);
      }
      
      if (state.username) {
        document.body.dataset.username = state.username;
        try { 
          sessionStorage.setItem('username', state.username); 
          sessionStorage.setItem('userAvatar', state.myAvatar || '');
        } catch (e) {
          console.error('Error saving to sessionStorage:', e);
        }
      }
      
      const headerAvatar = document.getElementById('myAvatar');
      if (headerAvatar && state.myAvatar) {
        headerAvatar.src = state.myAvatar;
      }
    } catch (e) {
      console.error('Error in loadMe:', e);
    }
  }

  // Wire UI
  if (sendBtn) {
    sendBtn.addEventListener('click', sendCurrentMessage);
  }
  
  if (messageInput) {
    messageInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendCurrentMessage();
      }
    });
  }

  if (contactsWrap) {
    contactsWrap.addEventListener('click', (e) => {
      const item = e.target.closest('.contact');
      if (!item) return;
      loadRoomFromContact(item);
      try { state.ws?.close(); } catch {}
      connectWs();
    });
  }

  // Init flow
  (async function init() {
    console.log('Initializing chat for session:', state.sessionId);
    
    // hydrate possible name from DOM/storage first (per-tab)
    state.username = document.body.dataset.username?.trim() ||
                     sessionStorage.getItem('username') ||
                     localStorage.getItem('username') ||
                     state.username;
                     
    state.myAvatar = sessionStorage.getItem('userAvatar') || null;

    console.log('Initial username:', state.username);
    
    await loadMe();        // ensure username (e.g., 'Nev') is set first
    await loadContacts();  // optional avatars

    const active = document.querySelector('.contact.active');
    loadRoomFromContact(active);

    connectWs();           // open WS only after we have a username
  })();
})();