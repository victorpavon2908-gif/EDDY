import backend.app as app_module


def test_health_reports_niko_web_math_engine():
    client = app_module.app.test_client()
    response = client.get("/health")

    assert response.status_code == 200
    payload = response.get_json()
    assert payload["status"] == "ok"
    assert payload["engine"] == "niko-web+math"
    assert payload["mode"] == "automatic-research+calculator"
    assert payload["remote_model"] is False


def test_search_requires_message():
    client = app_module.app.test_client()
    response = client.post("/search", json={})

    assert response.status_code == 400
    assert response.get_json() == {"error": "message is required"}


def test_chat_calculates_basic_math_without_web():
    client = app_module.app.test_client()
    response = client.post(
        "/chat",
        json={"message": "Cuánto es 4 + 4", "force_web": False},
    )

    assert response.status_code == 200
    payload = response.get_json()
    assert payload["reply"] == "El resultado es 8."
    assert payload["kind"] == "calculation"
    assert payload["web_used"] is False
    assert payload["sources"] == []


def test_chat_understands_spoken_math():
    client = app_module.app.test_client()
    response = client.post(
        "/chat",
        json={"message": "calculame 20 por ciento de 500", "force_web": False},
    )

    assert response.status_code == 200
    assert response.get_json()["reply"] == "El resultado es 100."


def test_unknown_local_action_is_not_sent_to_research():
    client = app_module.app.test_client()
    response = client.post(
        "/chat",
        json={"message": "compra comida", "force_web": False},
    )

    assert response.status_code == 422
    assert response.get_json() == {"error": "local_command_unknown"}


def test_natural_question_automatically_researches(monkeypatch):
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
        "/chat",
        json={"message": "quién fue Rubén Darío", "force_web": False},
    )

    assert response.status_code == 200
    payload = response.get_json()
    assert payload["kind"] == "research"
    assert payload["web_used"] is True
    assert payload["sources"] == [
        {"title": "Fuente oficial", "url": "https://example.gov/info"},
        {"title": "Medio secundario", "url": "https://example.com/noticia"},
    ]


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


def test_legacy_endpoint_kept_for_old_apks(monkeypatch):
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
