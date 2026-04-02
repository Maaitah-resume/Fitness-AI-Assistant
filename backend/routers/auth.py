# backend/routers/auth.py
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, EmailStr

# ----- ABSOLUTE IMPORT -------------------------------------------------
# The db helpers live in backend.db
from db import get_or_create_user, get_user_by_username
# ---------------------------------------------------------------------

router = APIRouter(prefix="/api/v1/auth", tags=["auth"])


# ── Schemas ───────────────────────────────────────────────────────────────────
class RegisterRequest(BaseModel):
    username: str
    password: str
    email: EmailStr


class LoginRequest(BaseModel):
    username: str
    password: str


# ── Routes ───────────────────────────────────────────────────────────────────
@router.post("/register")
async def register(req: RegisterRequest):
    """Create a new user account."""
    try:
        user_id = get_or_create_user(
            username=req.username,
            password=req.password,
            email=req.email,
        )
        return {
            "status": "success",
            "message": "User registered successfully.",
            "data": {
                "user_id": user_id,
                "username": req.username,
                "email": req.email,
            },
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/login")
async def login(req: LoginRequest):
    """Verify credentials and return the user record."""
    try:
        user = get_user_by_username(req.username)

        if not user:
            raise HTTPException(
                status_code=401, detail="Invalid username or password."
            )

        # `user` is a dict (see backend/db.py). Adjust if you store a tuple.
        if req.password != user["password"]:
            raise HTTPException(
                status_code=401, detail="Invalid username or password."
            )

        return {
            "status": "success",
            "message": "Login successful.",
            "data": {
                "user_id": user["id"],
                "username": user["username"],
                "email": user.get("email"),
            },
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
