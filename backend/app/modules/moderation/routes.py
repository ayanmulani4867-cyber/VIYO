import uuid
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy import func

from app.core.database import get_db
from app.core.dependencies import get_current_user, get_current_admin
from app.models import Report, User, UserProfile, AuditLog, Call, Message, Meeting
from app.schemas import ReportCreate, ReportOut, AdminUserUpdate, PlatformStats

router = APIRouter(prefix="/moderation", tags=["Moderation & Administration"])

@router.post("/reports", response_model=ReportOut)
async def submit_report(
    payload: ReportCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    if payload.reported_user_id == current_user.id:
        raise HTTPException(status_code=400, detail="Cannot report yourself")

    target_res = await db.execute(select(User).where(User.id == payload.reported_user_id))
    if not target_res.scalars().first():
        raise HTTPException(status_code=404, detail="Reported user not found")

    report = Report(
        reporter_id=current_user.id,
        reported_user_id=payload.reported_user_id,
        reason=payload.reason,
        description=payload.description
    )
    db.add(report)

    db.add(AuditLog(
        user_id=current_user.id,
        action="report_submitted",
        resource_type="report",
        resource_id=str(payload.reported_user_id)
    ))

    await db.commit()
    await db.refresh(report)
    return report

@router.get("/admin/reports", response_model=List[ReportOut])
async def list_reports(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(Report).order_by(Report.created_at.desc())
    res = await db.execute(stmt)
    return res.scalars().all()

@router.put("/admin/users/{user_id}")
async def admin_update_user(
    user_id: uuid.UUID,
    payload: AdminUserUpdate,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    res = await db.execute(select(User).where(User.id == user_id))
    user = res.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    if payload.is_active is not None:
        user.is_active = payload.is_active
    if payload.is_verified is not None:
        user.is_verified = payload.is_verified
    if payload.is_admin is not None:
        user.is_admin = payload.is_admin

    db.add(AuditLog(
        user_id=admin.id,
        action="admin_updated_user",
        resource_type="user",
        resource_id=str(user.id),
        metadata_json=f'{{"is_active": {user.is_active}, "is_admin": {user.is_admin}}}'
    ))

    await db.commit()
    return {"message": "User updated successfully"}

@router.get("/admin/stats", response_model=PlatformStats)
async def get_stats(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    u_count = await db.execute(select(func.count(User.id)))
    c_count = await db.execute(select(func.count(Call.id)))
    m_count = await db.execute(select(func.count(Message.id)))
    mtg_count = await db.execute(select(func.count(Meeting.id)))

    u_val = u_count.scalar() or 0
    c_val = c_count.scalar() or 0
    m_val = m_count.scalar() or 0
    mtg_val = mtg_count.scalar() or 0

    return PlatformStats(
        total_users=u_val,
        active_users_today=u_val,
        total_calls=c_val,
        total_messages=m_val,
        total_meetings=mtg_val
    )
