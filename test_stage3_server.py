import json
import os
import secrets
import socket
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import uuid4


ROOT = Path(__file__).resolve().parent
TOKEN_ENV = "ASSISTANT_MESSAGE_API_TOKEN"


def request_json(
    url: str,
    *,
    method: str = "GET",
    payload: dict[str, object] | None = None,
    token: str,
) -> tuple[int, object]:
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    request = Request(
        url,
        data=data,
        headers={"Content-Type": "application/json", "X-Assistant-Token": token},
        method=method,
    )
    with urlopen(request, timeout=2) as response:
        return response.status, json.load(response)


def unused_port() -> int:
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def start_server(token: str) -> tuple[subprocess.Popen[str], str]:
    port = unused_port()
    base_url = f"http://127.0.0.1:{port}"
    environment = os.environ.copy()
    environment[TOKEN_ENV] = token
    process = subprocess.Popen(
        [
            sys.executable,
            "-m",
            "uvicorn",
            "server:app",
            "--host",
            "127.0.0.1",
            "--port",
            str(port),
        ],
        cwd=ROOT,
        env=environment,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    for _ in range(100):
        if process.poll() is not None:
            output = process.stdout.read() if process.stdout else ""
            raise RuntimeError(f"FastAPI failed to start:\n{output}")
        try:
            with urlopen(f"{base_url}/health", timeout=2):
                return process, base_url
        except URLError:
            time.sleep(0.05)
    raise RuntimeError("FastAPI did not become ready")


def stop_server(process: subprocess.Popen[str]) -> None:
    process.terminate()
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)
    if process.stdout:
        process.stdout.close()


def main() -> None:
    token = secrets.token_urlsafe(32)
    client_message_id = str(uuid4())
    payload: dict[str, object] = {
        "client_message_id": client_message_id,
        "source_app": "WeChat",
        "package_name": "com.tencent.mm",
        "source_type": "notification",
        "conversation_name": "Stage 3 Test Group",
        "conversation_type": "group",
        "sender": "Stage 3 Tester",
        "title": "Idempotency test",
        "raw_text": "The same client message may be retried safely",
        "received_at": datetime.now(timezone.utc).isoformat(),
        "captured_at": datetime.now(timezone.utc).isoformat(),
        "device_id": "stage3-test-device",
        "notification_id": f"notification-{client_message_id}",
    }

    process, base_url = start_server(token)
    try:
        first_status, first = request_json(
            f"{base_url}/api/messages",
            method="POST",
            payload=payload,
            token=token,
        )
        second_status, second = request_json(
            f"{base_url}/api/messages",
            method="POST",
            payload=payload,
            token=token,
        )
        assert first_status == 201
        assert second_status == 200
        assert isinstance(first, dict) and isinstance(second, dict)
        assert first["id"] == second["id"]

        _, messages = request_json(f"{base_url}/api/messages", token=token)
        assert isinstance(messages, list)
        matching = [
            message
            for message in messages
            if message["client_message_id"] == client_message_id
        ]
        assert len(matching) == 1

        conflicting_payload = dict(payload)
        conflicting_payload["raw_text"] = "Different content must not overwrite facts"
        try:
            request_json(
                f"{base_url}/api/messages",
                method="POST",
                payload=conflicting_payload,
                token=token,
            )
        except HTTPError as error:
            assert error.code == 409
        else:
            raise AssertionError("Conflicting client_message_id was accepted")
    finally:
        stop_server(process)

    print("Stage 3 server idempotency test passed")
    print(f"client_message_id: {client_message_id}")
    print("First POST: 201")
    print("Identical retry: 200 with the same server id")
    print("Database rows for client_message_id: 1")
    print("Conflicting reuse: 409")


if __name__ == "__main__":
    main()
