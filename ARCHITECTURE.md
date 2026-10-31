# VIYO Architecture Documentation

**Tagline:** See. Talk. Connect.  
**Version:** 1.0.0  
**Status:** In Active Development

---

## 1. High-Level System Architecture

VIYO is an ultra-reliable, public real-time communication platform engineered for high-fidelity audio/video calling, group conferencing, and private instant messaging across Android and modern web clients.

```
+-------------------------------------------------------------------------------+
|                                  CLIENT LAYER                                 |
|                                                                               |
|   +------------------------------------+   +------------------------------+   |
|   |         Android Application        |   |      React 19 / Vite Web     |   |
|   |   (Kotlin + Compose + LiveKit)     |   |   (Tailwind + LiveKit React) |   |
|   +-----------------+------------------+   +--------------+---------------+   |
+---------------------|-------------------------------------|-------------------+
                      |                                     |
                      | HTTPS REST / WSS Events             | HTTPS REST / WSS Events
                      v                                     v
+-------------------------------------------------------------------------------+
|                                BACKEND CLUSTER                                |
|                                                                               |
|                   FastAPI Async Application (Python 3.13)                     |
|                                                                               |
|   +----------------------+  +---------------------+  +--------------------+   |
|   | Authentication Core  |  | Messaging & WS Core |  | Call Signaling Hub|   |
|   +----------------------+  +---------------------+  +--------------------+   |
|   | Profiles & Discovery |  | Meeting Coordinator |  | Moderation & Audit |   |
|   +----------------------+  +---------------------+  +--------------------+   |
|                                                                               |
+-----------------------+-------------------------+------------------+----------+
                        |                         |                  |
           State & Data |             Media Token |     Push Events  |
                        v                         v                  v
+-----------------------------+  +-----------------------+  +-------------------+
|      DATA PERSISTENCE       |  |  SELECTIVE FORWARDING |  | PUSH NOTIFICATION |
|                             |  |      UNIT (SFU)       |  |     DELIVERY      |
|  - PostgreSQL 16 (Tables)   |  |                       |  |                   |
|  - Redis 7 (Presence/Cache) |  |  LiveKit CE WebRTC    |  |  Firebase Cloud   |
|  - Object Storage (Media)   |  |  Adaptive Simulcast   |  |  Messaging (FCM)  |
+-----------------------------+  +-----------------------+  +-------------------+
```

---

## 2. Core Subsystems

### 2.1 Backend Architecture
FastAPI provides asynchronous request handling, WebSockets for bi-directional signaling, and integration with PostgreSQL via SQLAlchemy 2.0 (async).
- **Security & Auth:** Firebase Auth token validation with self-contained JWT fallback for zero-budget local environments.
- **Signaling:** WebSockets route call invitations, accept/reject decisions, ICE state, typing indicators, and message events.
- **Token Generation:** Server signs short-lived (5-15 min) LiveKit tokens bound to specific room IDs and user identities.

### 2.2 WebRTC Media Engine (LiveKit SFU)
- WebRTC media traffic (audio/video/screen share) flows directly through the LiveKit SFU server over UDP/DTLS-SRTP, bypassing application API servers.
- Codecs: VP8/H.264 for video, Opus for audio. Adaptive bitrate automatically scales based on network conditions.

### 2.3 Android Client Architecture
- Kotlin + Jetpack Compose with Material 3 styling (dark and light themes).
- State management: MVVM with StateFlow / SharedFlow.
- Networking: Retrofit 2 + OkHttp + Moshi/Kotlinx Serialization.
- WebRTC: LiveKit Android SDK (`io.livekit:livekit-android`).

### 2.4 Web Client Architecture
- React 19 + TypeScript + Vite.
- Styling: Tailwind CSS configured with a sleek dark slate background and violet/indigo branding accents.
- Media components: `@livekit/components-react` and `livekit-client`.
- Progressive Web App (PWA) with offline shell caching and Web Push support.

