import json
import logging
import time
import uuid
from typing import Dict, Set, Optional, Any
from datetime import datetime, timezone
from fastapi import WebSocket

logger = logging.getLogger("viyo.websocket")

class ConnectionManager:
    def __init__(self):
        # Maps user_id (str) to set of active WebSocket connections (e.g. multiple tabs or devices)
        self.active_connections: Dict[str, Set[WebSocket]] = {}
        self.last_heartbeat: Dict[str, float] = {}

    async def connect(self, user_id: str, websocket: WebSocket):
        await websocket.accept()
        if user_id not in self.active_connections:
            self.active_connections[user_id] = set()
        self.active_connections[user_id].add(websocket)
        self.last_heartbeat[user_id] = time.time()
        logger.info(f"User {user_id} connected via WebSocket. Active sessions: {len(self.active_connections[user_id])}")

    def disconnect(self, user_id: str, websocket: WebSocket):
        if user_id in self.active_connections:
            self.active_connections[user_id].discard(websocket)
            if not self.active_connections[user_id]:
                del self.active_connections[user_id]
                self.last_heartbeat.pop(user_id, None)
        logger.info(f"User {user_id} disconnected from WebSocket session.")

    def record_heartbeat(self, user_id: str):
        self.last_heartbeat[user_id] = time.time()

    def is_user_online(self, user_id: str) -> bool:
        return user_id in self.active_connections and len(self.active_connections[user_id]) > 0

    async def send_personal_message(self, user_id: str, message: Dict[str, Any]):
        """Delivers a real-time event to all active sessions of a given user."""
        if user_id in self.active_connections:
            data_str = json.dumps(message)
            dead_sockets = set()
            for connection in self.active_connections[user_id]:
                try:
                    await connection.send_text(data_str)
                except Exception as e:
                    logger.warning(f"Error sending message to user {user_id}: {e}")
                    dead_sockets.add(connection)
            for dead in dead_sockets:
                self.disconnect(user_id, dead)

    async def broadcast_to_users(self, user_ids: list[str], message: Dict[str, Any]):
        for uid in user_ids:
            await self.send_personal_message(uid, message)

    async def broadcast_presence_to_contacts(self, user_id: str, is_online: bool, last_seen: Optional[datetime], db_session):
        try:
            from app.models import Contact
            from sqlalchemy.future import select
            # Notify users who have this user in their contacts
            uid = uuid.UUID(user_id) if isinstance(user_id, str) else user_id
            stmt = select(Contact.user_id).where(Contact.contact_user_id == uid)
            res = await db_session.execute(stmt)
            contact_user_ids = [str(cid) for cid in res.scalars().all()]

            if contact_user_ids:
                presence_event = {
                    "event": "user_presence",
                    "user_id": str(user_id),
                    "is_online": is_online,
                    "last_seen": last_seen.isoformat() if last_seen else None
                }
                await self.broadcast_to_users(contact_user_ids, presence_event)
        except Exception as e:
            logger.warning(f"Failed to broadcast presence for {user_id}: {e}")

manager = ConnectionManager()
