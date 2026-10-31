from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload
from datetime import datetime, timezone
import uuid

from app.core.database import get_db
from app.core.security import hash_password, verify_password, create_access_token, verify_firebase_token
from app.core.dependencies import get_current_user
from app.models import User, UserProfile, UserSettings, AuditLog
from app.schemas import UserRegister, UserLogin, Token, UserOut, FirebaseAuthRequest

router = APIRouter(prefix="/auth", tags=["Authentication"])

@router.post("/register", response_model=Token, status_code=status.HTTP_201_CREATED)
async def register(payload: UserRegister, db: AsyncSession = Depends(get_db)):
    # Check existing email
    stmt = select(User).where((User.email == payload.email.lower()) | (User.username == payload.username.lower()))
    result = await db.execute(stmt)
    existing = result.scalars().first()
    if existing:
        if existing.email.lower() == payload.email.lower():
            raise HTTPException(status_code=400, detail="Email is already registered")
        else:
            raise HTTPException(status_code=400, detail="Username is already taken")

    # First registered user becomes admin automatically if no users exist
    count_stmt = select(User)
    count_result = await db.execute(count_stmt)
    is_first = count_result.scalars().first() is None

    hashed = hash_password(payload.password)
    user = User(
        email=payload.email.lower(),
        username=payload.username.lower(),
        hashed_password=hashed,
        is_verified=True, # Self-verified for direct registration
        is_admin=is_first
    )
    db.add(user)
    await db.flush()

    # Create associated profile
    profile = UserProfile(
        user_id=user.id,
        display_name=payload.display_name,
        is_online=True
    )
    db.add(profile)

    # Create default user settings
    settings = UserSettings(
        user_id=user.id
    )
    db.add(settings)

    # Create audit log
    audit = AuditLog(
        user_id=user.id,
        action="user_registered",
        resource_type="user",
        resource_id=str(user.id)
    )
    db.add(audit)

    await db.commit()

    token_data = {"sub": str(user.id), "username": user.username, "is_admin": user.is_admin}
    access_token = create_access_token(token_data)

    return Token(
        access_token=access_token,
        token_type="bearer",
        user_id=str(user.id),
        username=user.username
    )

@router.post("/login", response_model=Token)
async def login(payload: UserLogin, db: AsyncSession = Depends(get_db)):
    identifier = payload.username_or_email.strip().lower()
    stmt = select(User).where((User.email == identifier) | (User.username == identifier))
    result = await db.execute(stmt)
    user = result.scalars().first()

    if not user or not user.hashed_password:
        raise HTTPException(status_code=401, detail="Invalid username or password")

    if not verify_password(payload.password, user.hashed_password):
        raise HTTPException(status_code=401, detail="Invalid username or password")

    if not user.is_active:
        raise HTTPException(status_code=403, detail="Account is suspended or deactivated")

    # Update last active/profile online
    profile_stmt = select(UserProfile).where(UserProfile.user_id == user.id)
    p_res = await db.execute(profile_stmt)
    profile = p_res.scalars().first()
    if profile:
        profile.is_online = True
        profile.last_seen = datetime.now(timezone.utc)
    
    audit = AuditLog(
        user_id=user.id,
        action="user_logged_in",
        resource_type="user",
        resource_id=str(user.id)
    )
    db.add(audit)
    await db.commit()

    token_data = {"sub": str(user.id), "username": user.username, "is_admin": user.is_admin}
    access_token = create_access_token(token_data)

    return Token(
        access_token=access_token,
        token_type="bearer",
        user_id=str(user.id),
        username=user.username
    )

@router.post("/firebase-auth", response_model=Token)
async def firebase_auth(payload: FirebaseAuthRequest, db: AsyncSession = Depends(get_db)):
    decoded = await verify_firebase_token(payload.id_token)
    if not decoded:
        raise HTTPException(status_code=401, detail="Invalid Firebase ID token")

    firebase_uid = decoded.get("uid")
    email = decoded.get("email") or f"{firebase_uid}@viyo.internal"

    stmt = select(User).where((User.firebase_uid == firebase_uid) | (User.email == email.lower()))
    result = await db.execute(stmt)
    user = result.scalars().first()

    if not user:
        # Create new user via Firebase
        username = (payload.username or email.split("@")[0]).lower().replace(" ", "_")
        # Ensure unique username
        u_check = await db.execute(select(User).where(User.username == username))
        if u_check.scalars().first():
            username = f"{username}_{uuid.uuid4().hex[:6]}"

        user = User(
            email=email.lower(),
            username=username,
            firebase_uid=firebase_uid,
            is_verified=True,
            is_admin=False
        )
        db.add(user)
        await db.flush()

        profile = UserProfile(
            user_id=user.id,
            display_name=payload.display_name or decoded.get("name") or username,
            avatar_url=decoded.get("picture"),
            is_online=True
        )
        db.add(profile)
        db.add(UserSettings(user_id=user.id))
        await db.commit()
    else:
        if not user.firebase_uid:
            user.firebase_uid = firebase_uid
            await db.commit()

    token_data = {"sub": str(user.id), "username": user.username, "is_admin": user.is_admin}
    access_token = create_access_token(token_data)

    return Token(
        access_token=access_token,
        token_type="bearer",
        user_id=str(user.id),
        username=user.username
    )

@router.get("/me", response_model=UserOut)
async def get_me(current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    stmt = (
        select(User)
        .options(selectinload(User.profile), selectinload(User.settings))
        .where(User.id == current_user.id)
    )
    result = await db.execute(stmt)
    full_user = result.scalars().first()
    return full_user

@router.post("/logout")
async def logout(current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    stmt = select(UserProfile).where(UserProfile.user_id == current_user.id)
    result = await db.execute(stmt)
    profile = result.scalars().first()
    if profile:
        profile.is_online = False
        profile.last_seen = datetime.now(timezone.utc)
    
    audit = AuditLog(
        user_id=current_user.id,
        action="user_logged_out",
        resource_type="user",
        resource_id=str(current_user.id)
    )
    db.add(audit)
    await db.commit()
    return {"message": "Successfully logged out"}

