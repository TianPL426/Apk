import os
import secrets
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Annotated, Literal
from uuid import uuid4

from fastapi import Depends, FastAPI, Header, HTTPException, status
from pydantic import BaseModel, Field


DATA_DIR = Path(__file__).resolve().parent / "data"
DATABASE_PATH = DATA_DIR / "assistant.db"
MESSAGE_API_TOKEN_ENV = "ASSISTANT_MESSAGE_API_TOKEN"


class MessageCreate(BaseModel):
    source_app: str = Field(min_length=1)
    source_type: str = Field(min_length=1)
    conversation_name: str | None = None
    conversation_type: Literal["group", "private"] | None = None
    sender: str | None = None
    title: str | None = None
    raw_text: str = Field(min_length=1)
    received_at: datetime
    device_id: str = Field(min_length=1)
    notification_id: str | None = None


class Message(MessageCreate):
    id: str
    created_at: datetime


def connect_database() -> sqlite3.Connection:
    connection = sqlite3.connect(DATABASE_PATH)
    connection.row_factory = sqlite3.Row
    return connection


def initialize_database() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with connect_database() as connection:
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                source_app TEXT NOT NULL,
                source_type TEXT NOT NULL,
                conversation_name TEXT,
                conversation_type TEXT CHECK (
                    conversation_type IN ('group', 'private')
                    OR conversation_type IS NULL
                ),
                sender TEXT,
                title TEXT,
                raw_text TEXT NOT NULL,
                received_at TEXT NOT NULL,
                created_at TEXT NOT NULL,
                device_id TEXT NOT NULL,
                notification_id TEXT
            )
            """
        )


def require_message_api_token(
    x_assistant_token: Annotated[str | None, Header()] = None,
) -> None:
    configured_token = os.environ.get(MESSAGE_API_TOKEN_ENV)
    if not configured_token:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Message API is disabled until a local token is configured",
        )
    if x_assistant_token is None or not secrets.compare_digest(
        x_assistant_token, configured_token
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid message API token",
        )


initialize_database()


app = FastAPI(title="Personal AI Passive Memory Assistant")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "message": "PC backend is running"}


@app.post(
    "/api/messages",
    response_model=Message,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(require_message_api_token)],
)
def create_message(message: MessageCreate) -> dict[str, object]:
    message_id = str(uuid4())
    created_at = datetime.now(timezone.utc).isoformat()

    with connect_database() as connection:
        connection.execute(
            """
            INSERT INTO messages (
                id,
                source_app,
                source_type,
                conversation_name,
                conversation_type,
                sender,
                title,
                raw_text,
                received_at,
                created_at,
                device_id,
                notification_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                message_id,
                message.source_app,
                message.source_type,
                message.conversation_name,
                message.conversation_type,
                message.sender,
                message.title,
                message.raw_text,
                message.received_at.isoformat(),
                created_at,
                message.device_id,
                message.notification_id,
            ),
        )
        saved_message = connection.execute(
            "SELECT * FROM messages WHERE id = ?", (message_id,)
        ).fetchone()

    if saved_message is None:
        raise RuntimeError("Saved message could not be read back")
    return dict(saved_message)


@app.get(
    "/api/messages",
    response_model=list[Message],
    dependencies=[Depends(require_message_api_token)],
)
def list_messages() -> list[dict[str, object]]:
    with connect_database() as connection:
        messages = connection.execute(
            "SELECT * FROM messages ORDER BY created_at DESC, rowid DESC"
        ).fetchall()
    return [dict(message) for message in messages]


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=8000)
