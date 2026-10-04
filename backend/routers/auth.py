# backend/routers/auth.py
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, EmailStr

from db import UserExistsError, create_user, get_user_by_username
from security import create_access_token, get_current_user, hash_password, verify_password

router = APIRouter(prefix="/api/v1/auth", tags=["auth"])


# ── Schemas ───────────────────────────────────────────────────────────────────
class RegisterRequest(BaseModel):
    username: str
    password: str
    email: EmailStr
    name: str | None = None


class LoginRequest(BaseModel):
    username: str
    password: str


# ── Routes ───────────────────────────────────────────────────────────────────
@router.post("/register")
async def register(req: RegisterRequest):
    """Create a new user account (role defaults to 'user') and log them in."""
    try:
        user_id = create_user(
            username=req.username,
            password_hash=hash_password(req.password),
            email=req.email,
            name=req.name,
        )
    except UserExistsError:
        raise HTTPException(status_code=400, detail="Username or email already in use.")

    token = create_access_token(user_id=user_id, username=req.username, role="user")
    return {
        "status": "success",
        "message": "User registered successfully.",
        "data": {
            "access_token": token,
            "token_type": "bearer",
            "user_id": user_id,
            "username": req.username,
            "email": req.email,
            "role": "user",
        },
    }


@router.post("/login")
async def login(req: LoginRequest):
    """Verify credentials and return a JWT access token."""
    user = get_user_by_username(req.username)

    if not user or not verify_password(req.password, user["password"]):
        raise HTTPException(status_code=401, detail="Invalid username or password.")

    token = create_access_token(
        user_id=user["id"], username=user["username"], role=user["role"]
    )
    return {
        "status": "success",
        "message": "Login successful.",
        "data": {
            "access_token": token,
            "token_type": "bearer",
            "user_id": user["id"],
            "username": user["username"],
            "email": user["email"],
            "role": user["role"],
        },
    }


@router.get("/me")
async def me(current_user: dict = Depends(get_current_user)):
    """Return the identity of the currently authenticated user."""
    return {"status": "success", "data": current_user}
