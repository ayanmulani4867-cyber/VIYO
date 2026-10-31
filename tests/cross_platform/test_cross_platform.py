import pytest
import pytest_asyncio
import uuid
from httpx import AsyncClient, ASGITransport
import sys
import os

# Ensure backend app is importable
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "backend")))

from app.main import app
from app.core.database import engine, Base

@pytest_asyncio.fixture(scope="session", autouse=True)
async def init_test_db():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)

@pytest.mark.asyncio
async def test_cross_platform_calling_and_messaging():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # Create Android Client User
        android_reg = await client.post("/api/v1/auth/register", json={
            "email": "android_user@viyo.io",
            "username": "android_user",
            "password": "Password123!",
            "display_name": "Android Device"
        })
        assert android_reg.status_code == 201
        android_token = android_reg.json()["access_token"]
        android_id = android_reg.json()["user_id"]
        android_headers = {"Authorization": f"Bearer {android_token}"}

        # Create Web Client User
        web_reg = await client.post("/api/v1/auth/register", json={
            "email": "web_user@viyo.io",
            "username": "web_user",
            "password": "Password123!",
            "display_name": "Web Browser"
        })
        assert web_reg.status_code == 201
        web_token = web_reg.json()["access_token"]
        web_id = web_reg.json()["user_id"]
        web_headers = {"Authorization": f"Bearer {web_token}"}

        # Register Device Tokens (FCM)
        dev_res = await client.post("/api/v1/notifications/device", json={
            "fcm_token": "mock_fcm_android_token_12345",
            "device_type": "android",
            "device_name": "Pixel 9 Pro"
        }, headers=android_headers)
        assert dev_res.status_code == 200

        # TEST 1: Android to Web Video Call Initiation
        call_init = await client.post("/api/v1/calls/initiate", json={
            "receiver_id": web_id,
            "call_type": "video"
        }, headers=android_headers)
        assert call_init.status_code == 200
        call_data = call_init.json()
        assert call_data["status"] == "ringing"
        assert call_data["livekit_token"] is not None
        call_id = call_data["id"]

        # TEST 2: Web User Accepts Call
        call_accept = await client.post(f"/api/v1/calls/{call_id}/action", json={
            "action": "accept"
        }, headers=web_headers)
        assert call_accept.status_code == 200
        assert call_accept.json()["status"] == "accepted"
        assert call_accept.json()["livekit_token"] is not None

        # TEST 3: Termination
        call_end = await client.post(f"/api/v1/calls/{call_id}/action", json={
            "action": "end"
        }, headers=android_headers)
        assert call_end.status_code == 200
        assert call_end.json()["status"] == "ended"

        # TEST 4: Web to Android Voice-Only Call
        voice_init = await client.post("/api/v1/calls/initiate", json={
            "receiver_id": android_id,
            "call_type": "voice"
        }, headers=web_headers)
        assert voice_init.status_code == 200
        assert voice_init.json()["call_type"] == "voice"
        assert voice_init.json()["livekit_token"] is not None

        # TEST 5: Group Meeting Multi-Party Join
        mtg_res = await client.post("/api/v1/meetings", json={
            "title": "Cross Platform Multi-Party Conference"
        }, headers=web_headers)
        assert mtg_res.status_code == 200
        code = mtg_res.json()["meeting_code"]

        # Android User Joins Group Meeting via Code
        join_res = await client.get(f"/api/v1/meetings/{code}", headers=android_headers)
        assert join_res.status_code == 200
        assert join_res.json()["livekit_token"] is not None

        # TEST 6: Privacy Restrictions Enforcement
        # Set Web User privacy to nobody
        priv_res = await client.put("/api/v1/users/settings", json={
            "privacy_calls": "nobody"
        }, headers=web_headers)
        assert priv_res.status_code == 200

        # Android user tries to call Web user -> 403 Forbidden
        blocked_call = await client.post("/api/v1/calls/initiate", json={
            "receiver_id": web_id,
            "call_type": "video"
        }, headers=android_headers)
        assert blocked_call.status_code == 403

