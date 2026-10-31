import uuid
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select

from app.core.database import get_db
from app.core.dependencies import get_current_user
from app.models import Notification, Device, User
from app.schemas import NotificationOut, DeviceRegister

router = APIRouter(prefix="/notifications", tags=["Notifications"])

@router.post("/device")
async def register_device(
    payload: DeviceRegister,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(Device).where(Device.user_id == current_user.id, Device.fcm_token == payload.fcm_token)
    res = await db.execute(stmt)
    existing = res.scalars().first()
    if not existing:
        device = Device(
            user_id=current_user.id,
            fcm_token=payload.fcm_token,
            device_type=payload.device_type,
            device_name=payload.device_name
        )
        db.add(device)
    else:
        existing.device_name = payload.device_name
    await db.commit()
    return {"message": "Device registered successfully"}

@router.get("", response_model=List[NotificationOut])
async def get_notifications(
    limit: int = 50,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = (
        select(Notification)
        .where(Notification.user_id == current_user.id)
        .order_by(Notification.created_at.desc())
        .limit(limit)
    )
    res = await db.execute(stmt)
    return res.scalars().all()

@router.post("/{notification_id}/read")
async def mark_notification_read(
    notification_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(Notification).where(
        Notification.id == notification_id,
        Notification.user_id == current_user.id
    )
    res = await db.execute(stmt)
    n = res.scalars().first()
    if n:
        n.is_read = True
        await db.commit()
    return {"message": "Marked as read"}

