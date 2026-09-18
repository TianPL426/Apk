import os
import secrets
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Annotated, Literal
from uuid import uuid4

from fastapi import Depends, FastAPI, Header, HTTPException, Response, status
from pydantic import BaseModel, Field


DATA_DIR = Path(__file__).resolve().parent / "data"
DATABASE_PATH = DATA_DIR / "assistant.db"
MESSAGE_API_TOKEN_ENV = "ASSISTANT_MESSAGE_API_TOKEN"


class MessageCreate(BaseModel):
    client_message_id: str | None = Field(default=None, min_length=1, max_length=128)
    source_app: str = Field(min_length=1)
    package_name: str | None = None
    source_type: str = Field(min_length=1)
    conversation_name: str | None = None
    conversation_type: Literal["group", "private"] | None = None
    sender: str | None = None
    title: str | None = None
    raw_text: str = Field(min_length=1)
    received_at: datetime
    captured_at: datetime | None = None
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
        existing_columns = {
            row["name"] for row in connection.execute("PRAGMA table_info(messages)")
        }
        for name, declaration in (
            ("client_message_id", "TEXT"),
            ("package_name", "TEXT"),
            ("captured_at", "TEXT"),
        ):
            if name not in existing_columns:
                connection.execute(
                    f"ALTER TABLE messages ADD COLUMN {name} {declaration}"
                )
        connection.execute(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                idx_messages_device_client_message
            ON messages (device_id, client_message_id)
            WHERE client_message_id IS NOT NULL
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
def create_message(message: MessageCreate, response: Response) -> dict[str, object]:
    message_id = str(uuid4())
    created_at = datetime.now(timezone.utc).isoformat()
    received_at = message.received_at.isoformat()
    captured_at = message.captured_at.isoformat() if message.captured_at else None

    with connect_database() as connection:
        existing_message = None
        if message.client_message_id is not None:
            existing_message = connection.execute(
                """
                SELECT * FROM messages
                WHERE device_id = ? AND client_message_id = ?
                """,
                (message.device_id, message.client_message_id),
            ).fetchone()

        expected_values = {
            "client_message_id": message.client_message_id,
            "source_app": message.source_app,
            "package_name": message.package_name,
            "source_type": message.source_type,
            "conversation_name": message.conversation_name,
            "conversation_type": message.conversation_type,
            "sender": message.sender,
            "title": message.title,
            "raw_text": message.raw_text,
            "received_at": received_at,
            "captured_at": captured_at,
            "device_id": message.device_id,
            "notification_id": message.notification_id,
        }

        if existing_message is not None:
            if any(
                existing_message[field] != value
                for field, value in expected_values.items()
            ):
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="client_message_id already exists with different content",
                )
            response.status_code = status.HTTP_200_OK
            return dict(existing_message)

        try:
            connection.execute(
                """
                INSERT INTO messages (
                    id,
                    client_message_id,
                    source_app,
                    package_name,
                    source_type,
                    conversation_name,
                    conversation_type,
                    sender,
                    title,
                    raw_text,
                    received_at,
                    captured_at,
                    created_at,
                    device_id,
                    notification_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    message_id,
                    message.client_message_id,
                    message.source_app,
                    message.package_name,
                    message.source_type,
                    message.conversation_name,
                    message.conversation_type,
                    message.sender,
                    message.title,
                    message.raw_text,
                    received_at,
                    captured_at,
                    created_at,
                    message.device_id,
                    message.notification_id,
                ),
            )
        except sqlite3.IntegrityError:
            if message.client_message_id is None:
                raise
            concurrent_message = connection.execute(
                """
                SELECT * FROM messages
                WHERE device_id = ? AND client_message_id = ?
                """,
                (message.device_id, message.client_message_id),
            ).fetchone()
            if concurrent_message is None or any(
                concurrent_message[field] != value
                for field, value in expected_values.items()
            ):
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="client_message_id already exists with different content",
                )
            response.status_code = status.HTTP_200_OK
            return dict(concurrent_message)
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
