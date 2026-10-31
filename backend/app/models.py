import uuid
from datetime import datetime, timezone
from sqlalchemy import (
    Column, String, Boolean, DateTime, ForeignKey, Text, Integer, BigInteger, Table, UniqueConstraint, Index
)
from sqlalchemy.orm import relationship
from app.core.database import Base, GUID

def utcnow():
    return datetime.now(timezone.utc)

class User(Base):
    __tablename__ = "users"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    email = Column(String(255), unique=True, index=True, nullable=False)
    username = Column(String(50), unique=True, index=True, nullable=False)
    hashed_password = Column(String(255), nullable=True)
    firebase_uid = Column(String(128), unique=True, index=True, nullable=True)
    is_active = Column(Boolean, default=True, nullable=False)
    is_verified = Column(Boolean, default=False, nullable=False)
    is_admin = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, nullable=False)

    profile = relationship("UserProfile", back_populates="user", uselist=False, cascade="all, delete-orphan")
    settings = relationship("UserSettings", back_populates="user", uselist=False, cascade="all, delete-orphan")
    devices = relationship("Device", back_populates="user", cascade="all, delete-orphan")
    notifications = relationship("Notification", back_populates="user", cascade="all, delete-orphan")

class UserProfile(Base):
    __tablename__ = "user_profiles"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    user_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), unique=True, nullable=False)
    display_name = Column(String(100), nullable=False)
    avatar_url = Column(String(512), nullable=True)
    bio = Column(Text, nullable=True)
    status_message = Column(String(140), nullable=True)
    is_online = Column(Boolean, default=False, index=True, nullable=False)
    last_seen = Column(DateTime(timezone=True), nullable=True)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, nullable=False)

    user = relationship("User", back_populates="profile")

class UserSettings(Base):
    __tablename__ = "user_settings"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    user_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), unique=True, nullable=False)
    privacy_last_seen = Column(String(20), default="contacts_only", nullable=False)  # everyone, contacts_only, nobody
    privacy_calls = Column(String(20), default="everyone", nullable=False)          # everyone, contacts_only, nobody
    privacy_profile_photo = Column(String(20), default="everyone", nullable=False)
    allow_incoming_calls = Column(Boolean, default=True, nullable=False)
    camera_enabled_by_default = Column(Boolean, default=True, nullable=False)
    microphone_enabled_by_default = Column(Boolean, default=True, nullable=False)
    push_notifications_enabled = Column(Boolean, default=True, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, nullable=False)

    user = relationship("User", back_populates="settings")

class Device(Base):
    __tablename__ = "devices"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    user_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    fcm_token = Column(String(512), nullable=True, index=True)
    device_type = Column(String(20), nullable=False) # android, web, ios
    device_name = Column(String(100), nullable=True)
    last_active_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)

    user = relationship("User", back_populates="devices")

class Contact(Base):
    __tablename__ = "contacts"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    user_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    contact_user_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    nickname = Column(String(100), nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)

    __table_args__ = (UniqueConstraint("user_id", "contact_user_id", name="uq_user_contact"),)

class ContactRequest(Base):
    __tablename__ = "contact_requests"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    sender_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    receiver_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    status = Column(String(20), default="pending", nullable=False) # pending, accepted, rejected, cancelled
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, nullable=False)

class BlockedUser(Base):
    __tablename__ = "blocked_users"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    blocker_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    blocked_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    reason = Column(Text, nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)

    __table_args__ = (UniqueConstraint("blocker_id", "blocked_id", name="uq_blocker_blocked"),)

class Conversation(Base):
    __tablename__ = "conversations"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    type = Column(String(20), nullable=False) # direct, group
    title = Column(String(150), nullable=True)
    avatar_url = Column(String(512), nullable=True)
    created_by = Column(GUID, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, nullable=False)

    members = relationship("ConversationMember", back_populates="conversation", cascade="all, delete-orphan")
    messages = relationship("Message", back_populates="conversation", cascade="all, delete-orphan")

class ConversationMember(Base):
    __tablename__ = "conversation_members"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    conversation_id = Column(GUID, ForeignKey("conversations.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    role = Column(String(20), default="member", nullable=False) # owner, admin, member
    joined_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    last_read_message_id = Column(GUID, nullable=True)

    __table_args__ = (UniqueConstraint("conversation_id", "user_id", name="uq_conv_member"),)

    conversation = relationship("Conversation", back_populates="members")

class Message(Base):
    __tablename__ = "messages"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    conversation_id = Column(GUID, ForeignKey("conversations.id", ondelete="CASCADE"), nullable=False, index=True)
    sender_id = Column(GUID, ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True)
    content = Column(Text, nullable=True)
    message_type = Column(String(20), default="text", nullable=False) # text, image, audio, system
    is_deleted = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False, index=True)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, nullable=False)

    conversation = relationship("Conversation", back_populates="messages")
    attachments = relationship("Attachment", back_populates="message", cascade="all, delete-orphan")

