# backend/routers/users.py
from fastapi import APIRouter, HTTPException
from db import get_user_profile  # absolute import

router = APIRouter(prefix="/api/v1/users", tags=["users"])


@router.get("/profile/{user_id}")
async def get_profile(user_id: int):
    """Return the fitness profile for a given user ID."""
    try:
        profile = get_user_profile(user_id)
        if not profile:
            raise HTTPException(status_code=404, detail="User not found.")
        return {"status": "success", "data": profile}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
