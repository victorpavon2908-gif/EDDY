"""Wake a Render Free backend on demand.

This helper is intentionally NOT a permanent keep-alive loop. Render Free web
services spin down after 15 minutes without inbound traffic and consume the
workspace's Free instance hours while running. Run this helper only when a real
client session needs the backend, for example during testing or before an
integration test.

Usage:
    python render_free_wakeup.py https://eddy-ai-ny8o.onrender.com
"""

from __future__ import annotations

import sys
import time
from urllib.parse import urljoin

import requests


def wake(base_url: str, max_wait_seconds: int = 75) -> bool:
    health_url = urljoin(base_url.rstrip("/") + "/", "health")
    deadline = time.monotonic() + max_wait_seconds
    attempt = 0

    while time.monotonic() < deadline:
        attempt += 1
        try:
            response = requests.get(
                health_url,
                timeout=12,
                headers={"User-Agent": "EDDY-Render-Wakeup/1.0"},
            )
            if 200 <= response.status_code < 300:
                print(f"EDDY backend listo en intento {attempt}: {health_url}")
                return True
        except requests.RequestException:
            pass

        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        time.sleep(min(3, remaining))

    print(f"EDDY backend no respondió dentro de {max_wait_seconds}s: {health_url}")
    return False


def main() -> int:
    if len(sys.argv) != 2:
        print("Uso: python render_free_wakeup.py https://tu-servicio.onrender.com")
        return 2
    return 0 if wake(sys.argv[1]) else 1


if __name__ == "__main__":
    raise SystemExit(main())
