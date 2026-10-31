# VIYO Environment Setup Guide

**Application:** VIYO  
**Tagline:** See. Talk. Connect.

---

## 1. Prerequisites

- **Python:** 3.11+ (Python 3.13 tested)
- **Node.js:** v20+ (Node v24 tested) & npm 10+
- **JDK:** OpenJDK 21 or Android Studio JBR
- **Android SDK:** API 26 (Min) to API 36 (Compile)
- **Docker / Docker Compose:** (Optional for containerized deployment)

---

## 2. Directory Structure

```
VIYO/
├── backend/                  # FastAPI Application, Alembic, SQLAlchemy models, WebSockets
│   ├── app/
│   │   ├── core/            # Config, DB engine, JWT security, Auth dependencies
│   │   ├── modules/         # Auth, users, contacts, messaging, calls, meetings, moderation
│   │   └── integrations/    # LiveKit server SDK, WebSocket connection manager
│   ├── tests/               # Backend pytest suite
│   ├── requirements.txt
│   └── alembic.ini
├── android/                  # Native Kotlin Jetpack Compose Android Application
│   ├── app/
│   │   ├── src/main/java/   # Compose screens, MVVM ViewModel, LiveKit Android SDK
│   │   └── build.gradle.kts
│   └── gradle/
├── web/                      # React 19 + TypeScript + Vite + Tailwind CSS PWA
│   ├── src/
│   │   ├── components/      # Video conference grid, call dialogs, controls
│   │   ├── pages/           # Home, Chats, Calls, Meetings, Contacts, Profile, Settings
│   │   ├── store/           # AuthContext, CallContext
│   │   └── services/        # REST client, WebSocket client
│   └── package.json
├── infrastructure/           # Docker Compose & LiveKit CE server configuration
└── tests/                    # Cross-platform integration tests
```

---

## 3. Environment Variables (`.env`)

Create `.env` inside `backend/`:
```ini
ENVIRONMENT=development
DEBUG=True
SECRET_KEY=viyo-super-secret-key-production-ready
DATABASE_URL=sqlite+aiosqlite:///./viyo.db
LIVEKIT_URL=ws://localhost:7880
LIVEKIT_API_KEY=devkey
LIVEKIT_API_SECRET=secret
```

