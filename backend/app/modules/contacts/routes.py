import uuid
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload
from typing import List

from app.core.database import get_db
from app.core.dependencies import get_current_user
from app.models import User, UserProfile, Contact, ContactRequest, BlockedUser
from app.schemas import ContactOut, ContactRequestOut, ContactRequestCreate, BlockUserRequest
from app.integrations.websocket_manager import manager

router = APIRouter(prefix="/contacts", tags=["Contacts"])

@router.get("", response_model=List[ContactOut])
async def get_contacts(current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    stmt = (
        select(Contact, User, UserProfile)
        .join(User, Contact.contact_user_id == User.id)
        .join(UserProfile, User.id == UserProfile.user_id)
        .where(Contact.user_id == current_user.id, User.is_active == True)
    )
    result = await db.execute(stmt)
    rows = result.all()

    contacts = []
    for contact_entry, user, profile in rows:
        contacts.append(
            ContactOut(
                id=contact_entry.id,
                contact_user_id=user.id,
                username=user.username,
                display_name=profile.display_name,
                avatar_url=profile.avatar_url,
                is_online=manager.is_user_online(str(user.id)) or profile.is_online,
                last_seen=profile.last_seen,
                nickname=contact_entry.nickname,
                created_at=contact_entry.created_at
            )
        )
    return contacts

@router.post("/request", response_model=ContactRequestOut)
async def send_contact_request(
    payload: ContactRequestCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    target_username = payload.receiver_username.strip().lower()
    if target_username == current_user.username.lower():
        raise HTTPException(status_code=400, detail="Cannot send contact request to yourself")

    user_stmt = select(User).where(User.username == target_username, User.is_active == True)
    res = await db.execute(user_stmt)
    receiver = res.scalars().first()
    if not receiver:
        raise HTTPException(status_code=404, detail="User not found")

    # Check if already contacts
    c_check = await db.execute(
        select(Contact).where(Contact.user_id == current_user.id, Contact.contact_user_id == receiver.id)
    )
    if c_check.scalars().first():
        raise HTTPException(status_code=400, detail="User is already in your contacts")

    # Check if blocked
    b_check = await db.execute(
        select(BlockedUser).where(
            ((BlockedUser.blocker_id == current_user.id) & (BlockedUser.blocked_id == receiver.id)) |
            ((BlockedUser.blocker_id == receiver.id) & (BlockedUser.blocked_id == current_user.id))
        )
    )
    if b_check.scalars().first():
        raise HTTPException(status_code=403, detail="Unable to send contact request to this user")

    # Check existing pending request
    req_check = await db.execute(
        select(ContactRequest).where(
            ContactRequest.sender_id == current_user.id,
            ContactRequest.receiver_id == receiver.id,
            ContactRequest.status == "pending"
        )
    )
    if req_check.scalars().first():
        raise HTTPException(status_code=400, detail="Contact request is already pending")

    new_request = ContactRequest(
        sender_id=current_user.id,
        receiver_id=receiver.id,
        status="pending"
    )
    db.add(new_request)
    await db.commit()
    await db.refresh(new_request)

    # Real-time WebSocket event
    await manager.send_personal_message(
        str(receiver.id),
        {
            "event": "contact_request_received",
            "request_id": str(new_request.id),
            "sender_id": str(current_user.id),
            "sender_username": current_user.username
        }
    )

    return ContactRequestOut(
        id=new_request.id,
        sender_id=new_request.sender_id,
        receiver_id=new_request.receiver_id,
        status=new_request.status,
        created_at=new_request.created_at,
        sender_username=current_user.username
    )

@router.get("/requests/pending", response_model=List[ContactRequestOut])
async def get_pending_requests(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = (
        select(ContactRequest, User, UserProfile)
        .join(User, ContactRequest.sender_id == User.id)
        .join(UserProfile, User.id == UserProfile.user_id)
        .where(ContactRequest.receiver_id == current_user.id, ContactRequest.status == "pending")
    )
    result = await db.execute(stmt)
    rows = result.all()

    requests = []
    for req, sender, profile in rows:
        requests.append(
            ContactRequestOut(
                id=req.id,
                sender_id=req.sender_id,
                receiver_id=req.receiver_id,
                status=req.status,
                created_at=req.created_at,
                sender_username=sender.username,
                sender_display_name=profile.display_name,
                sender_avatar_url=profile.avatar_url
            )
        )
    return requests

@router.post("/requests/{request_id}/accept")
async def accept_contact_request(
    request_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(ContactRequest).where(ContactRequest.id == request_id, ContactRequest.receiver_id == current_user.id)
    result = await db.execute(stmt)
    req = result.scalars().first()
    if not req or req.status != "pending":
        raise HTTPException(status_code=404, detail="Pending request not found")

    req.status = "accepted"

    # Mutual contact creation
    c1 = Contact(user_id=current_user.id, contact_user_id=req.sender_id)
    c2 = Contact(user_id=req.sender_id, contact_user_id=current_user.id)
    db.add(c1)
    db.add(c2)

    await db.commit()

    await manager.send_personal_message(
        str(req.sender_id),
        {
            "event": "contact_request_accepted",
            "user_id": str(current_user.id),
            "username": current_user.username
        }
    )

    return {"message": "Contact request accepted"}

@router.post("/requests/{request_id}/reject")
async def reject_contact_request(
    request_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(ContactRequest).where(ContactRequest.id == request_id, ContactRequest.receiver_id == current_user.id)
    result = await db.execute(stmt)
    req = result.scalars().first()
    if not req or req.status != "pending":
        raise HTTPException(status_code=404, detail="Pending request not found")

    req.status = "rejected"
    await db.commit()
    return {"message": "Contact request rejected"}

@router.delete("/{contact_user_id}")
async def remove_contact(
    contact_user_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(Contact).where(
        ((Contact.user_id == current_user.id) & (Contact.contact_user_id == contact_user_id)) |
        ((Contact.user_id == contact_user_id) & (Contact.contact_user_id == current_user.id))
    )
    result = await db.execute(stmt)
    contacts = result.scalars().all()
    for c in contacts:
        await db.delete(c)
    await db.commit()
    return {"message": "Contact removed successfully"}

@router.post("/block")
async def block_user(
    payload: BlockUserRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    if payload.user_id == current_user.id:
        raise HTTPException(status_code=400, detail="Cannot block yourself")

    # Check already blocked
    stmt = select(BlockedUser).where(BlockedUser.blocker_id == current_user.id, BlockedUser.blocked_id == payload.user_id)
    result = await db.execute(stmt)
    if not result.scalars().first():
        block = BlockedUser(blocker_id=current_user.id, blocked_id=payload.user_id, reason=payload.reason)
        db.add(block)
        
        # Remove mutual contacts if exists
        c_stmt = select(Contact).where(
            ((Contact.user_id == current_user.id) & (Contact.contact_user_id == payload.user_id)) |
            ((Contact.user_id == payload.user_id) & (Contact.contact_user_id == current_user.id))
        )
        c_res = await db.execute(c_stmt)
        for c in c_res.scalars().all():
            await db.delete(c)

        await db.commit()

    return {"message": "User blocked"}

@router.post("/unblock/{blocked_user_id}")
async def unblock_user(
    blocked_user_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(BlockedUser).where(BlockedUser.blocker_id == current_user.id, BlockedUser.blocked_id == blocked_user_id)
    result = await db.execute(stmt)
    block = result.scalars().first()
    if block:
        await db.delete(block)
        await db.commit()
    return {"message": "User unblocked"}

