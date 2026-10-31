import os
import uuid
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status, Query, UploadFile, File
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload

from app.core.database import get_db
from app.core.config import settings
from app.core.dependencies import get_current_user
from app.models import Conversation, ConversationMember, Message, Attachment, User, UserProfile, BlockedUser
from app.schemas import (
    ConversationOut, ConversationCreate, ConversationMemberOut,
    MessageOut, MessageCreate, AttachmentOut
)
from app.integrations.websocket_manager import manager

router = APIRouter(prefix="/messaging", tags=["Messaging"])

@router.get("/conversations", response_model=List[ConversationOut])
async def get_conversations(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # Find all conversation IDs current user belongs to
    member_stmt = select(ConversationMember.conversation_id).where(ConversationMember.user_id == current_user.id)
    result = await db.execute(member_stmt)
    conv_ids = result.scalars().all()

    if not conv_ids:
        return []

    stmt = (
        select(Conversation)
        .where(Conversation.id.in_(conv_ids))
        .options(
            selectinload(Conversation.members).selectinload(ConversationMember.conversation),
            selectinload(Conversation.messages).selectinload(Message.attachments)
        )
        .order_by(Conversation.updated_at.desc())
    )
    res = await db.execute(stmt)
    conversations = res.scalars().all()

    output = []
    for conv in conversations:
        # Collect member profiles
        member_ids = [m.user_id for m in conv.members]
        users_stmt = (
            select(User, UserProfile)
            .join(UserProfile, User.id == UserProfile.user_id)
            .where(User.id.in_(member_ids))
        )
        u_res = await db.execute(users_stmt)
        user_map = {u.id: (u, p) for u, p in u_res.all()}

        members_out = []
        for m in conv.members:
            if m.user_id in user_map:
                u, p = user_map[m.user_id]
                members_out.append(
                    ConversationMemberOut(
                        user_id=u.id,
                        username=u.username,
                        display_name=p.display_name,
                        avatar_url=p.avatar_url,
                        role=m.role,
                        is_online=manager.is_user_online(str(u.id)) or p.is_online
                    )
                )

        # Sort messages to get the latest
        last_msg = None
        if conv.messages:
            sorted_msgs = sorted(conv.messages, key=lambda x: x.created_at, reverse=True)
            latest = sorted_msgs[0]
            
            # Resolve sender name
            s_name = None
            s_dname = None
            if latest.sender_id and latest.sender_id in user_map:
                su, sp = user_map[latest.sender_id]
                s_name = su.username
                s_dname = sp.display_name

            last_msg = MessageOut(
                id=latest.id,
                conversation_id=latest.conversation_id,
                sender_id=latest.sender_id,
                sender_username=s_name,
                sender_display_name=s_dname,
                content=latest.content,
                message_type=latest.message_type,
                is_deleted=latest.is_deleted,
                created_at=latest.created_at,
                attachments=[
                    AttachmentOut(
                        id=a.id,
                        file_url=a.file_url,
                        file_name=a.file_name,
                        file_size_bytes=a.file_size_bytes,
                        mime_type=a.mime_type,
                        duration_seconds=a.duration_seconds,
                        created_at=a.created_at
                    )
                    for a in latest.attachments
                ]
            )

        # In direct chat, title defaults to other party's name
        conv_title = conv.title
        conv_avatar = conv.avatar_url
        if conv.type == "direct":
            other_members = [m for m in members_out if m.user_id != current_user.id]
            if other_members:
                conv_title = other_members[0].display_name
                conv_avatar = other_members[0].avatar_url

        output.append(
            ConversationOut(
                id=conv.id,
                type=conv.type,
                title=conv_title,
                avatar_url=conv_avatar,
                created_at=conv.created_at,
                members=members_out,
                last_message=last_msg,
                unread_count=0
            )
        )
    return output

@router.post("/conversations", response_model=ConversationOut)
async def create_conversation(
    payload: ConversationCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    participant_ids = set(payload.participant_user_ids)
    participant_ids.add(current_user.id)

    if payload.type == "direct":
        if len(participant_ids) != 2:
            raise HTTPException(status_code=400, detail="Direct conversation must have exactly two participants")
        
        other_id = [uid for uid in participant_ids if uid != current_user.id][0]
        
        # Check blocked
        b_check = await db.execute(
            select(BlockedUser).where(
                ((BlockedUser.blocker_id == current_user.id) & (BlockedUser.blocked_id == other_id)) |
                ((BlockedUser.blocker_id == other_id) & (BlockedUser.blocked_id == current_user.id))
            )
        )
        if b_check.scalars().first():
            raise HTTPException(status_code=403, detail="Cannot message this user")

        # Check existing direct conversation
        existing_stmt = (
            select(Conversation.id)
            .join(ConversationMember, Conversation.id == ConversationMember.conversation_id)
            .where(Conversation.type == "direct", ConversationMember.user_id.in_([current_user.id, other_id]))
            .group_by(Conversation.id)
            .having(Conversation.id.in_(
                select(ConversationMember.conversation_id)
                .where(ConversationMember.user_id == other_id)
            ))
        )
        res = await db.execute(existing_stmt)
        existing_id = res.scalars().first()
        if existing_id:
            # Return existing
            c_res = await db.execute(
                select(Conversation)
                .where(Conversation.id == existing_id)
                .options(selectinload(Conversation.members), selectinload(Conversation.messages))
            )
            existing_conv = c_res.scalars().first()
            if existing_conv:
                # Format output
                return ConversationOut(
                    id=existing_conv.id,
                    type=existing_conv.type,
                    title=payload.title,
                    avatar_url=None,
                    created_at=existing_conv.created_at,
                    members=[]
                )

    new_conv = Conversation(
        type=payload.type,
        title=payload.title,
        created_by=current_user.id
    )
    db.add(new_conv)
    await db.flush()

    for pid in participant_ids:
        role = "owner" if pid == current_user.id else "member"
        db.add(ConversationMember(conversation_id=new_conv.id, user_id=pid, role=role))

    await db.commit()

    return ConversationOut(
        id=new_conv.id,
        type=new_conv.type,
        title=new_conv.title,
        avatar_url=None,
        created_at=new_conv.created_at,
        members=[]
    )

@router.get("/conversations/{conversation_id}/messages", response_model=List[MessageOut])
async def get_messages(
    conversation_id: uuid.UUID,
    limit: int = Query(50, le=100),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # Verify membership
    m_check = await db.execute(
        select(ConversationMember).where(
            ConversationMember.conversation_id == conversation_id,
            ConversationMember.user_id == current_user.id
        )
    )
    if not m_check.scalars().first():
        raise HTTPException(status_code=403, detail="Not a member of this conversation")

    stmt = (
        select(Message)
        .where(Message.conversation_id == conversation_id)
        .options(selectinload(Message.attachments))
        .order_by(Message.created_at.asc())
        .limit(limit)
    )
    res = await db.execute(stmt)
    messages = res.scalars().all()

    # Pre-fetch user details for senders
    sender_ids = list({m.sender_id for m in messages if m.sender_id})
    user_map = {}
    if sender_ids:
        u_res = await db.execute(
            select(User, UserProfile)
            .join(UserProfile, User.id == UserProfile.user_id)
            .where(User.id.in_(sender_ids))
        )
        user_map = {u.id: (u.username, p.display_name) for u, p in u_res.all()}

    output = []
    for m in messages:
        uname = None
        dname = None
        if m.sender_id and m.sender_id in user_map:
            uname, dname = user_map[m.sender_id]

        output.append(
            MessageOut(
                id=m.id,
                conversation_id=m.conversation_id,
                sender_id=m.sender_id,
                sender_username=uname,
                sender_display_name=dname,
                content=m.content,
                message_type=m.message_type,
                is_deleted=m.is_deleted,
                created_at=m.created_at,
                attachments=[
                    AttachmentOut(
                        id=a.id,
                        file_url=a.file_url,
                        file_name=a.file_name,
                        file_size_bytes=a.file_size_bytes,
                        mime_type=a.mime_type,
                        duration_seconds=a.duration_seconds,
                        created_at=a.created_at
                    )
                    for a in m.attachments
                ]
            )
        )
    return output

@router.post("/conversations/{conversation_id}/messages", response_model=MessageOut)
async def send_message(
    conversation_id: uuid.UUID,
    payload: MessageCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # Verify membership
    m_check = await db.execute(
        select(ConversationMember).where(
            ConversationMember.conversation_id == conversation_id,
            ConversationMember.user_id == current_user.id
        )
    )
    if not m_check.scalars().first():
        raise HTTPException(status_code=403, detail="Not a member of this conversation")

    msg = Message(
        conversation_id=conversation_id,
        sender_id=current_user.id,
        content=payload.content,
        message_type=payload.message_type
    )
    db.add(msg)
    await db.flush()

    # Link attachments if provided
    attachments_out = []
    if payload.attachment_ids:
        att_stmt = select(Attachment).where(Attachment.id.in_(payload.attachment_ids))
        a_res = await db.execute(att_stmt)
        atts = a_res.scalars().all()
        for a in atts:
            a.message_id = msg.id
            attachments_out.append(
                AttachmentOut(
                    id=a.id,
                    file_url=a.file_url,
                    file_name=a.file_name,
                    file_size_bytes=a.file_size_bytes,
                    mime_type=a.mime_type,
                    duration_seconds=a.duration_seconds,
                    created_at=a.created_at
                )
            )

    # Touch conversation updated_at
    conv_stmt = select(Conversation).where(Conversation.id == conversation_id)
    conv_res = await db.execute(conv_stmt)
    conv = conv_res.scalars().first()
    if conv:
        conv.updated_at = datetime.now(timezone.utc)

    await db.commit()

    # Broadcast via WebSocket to all members
    member_stmt = select(ConversationMember.user_id).where(ConversationMember.conversation_id == conversation_id)
    m_res = await db.execute(member_stmt)
    member_ids = [str(uid) for uid in m_res.scalars().all()]

    ws_payload = {
        "event": "new_message",
        "message": {
            "id": str(msg.id),
            "conversation_id": str(conversation_id),
            "sender_id": str(current_user.id),
            "sender_username": current_user.username,
            "content": msg.content,
            "message_type": msg.message_type,
            "created_at": msg.created_at.isoformat(),
            "attachments": [
                {
                    "id": str(a.id),
                    "file_url": a.file_url,
                    "file_name": a.file_name,
                    "mime_type": a.mime_type
                }
                for a in attachments_out
            ]
        }
    }
    await manager.broadcast_to_users(member_ids, ws_payload)

    return MessageOut(
        id=msg.id,
        conversation_id=conversation_id,
        sender_id=current_user.id,
        sender_username=current_user.username,
        content=msg.content,
        message_type=msg.message_type,
        is_deleted=False,
        created_at=msg.created_at,
        attachments=attachments_out
    )

@router.post("/upload", response_model=AttachmentOut)
async def upload_attachment(
    file: UploadFile = File(...),
    duration: Optional[int] = Query(None),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    os.makedirs(settings.UPLOAD_DIR, exist_ok=True)
    ext = os.path.splitext(file.filename)[1] or ".bin"
    file_id = uuid.uuid4()
    saved_filename = f"media_{file_id.hex}{ext}"
    filepath = os.path.join(settings.UPLOAD_DIR, saved_filename)

    content = await file.read()
    if len(content) > settings.MAX_FILE_SIZE_MB * 1024 * 1024:
        raise HTTPException(status_code=400, detail=f"Attachment exceeds {settings.MAX_FILE_SIZE_MB}MB limit")

    with open(filepath, "wb") as f:
        f.write(content)

    # Temporary attachment without message_id (linked upon message send)
    attachment = Attachment(
        id=file_id,
        message_id=file_id, # Placeholder until linked
        file_url=f"/uploads/{saved_filename}",
        file_name=file.filename or saved_filename,
        file_size_bytes=len(content),
        mime_type=file.content_type or "application/octet-stream",
        duration_seconds=duration
    )
    db.add(attachment)
    await db.commit()

    return AttachmentOut(
        id=attachment.id,
        file_url=attachment.file_url,
        file_name=attachment.file_name,
        file_size_bytes=attachment.file_size_bytes,
        mime_type=attachment.mime_type,
        duration_seconds=attachment.duration_seconds,
        created_at=attachment.created_at
    )

