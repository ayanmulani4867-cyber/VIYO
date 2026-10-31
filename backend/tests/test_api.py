import pytest
import pytest_asyncio
import uuid
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.core.database import engine, Base

@pytest_asyncio.fixture(scope="session", autouse=True)
async def prepare_database():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)

@pytest.mark.asyncio
async def test_health_check():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        response = await ac.get("/api/v1/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert data["app"] == "VIYO"

@pytest.mark.asyncio
async def test_auth_and_user_flows():
    tag = uuid.uuid4().hex[:6]
    u1_name = f"alice_{tag}"
    u1_email = f"alice_{tag}@viyo.io"
    u2_name = f"bob_{tag}"
    u2_email = f"bob_{tag}@viyo.io"

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        # 1. Register User 1 (Admin)
        u1_reg = await ac.post("/api/v1/auth/register", json={
            "email": u1_email,
            "username": u1_name,
            "password": "Password123!",
            "display_name": "Alice Cooper"
        })
        assert u1_reg.status_code == 201, u1_reg.text
        u1_data = u1_reg.json()
        token1 = u1_data["access_token"]
        user1_id = u1_data["user_id"]
        headers1 = {"Authorization": f"Bearer {token1}"}

        # 2. Register User 2 (Bob)
        u2_reg = await ac.post("/api/v1/auth/register", json={
            "email": u2_email,
            "username": u2_name,
            "password": "Password123!",
            "display_name": "Bob Builder"
        })
        assert u2_reg.status_code == 201, u2_reg.text
        token2 = u2_reg.json()["access_token"]
        user2_id = u2_reg.json()["user_id"]
        headers2 = {"Authorization": f"Bearer {token2}"}

        # 3. Test /auth/me
        me_resp = await ac.get("/api/v1/auth/me", headers=headers1)
        assert me_resp.status_code == 200
        assert me_resp.json()["username"] == u1_name
        assert me_resp.json()["is_admin"] is True

        # 4. Update profile
        prof_resp = await ac.put("/api/v1/users/profile", json={
            "bio": "Audio/Video enthusiast",
            "status_message": "Available for calls"
        }, headers=headers1)
        assert prof_resp.status_code == 200
        assert prof_resp.json()["bio"] == "Audio/Video enthusiast"

        # 5. Search users
        search_resp = await ac.get(f"/api/v1/users/search?q={u2_name}", headers=headers1)
        assert search_resp.status_code == 200
        assert len(search_resp.json()) >= 1
        assert search_resp.json()[0]["username"] == u2_name

        # 6. Contact request flow: Alice -> Bob
        req_resp = await ac.post("/api/v1/contacts/request", json={
            "receiver_username": u2_name
        }, headers=headers1)
        assert req_resp.status_code == 200
        req_id = req_resp.json()["id"]

        # Bob views pending requests
        pending_resp = await ac.get("/api/v1/contacts/requests/pending", headers=headers2)
        assert pending_resp.status_code == 200
        assert len(pending_resp.json()) == 1

        # Bob accepts contact request
        accept_resp = await ac.post(f"/api/v1/contacts/requests/{req_id}/accept", headers=headers2)
        assert accept_resp.status_code == 200

        # Alice verifies Bob in contacts
        contacts_resp = await ac.get("/api/v1/contacts", headers=headers1)
        assert contacts_resp.status_code == 200
        assert any(c["username"] == u2_name for c in contacts_resp.json())

        # 7. Create direct conversation & send message
        conv_resp = await ac.post("/api/v1/messaging/conversations", json={
            "type": "direct",
            "participant_user_ids": [user2_id]
        }, headers=headers1)
        assert conv_resp.status_code == 200
        conv_id = conv_resp.json()["id"]

        msg_resp = await ac.post(f"/api/v1/messaging/conversations/{conv_id}/messages", json={
            "content": "Hello Bob! Testing real-time messaging on VIYO.",
            "message_type": "text"
        }, headers=headers1)
        assert msg_resp.status_code == 200
        assert msg_resp.json()["content"] == "Hello Bob! Testing real-time messaging on VIYO."

        # Bob fetches messages
        msgs_resp = await ac.get(f"/api/v1/messaging/conversations/{conv_id}/messages", headers=headers2)
        assert msgs_resp.status_code == 200
        assert len(msgs_resp.json()) >= 1

        # 8. Call lifecycle: Alice calls Bob
        call_resp = await ac.post("/api/v1/calls/initiate", json={
            "receiver_id": user2_id,
            "call_type": "video"
        }, headers=headers1)
        assert call_resp.status_code == 200
        call_data = call_resp.json()
        assert call_data["status"] == "ringing"
        assert call_data["livekit_token"] is not None
        call_id = call_data["id"]

        # Bob accepts call
        call_acc_resp = await ac.post(f"/api/v1/calls/{call_id}/action", json={
            "action": "accept"
        }, headers=headers2)
        assert call_acc_resp.status_code == 200
        assert call_acc_resp.json()["status"] == "accepted"
        assert call_acc_resp.json()["livekit_token"] is not None

        # Alice ends call
        call_end_resp = await ac.post(f"/api/v1/calls/{call_id}/action", json={
            "action": "end"
        }, headers=headers1)
        assert call_end_resp.status_code == 200
        assert call_end_resp.json()["status"] == "ended"

        # Check call history
        history_resp = await ac.get("/api/v1/calls/history", headers=headers1)
        assert history_resp.status_code == 200
        assert len(history_resp.json()) >= 1

        # 9. Group Meeting flow
        mtg_resp = await ac.post("/api/v1/meetings", json={
            "title": "VIYO Architecture Sync"
        }, headers=headers1)
        assert mtg_resp.status_code == 200
        mtg_data = mtg_resp.json()
        mtg_code = mtg_data["meeting_code"]
        assert mtg_data["livekit_token"] is not None

        # Bob joins meeting by code
        join_resp = await ac.get(f"/api/v1/meetings/{mtg_code}", headers=headers2)
        assert join_resp.status_code == 200
        assert join_resp.json()["livekit_token"] is not None

        # 10. Admin moderation stats
        stats_resp = await ac.get("/api/v1/moderation/admin/stats", headers=headers1)
        assert stats_resp.status_code == 200
        stats = stats_resp.json()
        assert stats["total_users"] == 2
        assert stats["total_calls"] >= 1
        assert stats["total_messages"] >= 1
        assert stats["total_meetings"] >= 1

