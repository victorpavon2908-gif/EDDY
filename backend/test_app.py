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
    assert isinstance(payload["web_search"], bool)


def test_chat_requires_message():
    client = app_module.app.test_client()
    response = client.post("/chat", json={"context": "hola"})

    assert response.status_code == 400
    assert response.get_json() == {"error": "message is required"}


def test_chat_returns_model_reply(monkeypatch):
    class FakeResponse:
        output_text = "  Hola, aquí estoy.  "

        def model_dump(self):
            return {"output": []}

    captured = {}

    def create(**kwargs):
        captured.update(kwargs)
        return FakeResponse()

    fake_client = SimpleNamespace(responses=SimpleNamespace(create=create))
    monkeypatch.setattr(app_module, "get_client", lambda: fake_client)

    client = app_module.app.test_client()
    response = client.post(
        "/chat",
        json={"message": "hola", "context": "El usuario prefiere respuestas breves."},
    )

    assert response.status_code == 200
    assert response.get_json() == {
        "reply": "Hola, aquí estoy.",
        "sources": [],
        "web_used": False,
    }
    if app_module.WEB_SEARCH_ENABLED:
        assert captured["tool_choice"] == "auto"
        assert captured["tools"][0]["type"] == "web_search"
        assert captured["tools"][0]["search_context_size"] == "high"


def test_force_web_requires_tool_and_returns_sources(monkeypatch):
    class FakeResponse:
        output_text = "El dato fue verificado en la web."

        def model_dump(self):
            return {
                "output": [
                    {
                        "type": "web_search_call",
                        "action": {
                            "type": "search",
                            "queries": ["dato actual"],
                            "sources": [
                                {"type": "url", "url": "https://example.com/info"},
                            ],
                        },
                    },
                    {
                        "type": "message",
                        "content": [
                            {
                                "type": "output_text",
                                "text": "El dato fue verificado en la web.",
                                "annotations": [
                                    {
                                        "type": "url_citation",
                                        "title": "Fuente principal",
                                        "url": "https://example.com/info",
                                        "start_index": 0,
                                        "end_index": 10,
                                    }
                                ],
                            }
                        ],
                    },
                ]
            }

    captured = {}

    def create(**kwargs):
        captured.update(kwargs)
        return FakeResponse()

    fake_client = SimpleNamespace(responses=SimpleNamespace(create=create))
    monkeypatch.setattr(app_module, "get_client", lambda: fake_client)

    client = app_module.app.test_client()
    response = client.post(
        "/chat",
        json={"message": "busca el dato actual", "force_web": True},
    )

    assert response.status_code == 200
    payload = response.get_json()
    assert payload["web_used"] is True
    assert payload["sources"] == [
        {"title": "example.com", "url": "https://example.com/info"}
    ]
    if app_module.WEB_SEARCH_ENABLED:
        assert captured["tool_choice"] == "required"


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
