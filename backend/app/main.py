import os
import json
from datetime import datetime, timezone
from contextlib import asynccontextmanager
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Query, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.core.config import settings
from app.core.database import engine, Base, AsyncSessionLocal
from app.core.security import decode_access_token
from app.integrations.websocket_manager import manager
from app.models import UserProfile

# Import routers
from app.modules.authentication.routes import router as auth_router
from app.modules.users.routes import router as users_router
from app.modules.contacts.routes import router as contacts_router
from app.modules.messaging.routes import router as messaging_router
from app.modules.calls.routes import router as calls_router
from app.modules.meetings.routes import router as meetings_router
from app.modules.notifications.routes import router as notifications_router
from app.modules.moderation.routes import router as moderation_router

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Ensure upload directory exists
    os.makedirs(settings.UPLOAD_DIR, exist_ok=True)
    # Ensure database schema is ready
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield
    await engine.dispose()

app = FastAPI(
    title=settings.PROJECT_NAME,
    description="VIYO: High-fidelity public cross-platform video calling, meetings, and messaging platform.",
    version=settings.VERSION,
    lifespan=lifespan
)

# CORS Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Serve uploaded media files
os.makedirs(settings.UPLOAD_DIR, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=settings.UPLOAD_DIR), name="uploads")

# Health Check
@app.get("/health")
@app.get("/api/v1/health")
async def health_check():
    return {
        "status": "healthy",
        "app": settings.PROJECT_NAME,
        "tagline": settings.TAGLINE,
        "version": settings.VERSION,
        "livekit_url": settings.LIVEKIT_URL
    }

# Include API Routers
app.include_router(auth_router, prefix=settings.API_V1_STR)
app.include_router(users_router, prefix=settings.API_V1_STR)
app.include_router(contacts_router, prefix=settings.API_V1_STR)
app.include_router(messaging_router, prefix=settings.API_V1_STR)
app.include_router(calls_router, prefix=settings.API_V1_STR)
app.include_router(meetings_router, prefix=settings.API_V1_STR)
app.include_router(notifications_router, prefix=settings.API_V1_STR)
app.include_router(moderation_router, prefix=settings.API_V1_STR)

# Real-Time WebSocket Signaling & Messaging Endpoint
@app.websocket("/api/v1/ws")
async def websocket_endpoint(
    websocket: WebSocket,
    token: str = Query(...)
):
    payload = decode_access_token(token)
    if not payload or not payload.get("sub"):
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    user_id = payload["sub"]
    await manager.connect(user_id, websocket)

    # Set user online in database
    async with AsyncSessionLocal() as session:
        from sqlalchemy.future import select
        res = await session.execute(select(UserProfile).where(UserProfile.user_id == user_id))
        p = res.scalars().first()
        if p:
            p.is_online = True
            p.last_seen = datetime.now(timezone.utc)
            await session.commit()

    try:
        while True:
            data = await websocket.receive_text()
            try:
                msg_data = json.loads(data)
                action = msg_data.get("action")
                
                # Handle typing indicator
                if action == "typing":
                    conv_id = msg_data.get("conversation_id")
                    target_ids = msg_data.get("participant_ids", [])
                    is_typing = msg_data.get("is_typing", True)
                    await manager.broadcast_to_users(
                        target_ids,
                        {
                            "event": "user_typing",
                            "conversation_id": conv_id,
                            "user_id": user_id,
                            "is_typing": is_typing
                        }
                    )
                # Keep-alive heartbeat
                elif action == "ping":
                    await websocket.send_text(json.dumps({"event": "pong"}))
            except json.JSONDecodeError:
                pass

    except WebSocketDisconnect:
        manager.disconnect(user_id, websocket)
        if not manager.is_user_online(user_id):
            async with AsyncSessionLocal() as session:
                from sqlalchemy.future import select
                res = await session.execute(select(UserProfile).where(UserProfile.user_id == user_id))
                p = res.scalars().first()
                if p:
                    p.is_online = False
                    p.last_seen = datetime.now(timezone.utc)
                    await session.commit()

