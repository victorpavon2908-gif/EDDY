from types import SimpleNamespace

import backend.app as app_module


def setup_function():
    app_module._client = None


def test_health_reports_model():
    client = app_module.app.test_client()
    response = client.get("/health")

    assert response.status_code == 200
    payload = response.get_json()
    assert payload["status"] == "ok"
    assert payload["model"] == app_module.MODEL


def test_chat_requires_message():
    client = app_module.app.test_client()
    response = client.post("/chat", json={"context": "hola"})

    assert response.status_code == 400
    assert response.get_json() == {"error": "message is required"}


def test_chat_returns_model_reply(monkeypatch):
    fake_responses = SimpleNamespace(
        create=lambda **kwargs: SimpleNamespace(output_text="  Hola, aquí estoy.  ")
    )
    fake_client = SimpleNamespace(responses=fake_responses)
    monkeypatch.setattr(app_module, "get_client", lambda: fake_client)

    client = app_module.app.test_client()
    response = client.post(
        "/chat",
        json={"message": "hola", "context": "El usuario prefiere respuestas breves."},
    )

    assert response.status_code == 200
    assert response.get_json() == {"reply": "Hola, aquí estoy."}


def test_chat_rejects_empty_model_reply(monkeypatch):
    fake_responses = SimpleNamespace(
        create=lambda **kwargs: SimpleNamespace(output_text="   ")
    )
    fake_client = SimpleNamespace(responses=fake_responses)
    monkeypatch.setattr(app_module, "get_client", lambda: fake_client)

    client = app_module.app.test_client()
    response = client.post("/chat", json={"message": "hola"})

    assert response.status_code == 502
    assert response.get_json() == {"error": "empty model response"}


def test_chat_hides_provider_exception_details(monkeypatch):
    def fail(**kwargs):
        raise RuntimeError("secret provider detail")

    fake_client = SimpleNamespace(responses=SimpleNamespace(create=fail))
    monkeypatch.setattr(app_module, "get_client", lambda: fake_client)

    client = app_module.app.test_client()
    response = client.post("/chat", json={"message": "hola"})

    assert response.status_code == 502
    assert response.get_json() == {"error": "ai_request_failed"}
