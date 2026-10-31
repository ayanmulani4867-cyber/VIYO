# VIYO — API & Cloud Services Setup Guide

This guide walks you through connecting external cloud providers (LiveKit, Supabase PostgreSQL, Firebase, Render) to the VIYO cross-platform system.

---

## 1. Supabase PostgreSQL Setup

1. Create a project at [supabase.com](https://supabase.com).
2. Go to **Project Settings** -> **Database**.
3. Under **Connection string**, select **URI** and copy the URI format:
   ```text
   postgresql://postgres:[YOUR-PASSWORD]@db.[YOUR-PROJECT-REF].supabase.co:5432/postgres
   ```
4. Configure in `backend/.env` or Render environment:
   ```env
   DATABASE_URL=postgresql+asyncpg://postgres:<PASSWORD>@db.<PROJECT_REF>.supabase.co:5432/postgres
   ```
   *(VIYO automatically enforces SSL and handles connection pooling via asyncpg)*.
5. Run migrations:
   ```bash
   cd backend
   alembic upgrade head
   ```

---

## 2. LiveKit Cloud Setup (WebRTC SFU)

1. Create a free project at [livekit.io/cloud](https://cloud.livekit.io).
2. Under **Project Settings** -> **Keys**, generate an API key and secret.
3. Configure in `backend/.env`:
   ```env
   LIVEKIT_URL=wss://<YOUR_PROJECT_ID>.livekit.cloud
   LIVEKIT_API_KEY=<YOUR_API_KEY>
   LIVEKIT_API_SECRET=<YOUR_API_SECRET>
   ```
4. In `web/.env`:
   ```env
   VITE_LIVEKIT_URL=wss://<YOUR_PROJECT_ID>.livekit.cloud
   ```

---

## 3. Firebase & Push Notifications

### Android (FCM)
1. In the [Firebase Console](https://console.firebase.google.com), create an Android app with package name `com.viyo.app`.
2. Download `google-services.json` and place it in `android/app/google-services.json`.
3. Notifications are handled automatically by `com.viyo.app.notification.ViyoFirebaseMessagingService`.

### Web Push (VAPID)
1. In Firebase Console -> **Project Settings** -> **Cloud Messaging** -> **Web configuration**, generate a Web Push key pair (VAPID).
2. Configure in `web/.env`:
   ```env
   VITE_VAPID_PUBLIC_KEY=<YOUR_VAPID_PUBLIC_KEY>
   ```

---

## 4. Render Deployment (1-Click Blueprint)

1. Fork or push this repository to GitHub.
2. In [Render Dashboard](https://dashboard.render.com), click **New +** -> **Blueprint**.
3. Select your repository. Render will automatically parse [`render.yaml`](file:///C:/Users/ayanm/.gemini/antigravity/scratch/VIYO/render.yaml):
   - **`viyo-backend`**: Builds with Python 3.13, runs `alembic upgrade head`, and serves FastAPI with health monitoring at `/api/v1/health`.
   - **`viyo-web`**: Builds with React 19/Vite and deploys as a static SPA.
4. Set your production environment secrets (`DATABASE_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `VAPID_PUBLIC_KEY`).
