# VIYO Deployment Guide

**Application:** VIYO  
**Tagline:** See. Talk. Connect.  
**Budget Target:** ₹0 Zero-Budget Local & Free-Tier VPS Ready

---

## 1. Zero-Budget Local Deployment

For local testing, cross-platform pairing, and development:
- **FastAPI Backend:** Runs locally on Python 3.13 (`sqlite+aiosqlite:///./viyo.db` or PostgreSQL).
- **Web Client:** Runs on Vite or served as static distribution via Nginx.
- **Android App:** Built via Gradle and deployed to physical Android device (via USB/Wi-Fi adb) or Android Studio Emulator.
- **Media SFU:** Self-hosted LiveKit Community Edition (`docker run --rm -p 7880:7880 -p 7881:7881 -p 50000-50100:50000-50100/udp livekit/livekit-server --dev`).

### Step-by-Step Local Launch:

#### 1. Start the LiveKit SFU:
```bash
docker run --rm -p 7880:7880 -p 7881:7881 -p 50000-50100:50000-50100/udp livekit/livekit-server --dev
```

#### 2. Start the Backend:
```bash
cd backend
python -m venv venv
# On Windows: .\venv\Scripts\Activate.ps1
# On Linux/macOS: source venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

#### 3. Start the Web Client:
```bash
cd web
npm install
npm run dev
```

#### 4. Build and Install Android APK:
```bash
cd android
./gradlew assembleDebug
# Install to connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 2. Production Docker Deployment (VPS)

For hosting VIYO on a VPS (e.g. Hetzner, DigitalOcean, Oracle Cloud Free Tier):

1. Clone repository to server:
   ```bash
   git clone <repo_url> VIYO
   cd VIYO/infrastructure/docker
   ```
2. Configure `.env`:
   ```bash
   POSTGRES_USER=viyo
   POSTGRES_PASSWORD=generate_a_very_strong_password_here
   POSTGRES_DB=viyodb
   SECRET_KEY=generate_a_cryptographically_secure_random_key
   LIVEKIT_API_KEY=devkey
   LIVEKIT_API_SECRET=generate_strong_livekit_secret
   ```
3. Run with Docker Compose:
   ```bash
   docker compose up -d --build
   ```
4. Verify services:
   ```bash
   docker compose ps
   curl http://localhost/api/v1/health
   ```

