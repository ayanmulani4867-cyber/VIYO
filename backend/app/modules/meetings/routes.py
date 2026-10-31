import secrets
import string
import uuid
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload

from app.core.database import get_db
from app.core.config import settings
from app.core.dependencies import get_current_user
from app.models import Meeting, MeetingParticipant, User, UserProfile
from app.schemas import MeetingCreate, MeetingOut, MeetingJoinRequest
from app.integrations.livekit_service import livekit_service

router = APIRouter(prefix="/meetings", tags=["Meetings"])

def generate_meeting_code() -> str:
    alphabet = string.ascii_lowercase + string.digits
    part1 = "".join(secrets.choice(alphabet) for _ in range(3))
    part2 = "".join(secrets.choice(alphabet) for _ in range(4))
    part3 = "".join(secrets.choice(alphabet) for _ in range(3))
    return f"{part1}-{part2}-{part3}"

@router.post("", response_model=MeetingOut)
async def create_meeting(
    payload: MeetingCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    code = generate_meeting_code()
    room_name = f"viyo_mtg_{secrets.token_hex(8)}"

    meeting = Meeting(
        meeting_code=code,
        host_id=current_user.id,
        title=payload.title,
        room_name=room_name,
        is_active=True,
        waiting_room_enabled=payload.waiting_room_enabled
    )
    db.add(meeting)
    await db.flush()

    # Add host as participant
    db.add(MeetingParticipant(meeting_id=meeting.id, user_id=current_user.id, is_admitted=True))
    await db.commit()

    p_res = await db.execute(select(UserProfile).where(UserProfile.user_id == current_user.id))
    p = p_res.scalars().first()
    display_name = p.display_name if p else current_user.username

    token = livekit_service.generate_token(
        room_name=room_name,
        participant_identity=str(current_user.id),
        participant_name=display_name,
        is_host=True
    )

    return MeetingOut(
        id=meeting.id,
        meeting_code=meeting.meeting_code,
        title=meeting.title,
        room_name=meeting.room_name,
        host_id=meeting.host_id,
        host_username=current_user.username,
        host_display_name=display_name,
        is_active=meeting.is_active,
        waiting_room_enabled=meeting.waiting_room_enabled,
        created_at=meeting.created_at,
        livekit_token=token,
        livekit_url=settings.LIVEKIT_URL
    )

@router.get("/{meeting_code}", response_model=MeetingOut)
async def join_meeting(
    meeting_code: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    clean_code = meeting_code.strip().lower()
    stmt = (
        select(Meeting)
        .options(selectinload(Meeting.participants))
        .where(Meeting.meeting_code == clean_code, Meeting.is_active == True)
    )
    res = await db.execute(stmt)
    meeting = res.scalars().first()
    if not meeting:
        raise HTTPException(status_code=404, detail="Meeting not found or has concluded")

    # Record participant if not already registered
    p_check = await db.execute(
        select(MeetingParticipant).where(
            MeetingParticipant.meeting_id == meeting.id,
            MeetingParticipant.user_id == current_user.id
        )
    )
    if not p_check.scalars().first():
        db.add(MeetingParticipant(
            meeting_id=meeting.id,
            user_id=current_user.id,
            is_admitted=not meeting.waiting_room_enabled or meeting.host_id == current_user.id
        ))
        await db.commit()

    p_res = await db.execute(select(UserProfile).where(UserProfile.user_id == current_user.id))
    p = p_res.scalars().first()
    display_name = p.display_name if p else current_user.username

    is_host = meeting.host_id == current_user.id
    token = livekit_service.generate_token(
        room_name=meeting.room_name,
        participant_identity=str(current_user.id),
        participant_name=display_name,
        is_host=is_host
    )

    return MeetingOut(
        id=meeting.id,
        meeting_code=meeting.meeting_code,
        title=meeting.title,
        room_name=meeting.room_name,
        host_id=meeting.host_id,
        is_active=meeting.is_active,
        waiting_room_enabled=meeting.waiting_room_enabled,
        created_at=meeting.created_at,
        livekit_token=token,
        livekit_url=settings.LIVEKIT_URL
    )

@router.post("/{meeting_code}/end")
async def end_meeting(
    meeting_code: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(Meeting).where(Meeting.meeting_code == meeting_code.strip().lower())
    res = await db.execute(stmt)
    meeting = res.scalars().first()
    if not meeting:
        raise HTTPException(status_code=404, detail="Meeting not found")

    if meeting.host_id != current_user.id and not current_user.is_admin:
        raise HTTPException(status_code=403, detail="Only the host can conclude the meeting")

    meeting.is_active = False
    meeting.ended_at = datetime.now(timezone.utc)
    await db.commit()

    # Terminate LiveKit room SFU session
    await livekit_service.delete_room(meeting.room_name)

    return {"message": "Meeting ended successfully"}

