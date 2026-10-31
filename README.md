# VIYO — Together, Wherever We Are.
### Tagline: See. Talk. Connect.

![VIYO Logo](web/public/viyo-logo.svg)

VIYO is a modern cross-platform family and public communication platform engineered for high-fidelity audio/video calling, group conferencing, and private instant messaging across Android and modern web clients.

---

## Key Features

- **Real One-to-One Audio & Video Calling:**
  - Real-time WebRTC media streams powered by LiveKit Community Edition SFU.
  - Call lifecycle: initiate, ring, accept, reject, cancel, end with synchronized state.
  - Front/rear camera toggle, microphone mute, speaker controls, and call duration counter.
  - DTLS-SRTP media encryption with adaptive bitrate streaming.

- **Group Video Meetings:**
  - Instant meeting creation with alphanumeric 9-character code (e.g., `abc-defg-hij`).
  - Join via meeting code or direct browser link.
  - Multi-party video grid, participant indicators, and waiting room support.

- **Private & Family Messaging:**
  - One-to-one and group conversation threads.
  - Media attachments (photos, voice notes, documents).
  - Authenticated WebSocket real-time delivery, typing indicators, and delivery state.

- **Contacts & Directory Discovery:**
  - Search by username and display name.
  - Contact request lifecycle (send, accept, decline, remove).
  - Server-side privacy enforcement (`everyone`, `contacts_only`, `nobody`).
  - Block/unblock protection.

- **Platform Administration & Moderation:**
  - First registered user automatically provisioned with administrator role.
  - Platform metrics dashboard: active users, total calls, total messages, total meetings.
  - Abuse reporting and user account suspension.

---

## Technology Stack

- **Android:** Kotlin, Jetpack Compose, Material 3, MVVM Architecture, Retrofit 2, Moshi, LiveKit Android SDK (`livekit-android`).
- **Web:** React 19, TypeScript, Vite, Tailwind CSS, `@livekit/components-react`, `livekit-client`, PWA Manifest & Service Worker.
- **Backend:** Python 3.13, FastAPI, SQLAlchemy 2.0 (Async), PostgreSQL / SQLite, Alembic migrations, Redis Pub/Sub, WebSockets, LiveKit Server SDK (`livekit-api`), Bcrypt, PyJWT.
- **Media Engine:** LiveKit Community Edition SFU, WebRTC, Opus Audio, VP8/H.264 Video.
- **Infrastructure:** Docker, Docker Compose, Nginx.

---

## Quick Start (Zero-Budget Local Environment)

### 1. Start the LiveKit SFU:
```bash
docker run --rm -p 7880:7880 -p 7881:7881 -p 50000-50100:50000-50100/udp livekit/livekit-server --dev
```

### 2. Start the Backend API:
```bash
cd backend
python -m venv venv
# On Windows: .\venv\Scripts\Activate.ps1
# On Linux/macOS: source venv/bin/activate
pip install -r requirements.txt
python -m app.core.init_db
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```
API Documentation will be live at `http://127.0.0.1:8000/docs`.

### 3. Start the Web Client:
```bash
cd web
npm install
npm run dev
```
Web client will be live at `http://localhost:5173`.

### 4. Build Android Application:
```bash
cd android
./gradlew assembleDebug
```
The debug APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## Running the Automated Test Suite

- **Backend Tests:**
  ```bash
  cd backend
  pytest -o pythonpath=. -v tests/test_api.py
  ```
- **Cross-Platform Integration Tests:**
  ```bash
  cd backend
  pytest -o pythonpath=. -v ../tests/cross_platform/test_cross_platform.py
  ```
- **Web TypeScript Build Test:**
  ```bash
  cd web
  npm run build
  ```

---

## License & Privacy

Built with open-source technologies under zero-budget constraints. Media streams and family communication data are strictly private and never collected for AI training or analytics.

