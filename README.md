# UniConnect Live Chat 💬⚡

A lightweight **Jakarta Servlet + WebSocket** group chat for university teams. Users can **register & log in**, chat in **real time**, and share **photos/videos** captured in the browser. Frontend is plain **HTML/CSS/JS** (no frameworks), so it’s easy to read and extend.

This repo is perfect for demos and coursework: deploy the WAR to a Jakarta-compatible server (**GlassFish 7 / Payara 6**) and you’re chatting in minutes. The code shows clean WebSocket usage, session-based auth, and client media capture.

---

## ✨ Features

* 💬 **Real-time group chat** via `@ServerEndpoint("/chat")`
* 🔐 **Auth flow**: registration → login → session → logout
* 👤 **WhoAmI servlet** to display the **actual username** (no more `User####`)
* 📸 **Camera & recording** page (photo/video) with inline preview
* 📎 **Send media to chat** (base64 data URL for easy demoing)
* 🧩 **Plain HTML/CSS/JS** frontend (easy to follow & customize)

---

## 🧱 Tech Stack

* **Backend:** Jakarta Servlet & Jakarta WebSocket (Java 17+)
* **Server:** **GlassFish 7+** / **Payara 6+** (Jakarta EE 10, includes Tyrus WebSocket)
* **Frontend:** HTML5, CSS, vanilla JS (`fetch`, `WebSocket`, `MediaDevices`, `MediaRecorder`)
* **Build:** Maven (standard `src/main/java` + `src/main/webapp` structure)

---

## 📁 Project Structure

```
src/
  main/
    java/
      com/uniconnect/servlets/
        LoginServlet.java
        LogoutServlet.java
        WhoAmIServlet.java         // returns the logged-in username
        (optional) ContactsServlet.java
      com/uniconnect/ws/
        ChatEndpoint.java          // @ServerEndpoint("/chat")
    webapp/
      login.html
      registration.html
      conversations.html           // main chat UI
      camera.html                  // capture & send media
      home.html
      chat.js                      // chat logic (WS + UI)
      styles.css (if present)
```

> App context is typically `/UniConnectLiveChat`, so URLs look like `/UniConnectLiveChat/login.html`, `/UniConnectLiveChat/chat` (WS), etc.

---

## 📷 Screenshots

<img width="1899" height="904" alt="{C4A27F45-F269-4F20-B9AD-6E26BA003DC6}" src="https://github.com/user-attachments/assets/a80fe2f4-c0a5-4e8a-8237-1c8a07a7564d" />

<img width="1891" height="906" alt="{74192CAF-04C2-4336-9734-C966B16D7367}" src="https://github.com/user-attachments/assets/bdee476d-fe61-4107-b564-dd7c29536099" />

<img width="1883" height="912" alt="{7204213D-64FB-4DAC-9693-7C85C239DF99}" src="https://github.com/user-attachments/assets/5f9dcf0e-3be4-4a74-97e3-60d49dfccfdb" />

<img width="1896" height="917" alt="{2228BCD3-2AF4-4D93-B981-B2C98C29FFCC}" src="https://github.com/user-attachments/assets/f4a941ce-f16a-4609-b381-e5edea11454e" />

---

## 🚀 Deploy on GlassFish / Payara

> Tested with **GlassFish 7+** (Jakarta EE 10) and **Payara 6+**.

### 1) Build

```bash
mvn clean package
```

Produces `target/UniConnectLiveChat.war`.

### 2) Start the domain

```bash
asadmin start-domain domain1
```

### 3) Deploy the WAR

**Default context-root (from WAR name):**

```bash
asadmin deploy --force=true target/UniConnectLiveChat.war
```

**Custom context-root (recommended):**

```bash
asadmin deploy \
  --force=true \
  --contextroot /UniConnectLiveChat \
  target/UniConnectLiveChat.war
```

### 4) Open the app

* Login: `http://localhost:8080/UniConnectLiveChat/login.html`
* Chat: `http://localhost:8080/UniConnectLiveChat/conversations.html`
* Camera: `http://localhost:8080/UniConnectLiveChat/camera.html`

### 5) Useful admin commands

```bash
# list apps
asadmin list-applications

# redeploy quickly
asadmin redeploy UniConnectLiveChat

# undeploy
asadmin undeploy UniConnectLiveChat

# stop/start server
asadmin stop-domain domain1
asadmin start-domain domain1
```

---

## 🔌 Endpoints

**HTTP (Servlets)**

* `POST /login` — authenticate & create session
* `GET  /logout` — invalidate session and redirect to login
* `GET  /whoami` — returns the display name for the current session (text or JSON)
* `GET  /api/contacts` — (optional) list of known users/avatars

**WebSocket**

* `WS /chat?room=<ROOM>&sender=<USERNAME>` — main realtime channel

---

## 🧠 How It Works

1. **Login** stores your display name in the **HTTP session**.
2. **conversations.html** calls **`/whoami` first**, assigns `state.username`, then opens the WS:

   ```
   ws://localhost:8080/UniConnectLiveChat/chat?room=<ROOM>&sender=<USERNAME>
   ```
3. **ChatEndpoint** reads the `sender` from the handshake and tags messages.
4. **camera.html** captures photo/video → places a JSON payload in `sessionStorage.pendingMedia` → redirects to chat.
5. The chat page detects `pendingMedia`, sends it over WS, and renders **image/video** bubbles.

---

## 🧪 Demo Tips

* To simulate **two users**, use **two browsers** (e.g., Chrome + Firefox) or one normal + one incognito so they don’t share a session.
* In DevTools → **Network → WS**, check the handshake URL contains `&sender=<YourName>` for each browser.

---

## 🛠️ Troubleshooting

**❌ “User####” shows instead of my name**

* Ensure the chat page calls **`/whoami` before `connectWs()`** so `state.username` is set.
* Confirm the WS handshake URL includes `&sender=<YourName>`.

**❌ Both browsers show the same name**

* They share a session; use different browsers/profiles or log out in one before logging in as another user.

**❌ WebSocket not connecting**

* Make sure you’re on **GlassFish 7 / Payara 6** (Jakarta namespaces).
* If you have an auth filter, **allow `Upgrade: websocket`** and don’t block `/chat`.
* Double-check your context-root and client URLs.

**❌ Media didn’t appear**

* Keep clips short (base64 can be big).
* Ensure `sessionStorage.pendingMedia` is written in `camera.html` and the chat page sends it **after** `state.ws.readyState === 1`.

---

## 📌 Quick Commands (GlassFish)

```bash
mvn clean package
asadmin start-domain domain1
asadmin deploy --force=true --contextroot /UniConnectLiveChat target/UniConnectLiveChat.war

# open:
# http://localhost:8080/UniConnectLiveChat/login.html
# http://localhost:8080/UniConnectLiveChat/conversations.html
# http://localhost:8080/UniConnectLiveChat/camera.html
```
