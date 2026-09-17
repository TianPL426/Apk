from fastapi import FastAPI


app = FastAPI(title="Personal AI Passive Memory Assistant")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "message": "PC backend is running"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=8000)
