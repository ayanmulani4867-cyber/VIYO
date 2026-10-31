import livekit.api as lk
from datetime import timedelta
from typing import Optional
from app.core.config import settings

class LiveKitService:
    def __init__(self):
        self.api_key = settings.LIVEKIT_API_KEY
        self.api_secret = settings.LIVEKIT_API_SECRET
        self.url = settings.LIVEKIT_URL

    def generate_token(
        self,
        room_name: str,
        participant_identity: str,
        participant_name: str,
        is_host: bool = False,
        ttl_minutes: int = 60
    ) -> str:
        """Generates a cryptographically signed, short-lived LiveKit access token with strict scoped grants."""
        token = lk.AccessToken(self.api_key, self.api_secret)
        token.with_identity(str(participant_identity))
        token.with_name(str(participant_name))
        token.with_ttl(timedelta(minutes=ttl_minutes))

        grants = lk.VideoGrants(
            room_join=True,
            room=room_name,
            can_publish=True,
            can_subscribe=True,
            can_publish_data=True,
            room_admin=is_host
        )
        token.with_grants(grants)
        return token.to_jwt()

    async def delete_room(self, room_name: str) -> bool:
        """Terminates a LiveKit room explicitly."""
        try:
            lk_client = lk.LiveKitAPI(self.url, self.api_key, self.api_secret)
            await lk_client.room.delete_room(lk.DeleteRoomRequest(room=room_name))
            await lk_client.aclose()
            return True
        except Exception as e:
            # Non-fatal if room already closed or livekit server unreachable
            return False

livekit_service = LiveKitService()

