import json
import logging
from typing import Dict, Set, Optional, Any
from fastapi import WebSocket
from datetime import datetime, timezone
import uuid

logger = logging.getLogger("viyo.websocket")

class ConnectionManager:
    def __init__(self):
        # Maps user_id (str) to set of active WebSocket connections (e.g. multiple tabs or devices)
        self.active_connections: Dict[str, Set[WebSocket]] = {}

    async def connect(self, user_id: str, websocket: WebSocket):
        await websocket.accept()
        if user_id not in self.active_connections:
            self.active_connections[user_id] = set()
        self.active_connections[user_id].add(websocket)
        logger.info(f"User {user_id} connected via WebSocket. Total devices: {len(self.active_connections[user_id])}")

    def disconnect(self, user_id: str, websocket: WebSocket):
        if user_id in self.active_connections:
            self.active_connections[user_id].discard(websocket)
            if not self.active_connections[user_id]:
                del self.active_connections[user_id]
        logger.info(f"User {user_id} disconnected from WebSocket.")

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

manager = ConnectionManager()

