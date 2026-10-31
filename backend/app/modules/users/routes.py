import os
import uuid
import aiofiles
from fastapi import APIRouter, Depends, HTTPException, status, Query, UploadFile, File
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload
from typing import List, Optional

from app.core.database import get_db
from app.core.config import settings
from app.core.dependencies import get_current_user
from app.models import User, UserProfile, UserSettings, BlockedUser, Contact
from app.schemas import UserSearchOut, UserProfileUpdate, UserSettingsUpdate, UserSettingsOut, UserProfileOut

router = APIRouter(prefix="/users", tags=["Users"])

@router.get("/search", response_model=List[UserSearchOut])
async def search_users(
    q: str = Query(..., min_length=1),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    query_str = f"%{q.lower()}%"
    
    # Exclude blocked users (either direction)
    blocked_subquery = select(BlockedUser.blocked_id).where(BlockedUser.blocker_id == current_user.id)
    blocker_subquery = select(BlockedUser.blocker_id).where(BlockedUser.blocked_id == current_user.id)

    stmt = (
        select(User)
        .join(UserProfile, User.id == UserProfile.user_id)
        .where(
            User.id != current_user.id,
            User.is_active == True,
            (User.username.ilike(query_str) | UserProfile.display_name.ilike(query_str)),
            User.id.not_in(blocked_subquery),
            User.id.not_in(blocker_subquery)
        )
        .options(selectinload(User.profile), selectinload(User.settings))
        .limit(20)
    )
    result = await db.execute(stmt)
    users = result.scalars().all()

    output = []
    for u in users:
        p = u.profile
        s = u.settings
        
        # Check last_seen privacy
        last_seen = p.last_seen if p else None
        if s and s.privacy_last_seen == "nobody":
            last_seen = None

        output.append(
            UserSearchOut(
                id=u.id,
                username=u.username,
                display_name=p.display_name if p else u.username,
                avatar_url=p.avatar_url if p else None,
                is_online=p.is_online if p else False,
                last_seen=last_seen
            )
        )
    return output

@router.get("/{user_id}", response_model=UserSearchOut)
async def get_user_profile(
    user_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = (
        select(User)
        .options(selectinload(User.profile), selectinload(User.settings))
        .where(User.id == user_id, User.is_active == True)
    )
    res = await db.execute(stmt)
    user = res.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    # Check blocked
    b_check = await db.execute(
        select(BlockedUser).where(
            ((BlockedUser.blocker_id == current_user.id) & (BlockedUser.blocked_id == user_id)) |
            ((BlockedUser.blocker_id == user_id) & (BlockedUser.blocked_id == current_user.id))
        )
    )
    if b_check.scalars().first():
        raise HTTPException(status_code=403, detail="User profile unavailable")

    p = user.profile
    s = user.settings
    last_seen = p.last_seen if p else None
    if s and s.privacy_last_seen == "nobody":
        last_seen = None

    return UserSearchOut(
        id=user.id,
        username=user.username,
        display_name=p.display_name if p else user.username,
        avatar_url=p.avatar_url if p else None,
        is_online=p.is_online if p else False,
        last_seen=last_seen
    )

@router.put("/profile", response_model=UserProfileOut)
async def update_profile(
    payload: UserProfileUpdate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(UserProfile).where(UserProfile.user_id == current_user.id)
    result = await db.execute(stmt)
    profile = result.scalars().first()
    if not profile:
        profile = UserProfile(user_id=current_user.id, display_name=current_user.username)
        db.add(profile)

    if payload.display_name is not None:
        profile.display_name = payload.display_name
    if payload.bio is not None:
        profile.bio = payload.bio
    if payload.status_message is not None:
        profile.status_message = payload.status_message
    if payload.avatar_url is not None:
        profile.avatar_url = payload.avatar_url

    await db.commit()
    await db.refresh(profile)
    return profile

@router.put("/settings", response_model=UserSettingsOut)
async def update_settings(
    payload: UserSettingsUpdate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(UserSettings).where(UserSettings.user_id == current_user.id)
    result = await db.execute(stmt)
    user_settings = result.scalars().first()
    if not user_settings:
        user_settings = UserSettings(user_id=current_user.id)
        db.add(user_settings)

    if payload.privacy_last_seen is not None:
        if payload.privacy_last_seen in ["everyone", "contacts_only", "nobody"]:
            user_settings.privacy_last_seen = payload.privacy_last_seen
    if payload.privacy_calls is not None:
        if payload.privacy_calls in ["everyone", "contacts_only", "nobody"]:
            user_settings.privacy_calls = payload.privacy_calls
    if payload.privacy_profile_photo is not None:
        user_settings.privacy_profile_photo = payload.privacy_profile_photo
    if payload.allow_incoming_calls is not None:
        user_settings.allow_incoming_calls = payload.allow_incoming_calls
    if payload.camera_enabled_by_default is not None:
        user_settings.camera_enabled_by_default = payload.camera_enabled_by_default
    if payload.microphone_enabled_by_default is not None:
        user_settings.microphone_enabled_by_default = payload.microphone_enabled_by_default
    if payload.push_notifications_enabled is not None:
        user_settings.push_notifications_enabled = payload.push_notifications_enabled

    await db.commit()
    await db.refresh(user_settings)
    return user_settings

@router.post("/avatar")
async def upload_avatar(
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    if not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Only image files are allowed for avatar")

    os.makedirs(settings.UPLOAD_DIR, exist_ok=True)
    ext = os.path.splitext(file.filename)[1] or ".png"
    filename = f"avatar_{current_user.id}_{uuid.uuid4().hex[:8]}{ext}"
    filepath = os.path.join(settings.UPLOAD_DIR, filename)

    content = await file.read()
    if len(content) > settings.MAX_FILE_SIZE_MB * 1024 * 1024:
        raise HTTPException(status_code=400, detail=f"File exceeds maximum size of {settings.MAX_FILE_SIZE_MB}MB")

    with open(filepath, "wb") as f:
        f.write(content)

    avatar_url = f"/uploads/{filename}"

    stmt = select(UserProfile).where(UserProfile.user_id == current_user.id)
    result = await db.execute(stmt)
    profile = result.scalars().first()
    if profile:
        profile.avatar_url = avatar_url
    await db.commit()

    return {"avatar_url": avatar_url}

