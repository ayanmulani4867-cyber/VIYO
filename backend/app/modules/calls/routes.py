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
from app.models import Call, CallParticipant, User, UserProfile, UserSettings, BlockedUser, Contact
from app.schemas import CallInitiate, CallAction, CallOut
from app.integrations.livekit_service import livekit_service
from app.integrations.websocket_manager import manager

router = APIRouter(prefix="/calls", tags=["Calls"])

@router.post("/initiate", response_model=CallOut)
async def initiate_call(
    payload: CallInitiate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    if not payload.receiver_id:
        raise HTTPException(status_code=400, detail="Receiver ID required for direct call")
    if payload.receiver_id == current_user.id:
        raise HTTPException(status_code=400, detail="Cannot call yourself")

    # Fetch receiver
    stmt = (
        select(User)
        .options(selectinload(User.profile), selectinload(User.settings))
        .where(User.id == payload.receiver_id, User.is_active == True)
    )
    res = await db.execute(stmt)
    receiver = res.scalars().first()
    if not receiver:
        raise HTTPException(status_code=404, detail="Recipient not found")

    # Check blocked
    b_stmt = select(BlockedUser).where(
        ((BlockedUser.blocker_id == current_user.id) & (BlockedUser.blocked_id == receiver.id)) |
        ((BlockedUser.blocker_id == receiver.id) & (BlockedUser.blocked_id == current_user.id))
    )
    b_res = await db.execute(b_stmt)
    if b_res.scalars().first():
        raise HTTPException(status_code=403, detail="Unable to initiate call to this user")

    # Check privacy settings
    rec_settings = receiver.settings
    if rec_settings:
        if not rec_settings.allow_incoming_calls:
            raise HTTPException(status_code=403, detail="Recipient has disabled incoming calls")
        if rec_settings.privacy_calls == "nobody":
            raise HTTPException(status_code=403, detail="Recipient is not accepting calls")
        elif rec_settings.privacy_calls == "contacts_only":
            c_check = await db.execute(
                select(Contact).where(Contact.user_id == receiver.id, Contact.contact_user_id == current_user.id)
            )
            if not c_check.scalars().first():
                raise HTTPException(status_code=403, detail="Recipient only accepts calls from contacts")

    # Create call record
    room_name = f"viyo_call_{uuid.uuid4().hex}"
    call = Call(
        caller_id=current_user.id,
        receiver_id=receiver.id,
        room_name=room_name,
        call_type=payload.call_type,
        status="ringing"
    )
    db.add(call)
    await db.flush()

    # Add caller participant
    db.add(CallParticipant(call_id=call.id, user_id=current_user.id, role="caller"))
    db.add(CallParticipant(call_id=call.id, user_id=receiver.id, role="receiver"))
    await db.commit()

    # Generate token for caller
    caller_profile_res = await db.execute(select(UserProfile).where(UserProfile.user_id == current_user.id))
    caller_profile = caller_profile_res.scalars().first()
    caller_name = caller_profile.display_name if caller_profile else current_user.username

    caller_token = livekit_service.generate_token(
        room_name=room_name,
        participant_identity=str(current_user.id),
        participant_name=caller_name,
        is_host=True
    )

    # Send incoming call event to receiver via WebSocket
    await manager.send_personal_message(
        str(receiver.id),
        {
            "event": "incoming_call",
            "call_id": str(call.id),
            "room_name": room_name,
            "call_type": call.call_type,
            "caller": {
                "id": str(current_user.id),
                "username": current_user.username,
                "display_name": caller_name,
                "avatar_url": caller_profile.avatar_url if caller_profile else None
            }
        }
    )

    return CallOut(
        id=call.id,
        caller_id=current_user.id,
        receiver_id=receiver.id,
        caller_username=current_user.username,
        caller_display_name=caller_name,
        caller_avatar_url=caller_profile.avatar_url if caller_profile else None,
        receiver_username=receiver.username,
        receiver_display_name=receiver.profile.display_name if receiver.profile else receiver.username,
        receiver_avatar_url=receiver.profile.avatar_url if receiver.profile else None,
        room_name=room_name,
        call_type=call.call_type,
        status=call.status,
        created_at=call.created_at,
        livekit_token=caller_token,
        livekit_url=settings.LIVEKIT_URL
    )

@router.post("/{call_id}/action", response_model=CallOut)
async def handle_call_action(
    call_id: uuid.UUID,
    payload: CallAction,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = (
        select(Call)
        .options(selectinload(Call.participants))
        .where(Call.id == call_id)
    )
    res = await db.execute(stmt)
    call = res.scalars().first()
    if not call:
        raise HTTPException(status_code=404, detail="Call record not found")

    action = payload.action.lower()
    now = datetime.now(timezone.utc)
    token = None

    if action == "accept":
        if call.receiver_id != current_user.id:
            raise HTTPException(status_code=403, detail="Only the receiver can accept this call")
        call.status = "accepted"
        call.started_at = now

        profile_res = await db.execute(select(UserProfile).where(UserProfile.user_id == current_user.id))
        p = profile_res.scalars().first()
        receiver_name = p.display_name if p else current_user.username

        token = livekit_service.generate_token(
            room_name=call.room_name,
            participant_identity=str(current_user.id),
            participant_name=receiver_name,
            is_host=False
        )

        # Notify caller that receiver accepted
        await manager.send_personal_message(
            str(call.caller_id),
            {
                "event": "call_accepted",
                "call_id": str(call.id),
                "room_name": call.room_name
            }
        )

    elif action == "reject":
        if call.receiver_id != current_user.id:
            raise HTTPException(status_code=403, detail="Only receiver can reject this call")
        call.status = "rejected"
        call.ended_at = now
        await manager.send_personal_message(
            str(call.caller_id),
            {"event": "call_rejected", "call_id": str(call.id)}
        )

    elif action == "cancel":
        if call.caller_id != current_user.id:
            raise HTTPException(status_code=403, detail="Only caller can cancel outgoing call")
        call.status = "missed"
        call.ended_at = now
        await manager.send_personal_message(
            str(call.receiver_id),
            {"event": "call_cancelled", "call_id": str(call.id)}
        )

    elif action == "end":
        call.status = "ended"
        call.ended_at = now
        if call.started_at:
            started = call.started_at
            if started.tzinfo is None:
                started = started.replace(tzinfo=timezone.utc)
            duration = int((now - started).total_seconds())
            call.duration_seconds = max(0, duration)
        
        other_user_id = call.caller_id if current_user.id == call.receiver_id else call.receiver_id
        if other_user_id:
            await manager.send_personal_message(
                str(other_user_id),
                {"event": "call_ended", "call_id": str(call.id), "duration": call.duration_seconds}
            )

    await db.commit()

    return CallOut(
        id=call.id,
        caller_id=call.caller_id,
        receiver_id=call.receiver_id,
        room_name=call.room_name,
        call_type=call.call_type,
        status=call.status,
        started_at=call.started_at,
        ended_at=call.ended_at,
        duration_seconds=call.duration_seconds,
        created_at=call.created_at,
        livekit_token=token,
        livekit_url=settings.LIVEKIT_URL
    )

@router.get("/history", response_model=List[CallOut])
async def get_call_history(
    limit: int = 50,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = (
        select(Call)
        .where((Call.caller_id == current_user.id) | (Call.receiver_id == current_user.id))
        .order_by(Call.created_at.desc())
        .limit(limit)
    )
    res = await db.execute(stmt)
    calls = res.scalars().all()

    # Pre-fetch users for display
    user_ids = set()
    for c in calls:
        if c.caller_id:
            user_ids.add(c.caller_id)
        if c.receiver_id:
            user_ids.add(c.receiver_id)

    user_map = {}
    if user_ids:
        u_res = await db.execute(
            select(User, UserProfile)
            .join(UserProfile, User.id == UserProfile.user_id)
            .where(User.id.in_(list(user_ids)))
        )
        for u, p in u_res.all():
            user_map[u.id] = (u.username, p.display_name, p.avatar_url)

    output = []
    for c in calls:
        c_uname, c_dname, c_avatar = user_map.get(c.caller_id, (None, None, None))
        r_uname, r_dname, r_avatar = user_map.get(c.receiver_id, (None, None, None))

        output.append(
            CallOut(
                id=c.id,
                caller_id=c.caller_id,
                receiver_id=c.receiver_id,
                caller_username=c_uname,
                caller_display_name=c_dname,
                caller_avatar_url=c_avatar,
                receiver_username=r_uname,
                receiver_display_name=r_dname,
                receiver_avatar_url=r_avatar,
                room_name=c.room_name,
                call_type=c.call_type,
                status=c.status,
                started_at=c.started_at,
                ended_at=c.ended_at,
                duration_seconds=c.duration_seconds,
                created_at=c.created_at
            )
        )
    return output
