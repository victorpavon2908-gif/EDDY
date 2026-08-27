import backend.app as app_module


def test_health_reports_eddy_web_engine():
    client = app_module.app.test_client()
    response = client.get("/health")

    assert response.status_code == 200
    payload = response.get_json()
    assert payload["status"] == "ok"
    assert payload["engine"] == "eddy-web"
    assert payload["chatgpt"] is False
    assert payload["openai"] is False


def test_search_requires_message():
    client = app_module.app.test_client()
    response = client.post("/search", json={})

    assert response.status_code == 400
    assert response.get_json() == {"error": "message is required"}


def test_non_web_chat_is_rejected_without_remote_ai():
    client = app_module.app.test_client()
    response = client.post(
        "/chat",
        json={"message": "hola", "force_web": False},
    )

    assert response.status_code == 422
    assert response.get_json() == {"error": "web_search_required"}


def test_search_returns_ranked_sources(monkeypatch):
    fake_results = [
        {
            "title": "Fuente oficial",
            "url": "https://example.gov/info",
            "snippet": "El dato principal fue confirmado oficialmente.",
        },
        {
            "title": "Medio secundario",
            "url": "https://example.com/noticia",
            "snippet": "Una segunda fuente aporta contexto adicional.",
        },
    ]
    monkeypatch.setattr(app_module, "_search_web", lambda query: fake_results)

    client = app_module.app.test_client()
    response = client.post(
        "/search",
        json={"query": "dato actual", "force_web": True},
    )

    assert response.status_code == 200
    payload = response.get_json()
    assert payload["web_used"] is True
    assert "dato actual" in payload["reply"]
    assert payload["sources"] == [
        {"title": "Fuente oficial", "url": "https://example.gov/info"},
        {"title": "Medio secundario", "url": "https://example.com/noticia"},
    ]


def test_chat_endpoint_kept_for_old_apks(monkeypatch):
    monkeypatch.setattr(
        app_module,
        "_search_web",
        lambda query: [
            {
                "title": "Resultado",
                "url": "https://example.com/result",
                "snippet": "Resultado compatible.",
            }
        ],
    )

    client = app_module.app.test_client()
    response = client.post(
        "/chat",
        json={"message": "buscar algo", "force_web": True},
    )

    assert response.status_code == 200
    assert response.get_json()["web_used"] is True


def test_search_handles_no_results(monkeypatch):
    monkeypatch.setattr(app_module, "_search_web", lambda query: [])

    client = app_module.app.test_client()
    response = client.post(
        "/search",
        json={"message": "consulta imposible", "force_web": True},
    )

    assert response.status_code == 502
    payload = response.get_json()
    assert payload["web_used"] is False
    assert payload["sources"] == []
