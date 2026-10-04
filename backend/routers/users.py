# backend/routers/users.py
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from db import (
    delete_user,
    get_user_profile,
    list_users,
    update_user_profile,
    update_user_role,
)
from security import get_current_user, require_admin

router = APIRouter(prefix="/api/v1/users", tags=["users"])


# ── Schemas ───────────────────────────────────────────────────────────────────
class ProfileUpdateRequest(BaseModel):
    age: int | None = None
    gender: str | None = None
    goal: str | None = None
    level: str | None = None


class RoleUpdateRequest(BaseModel):
    role: str  # "user" or "admin"


# ── Own profile (any authenticated user) ──────────────────────────────────────
@router.get("/profile/me")
async def get_my_profile(current_user: dict = Depends(get_current_user)):
    profile = get_user_profile(current_user["id"])
    return {"status": "success", "data": profile}


@router.put("/profile/me")
async def update_my_profile(
    req: ProfileUpdateRequest, current_user: dict = Depends(get_current_user)
):
    fields = {k: v for k, v in req.model_dump().items() if v is not None}
    if fields:
        update_user_profile(current_user["id"], **fields)
    profile = get_user_profile(current_user["id"])
    return {"status": "success", "data": profile}


# ── Admin only ─────────────────────────────────────────────────────────────────
@router.get("/")
async def admin_list_users(admin: dict = Depends(require_admin)):
    return {"status": "success", "data": {"users": list_users()}}


@router.patch("/{user_id}/role")
async def admin_update_role(
    user_id: int, req: RoleUpdateRequest, admin: dict = Depends(require_admin)
):
    if req.role not in ("user", "admin"):
        raise HTTPException(status_code=400, detail="role must be 'user' or 'admin'.")
    update_user_role(user_id, req.role)
    return {"status": "success", "message": f"User {user_id} role set to '{req.role}'."}


@router.delete("/{user_id}")
async def admin_delete_user(user_id: int, admin: dict = Depends(require_admin)):
    if user_id == admin["id"]:
        raise HTTPException(status_code=400, detail="You cannot delete your own account.")
    delete_user(user_id)
    return {"status": "success", "message": f"User {user_id} deleted."}