class Attachment(Base):
    __tablename__ = "attachments"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    message_id = Column(GUID, ForeignKey("messages.id", ondelete="CASCADE"), nullable=False, index=True)
    file_url = Column(String(512), nullable=False)
    file_name = Column(String(255), nullable=False)
    file_size_bytes = Column(BigInteger, nullable=False)
    mime_type = Column(String(100), nullable=False)
    duration_seconds = Column(Integer, nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)

    message = relationship("Message", back_populates="attachments")

class Call(Base):
    __tablename__ = "calls"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    caller_id = Column(GUID, ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True)
    receiver_id = Column(GUID, ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True)
    room_name = Column(String(100), unique=True, nullable=False, index=True)
    call_type = Column(String(20), nullable=False) # video, voice, group
    status = Column(String(20), default="initiated", nullable=False) # initiated, ringing, accepted, rejected, ended, missed, busy
    started_at = Column(DateTime(timezone=True), nullable=True)
    ended_at = Column(DateTime(timezone=True), nullable=True)
    duration_seconds = Column(Integer, default=0, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)

    participants = relationship("CallParticipant", back_populates="call", cascade="all, delete-orphan")

class CallParticipant(Base):
    __tablename__ = "call_participants"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    call_id = Column(GUID, ForeignKey("calls.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    role = Column(String(20), default="participant", nullable=False)
    connection_status = Column(String(20), default="connected", nullable=False)
    joined_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    left_at = Column(DateTime(timezone=True), nullable=True)

    call = relationship("Call", back_populates="participants")

class Meeting(Base):
    __tablename__ = "meetings"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    meeting_code = Column(String(20), unique=True, index=True, nullable=False)
    host_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    title = Column(String(150), nullable=False)
    room_name = Column(String(100), unique=True, nullable=False, index=True)
    is_active = Column(Boolean, default=True, nullable=False)
    waiting_room_enabled = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    ended_at = Column(DateTime(timezone=True), nullable=True)

    participants = relationship("MeetingParticipant", back_populates="meeting", cascade="all, delete-orphan")

class MeetingParticipant(Base):
    __tablename__ = "meeting_participants"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    meeting_id = Column(GUID, ForeignKey("meetings.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    is_admitted = Column(Boolean, default=True, nullable=False)
    joined_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    left_at = Column(DateTime(timezone=True), nullable=True)

    meeting = relationship("Meeting", back_populates="participants")

class Notification(Base):
    __tablename__ = "notifications"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    user_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    type = Column(String(50), nullable=False) # call_incoming, missed_call, new_message, contact_request, meeting_invite
    title = Column(String(150), nullable=False)
    body = Column(Text, nullable=False)
    payload_json = Column(Text, nullable=True) # JSON string
    is_read = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)

    user = relationship("User", back_populates="notifications")

class Recording(Base):
    __tablename__ = "recordings"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    call_id = Column(GUID, ForeignKey("calls.id", ondelete="SET NULL"), nullable=True)
    meeting_id = Column(GUID, ForeignKey("meetings.id", ondelete="SET NULL"), nullable=True)
    file_url = Column(String(512), nullable=False)
    duration_seconds = Column(Integer, default=0, nullable=False)
    status = Column(String(20), default="completed", nullable=False)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)

class Report(Base):
    __tablename__ = "reports"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    reporter_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    reported_user_id = Column(GUID, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    reason = Column(String(100), nullable=False)
    description = Column(Text, nullable=True)
    status = Column(String(20), default="pending", nullable=False) # pending, investigating, resolved, dismissed
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)

class AuditLog(Base):
    __tablename__ = "audit_logs"

    id = Column(GUID, primary_key=True, default=uuid.uuid4)
    user_id = Column(GUID, ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True)
    action = Column(String(100), nullable=False, index=True)
    resource_type = Column(String(50), nullable=False)
    resource_id = Column(String(100), nullable=True)
    ip_address = Column(String(45), nullable=True)
    user_agent = Column(String(255), nullable=True)
    metadata_json = Column(Text, nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)
