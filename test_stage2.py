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
DATABASE_PATH = ROOT / "data" / "assistant.db"
TOKEN_ENV = "ASSISTANT_MESSAGE_API_TOKEN"


def request_json(
    url: str,
    *,
    method: str = "GET",
    payload: dict[str, object] | None = None,
    token: str | None = None,
) -> tuple[int, object]:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["X-Assistant-Token"] = token
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    request = Request(url, data=data, headers=headers, method=method)
    with urlopen(request, timeout=2) as response:
        return response.status, json.load(response)


def unused_port() -> int:
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def start_server(token: str | None) -> tuple[subprocess.Popen[str], str]:
    port = unused_port()
    base_url = f"http://127.0.0.1:{port}"
    environment = os.environ.copy()
    if token is None:
        environment.pop(TOKEN_ENV, None)
    else:
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
            status_code, health = request_json(f"{base_url}/health")
            if status_code == 200 and health == {
                "status": "ok",
                "message": "PC backend is running",
            }:
                return process, base_url
        except URLError:
            time.sleep(0.05)

    stop_server(process)
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


def assert_api_disabled_by_default() -> None:
    process, base_url = start_server(None)
    try:
        try:
            request_json(f"{base_url}/api/messages")
        except HTTPError as error:
            assert error.code == 503, error.code
        else:
            raise AssertionError("Message API was available without a configured token")
    finally:
        stop_server(process)


def main() -> None:
    assert_api_disabled_by_default()

    token = secrets.token_urlsafe(32)
    unique_value = uuid4().hex
    payload: dict[str, object] = {
        "source_app": "WeChat",
        "source_type": "manual",
        "conversation_name": "Stage 2 Test Group",
        "conversation_type": "group",
        "sender": "Stage 2 Tester",
        "title": "Persistence test",
        "raw_text": f"Stage 2 persistent test message {unique_value}",
        "received_at": datetime.now(timezone.utc).isoformat(),
        "device_id": "stage2-test-device",
        "notification_id": f"test-{unique_value}",
    }

    first_process, first_base_url = start_server(token)
    try:
        status_code, saved = request_json(
            f"{first_base_url}/api/messages",
            method="POST",
            payload=payload,
            token=token,
        )
        assert status_code == 201, status_code
        assert isinstance(saved, dict)
        message_id = saved["id"]

        status_code, messages = request_json(
            f"{first_base_url}/api/messages", token=token
        )
        assert status_code == 200, status_code
        assert isinstance(messages, list)
        assert any(message["id"] == message_id for message in messages)
        assert DATABASE_PATH.is_file()
    finally:
        stop_server(first_process)

    second_process, second_base_url = start_server(token)
    try:
        status_code, messages_after_restart = request_json(
            f"{second_base_url}/api/messages", token=token
        )
        assert status_code == 200, status_code
        assert isinstance(messages_after_restart, list)
        persisted = next(
            message
            for message in messages_after_restart
            if message["id"] == message_id
        )
        for field, value in payload.items():
            if field == "received_at":
                persisted_time = datetime.fromisoformat(
                    str(persisted[field]).replace("Z", "+00:00")
                )
                expected_time = datetime.fromisoformat(str(value))
                assert persisted_time == expected_time
                continue
            assert persisted[field] == value, (field, persisted[field], value)
    finally:
        stop_server(second_process)

    print("Stage 2 persistence test passed")
    print(f"Database: {DATABASE_PATH}")
    print(f"Saved message id: {message_id}")
    print("Save: passed")
    print("Read before restart: passed")
    print("FastAPI stop and restart: passed")
    print("Read after restart: passed")
    print("Message API disabled without a configured token: passed")


if __name__ == "__main__":
    main()
