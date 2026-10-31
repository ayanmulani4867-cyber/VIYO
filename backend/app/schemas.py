from pydantic import BaseModel, EmailStr, Field, ConfigDict
from typing import Optional, List, Dict, Any
from datetime import datetime
import uuid

# --- AUTH & USERS ---
class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user_id: str
    username: str

class TokenPayload(BaseModel):
    sub: Optional[str] = None
    exp: Optional[int] = None

class UserRegister(BaseModel):
    email: EmailStr
    username: str = Field(..., min_length=3, max_length=50, pattern=r"^[a-zA-Z0-9_.-]+$")
    password: str = Field(..., min_length=6)
    display_name: str = Field(..., min_length=1, max_length=100)

class UserLogin(BaseModel):
    username_or_email: str
    password: str

class FirebaseAuthRequest(BaseModel):
    id_token: str
    username: Optional[str] = None
    display_name: Optional[str] = None

class UserProfileOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    display_name: str
    avatar_url: Optional[str] = None
    bio: Optional[str] = None
    status_message: Optional[str] = None
    is_online: bool
    last_seen: Optional[datetime] = None

class UserSettingsOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    privacy_last_seen: str
    privacy_calls: str
    privacy_profile_photo: str
    allow_incoming_calls: bool
    camera_enabled_by_default: bool
    microphone_enabled_by_default: bool
    push_notifications_enabled: bool

class UserSettingsUpdate(BaseModel):
    privacy_last_seen: Optional[str] = None
    privacy_calls: Optional[str] = None
    privacy_profile_photo: Optional[str] = None
    allow_incoming_calls: Optional[bool] = None
    camera_enabled_by_default: Optional[bool] = None
    microphone_enabled_by_default: Optional[bool] = None
    push_notifications_enabled: Optional[bool] = None

class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    email: EmailStr
    username: str
    is_active: bool
    is_verified: bool
    is_admin: bool
    created_at: datetime
    profile: Optional[UserProfileOut] = None
    settings: Optional[UserSettingsOut] = None

class UserProfileUpdate(BaseModel):
    display_name: Optional[str] = None
    bio: Optional[str] = None
    status_message: Optional[str] = None
    avatar_url: Optional[str] = None

class UserSearchOut(BaseModel):
    id: uuid.UUID
    username: str
    display_name: str
    avatar_url: Optional[str] = None
    is_online: bool
    last_seen: Optional[datetime] = None

# --- CONTACTS ---
class ContactRequestCreate(BaseModel):
    receiver_username: str

class ContactRequestOut(BaseModel):
    id: uuid.UUID
    sender_id: uuid.UUID
    receiver_id: uuid.UUID
    status: str
    created_at: datetime
    sender_username: Optional[str] = None
    sender_display_name: Optional[str] = None
    sender_avatar_url: Optional[str] = None

class ContactOut(BaseModel):
    id: uuid.UUID
    contact_user_id: uuid.UUID
    username: str
    display_name: str
    avatar_url: Optional[str] = None
    is_online: bool
    last_seen: Optional[datetime] = None
    nickname: Optional[str] = None
    created_at: datetime

class BlockUserRequest(BaseModel):
    user_id: uuid.UUID
    reason: Optional[str] = None

# --- MESSAGING ---
class AttachmentOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    file_url: str
    file_name: str
    file_size_bytes: int
    mime_type: str
    duration_seconds: Optional[int] = None
    created_at: datetime

class MessageCreate(BaseModel):
    content: Optional[str] = None
    message_type: str = "text" # text, image, audio, system
    attachment_ids: Optional[List[uuid.UUID]] = None

class MessageOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    conversation_id: uuid.UUID
    sender_id: Optional[uuid.UUID]
    sender_username: Optional[str] = None
    sender_display_name: Optional[str] = None
    content: Optional[str]
    message_type: str
    is_deleted: bool
    created_at: datetime
    attachments: List[AttachmentOut] = []

class ConversationMemberOut(BaseModel):
    user_id: uuid.UUID
    username: str
    display_name: str
    avatar_url: Optional[str] = None
    role: str
    is_online: bool

class ConversationCreate(BaseModel):
    type: str = "direct" # direct or group
    participant_user_ids: List[uuid.UUID]
    title: Optional[str] = None

class ConversationOut(BaseModel):
    id: uuid.UUID
    type: str
    title: Optional[str]
    avatar_url: Optional[str]
    created_at: datetime
    members: List[ConversationMemberOut] = []
    last_message: Optional[MessageOut] = None
    unread_count: int = 0

# --- CALLS ---
class CallInitiate(BaseModel):
    receiver_id: Optional[uuid.UUID] = None
    call_type: str = "video" # video or voice

class CallAction(BaseModel):
    action: str # accept, reject, end, cancel

class CallOut(BaseModel):
    id: uuid.UUID
    caller_id: Optional[uuid.UUID]
    receiver_id: Optional[uuid.UUID]
    caller_username: Optional[str] = None
    caller_display_name: Optional[str] = None
    caller_avatar_url: Optional[str] = None
    receiver_username: Optional[str] = None
    receiver_display_name: Optional[str] = None
    receiver_avatar_url: Optional[str] = None
    room_name: str
    call_type: str
    status: str
    started_at: Optional[datetime] = None
    ended_at: Optional[datetime] = None
    duration_seconds: int = 0
    created_at: datetime
    livekit_token: Optional[str] = None
    livekit_url: Optional[str] = None

# --- MEETINGS ---
class MeetingCreate(BaseModel):
    title: str = Field(..., min_length=2, max_length=150)
    waiting_room_enabled: bool = False

class MeetingOut(BaseModel):
    id: uuid.UUID
    meeting_code: str
    title: str
    room_name: str
    host_id: uuid.UUID
    host_username: Optional[str] = None
    host_display_name: Optional[str] = None
    is_active: bool
    waiting_room_enabled: bool
    created_at: datetime
    livekit_token: Optional[str] = None
    livekit_url: Optional[str] = None

class MeetingJoinRequest(BaseModel):
    meeting_code: str

# --- NOTIFICATIONS & DEVICES ---
class DeviceRegister(BaseModel):
    fcm_token: str
    device_type: str # android, web, ios
    device_name: Optional[str] = None

class NotificationOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    type: str
    title: str
    body: str
    payload_json: Optional[str]
    is_read: bool
    created_at: datetime

# --- MODERATION & ADMIN ---
class ReportCreate(BaseModel):
    reported_user_id: uuid.UUID
    reason: str
    description: Optional[str] = None

class ReportOut(BaseModel):
    id: uuid.UUID
    reporter_id: uuid.UUID
    reported_user_id: uuid.UUID
    reason: str
    description: Optional[str]
    status: str
    created_at: datetime

class AdminUserUpdate(BaseModel):
    is_active: Optional[bool] = None
    is_verified: Optional[bool] = None
    is_admin: Optional[bool] = None

class PlatformStats(BaseModel):
    total_users: int
    active_users_today: int
    total_calls: int
    total_messages: int
    total_meetings: int
