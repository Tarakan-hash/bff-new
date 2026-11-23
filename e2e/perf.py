#!/usr/bin/env python3
"""
Задержка и пропускная способность bff-lk на стенде (./stand.sh up).

  ./perf.py [запросов на сценарий] [параллельность]

Базовая линия — /actuator/health (bff без Temporal). Остальные сценарии идут через Temporal.
"""
import http.client, json, os, statistics, subprocess, sys, threading, time

HERE = os.path.dirname(os.path.abspath(__file__))
HOST = os.environ.get("BFF_HOST", "127.0.0.1")
PORT = int(os.environ.get("BFF_PORT", "18080"))
TOKEN = os.environ.get("TOKEN") or subprocess.check_output(
    [os.path.join(HERE, "token.sh"), "user@example.com"], text=True).strip()

SCENARIOS = [
    # имя, метод, путь, тело, workflow / активити на запрос без кэша (|| — параллельно)
    ("health (без Temporal)", "GET", "/bff-lk/actuator/health", None, "0 / 0"),
    ("GET справочник", "GET", "/bff-lk/api/v1/regions", None, "1 / 2||"),
    ("GET согласия", "GET", "/bff-lk/api/v1/consents", None, "1 / 2||"),
    ("POST полисы (допуск+старт)", "POST", "/bff-lk/api/v1/policies", None, "1 / 2||"),
    ("POST отзыв согласия", "POST", "/bff-lk/api/v1/consents/CSI/recall", None, "2 / 2||+1"),
]


def run(method, path, body, count, concurrency):
    latencies, errors, lock = [], [0], threading.Lock()
    per_thread = count // concurrency

    def worker():
        conn = http.client.HTTPConnection(HOST, PORT, timeout=60)
        headers = {"Authorization": f"Bearer {TOKEN}"}
        for _ in range(per_thread):
            start = time.perf_counter()
            try:
                conn.request(method, path, body=body, headers=headers)
                response = conn.getresponse()
                response.read()
                ok = response.status < 400
            except Exception:
                ok = False
                conn = http.client.HTTPConnection(HOST, PORT, timeout=60)
            elapsed = (time.perf_counter() - start) * 1000
            with lock:
                latencies.append(elapsed)
                if not ok:
                    errors[0] += 1

    threads = [threading.Thread(target=worker) for _ in range(concurrency)]
    started = time.perf_counter()
    for t in threads: t.start()
    for t in threads: t.join()
    wall = time.perf_counter() - started
    latencies.sort()
    q = lambda p: latencies[min(len(latencies) - 1, int(len(latencies) * p))]
    return dict(p50=q(0.50), p95=q(0.95), p99=q(0.99), rps=len(latencies) / wall, errors=errors[0])


def main():
    count = int(sys.argv[1]) if len(sys.argv) > 1 else 200
    concurrency = int(sys.argv[2]) if len(sys.argv) > 2 else 1
    print(f"запросов={count} параллельность={concurrency}")
    print(f"{'сценарий':32} {'wf/акт':>10} {'p50 мс':>8} {'p95 мс':>8} {'p99 мс':>8} {'RPS':>8} {'ошибки':>7}")
    for name, method, path, body, shape in SCENARIOS:
        run(method, path, body, min(count, 20), min(concurrency, 4))  # прогрев
        r = run(method, path, body, count, concurrency)
        print(f"{name:32} {shape:>10} {r['p50']:8.1f} {r['p95']:8.1f} {r['p99']:8.1f} {r['rps']:8.1f} {r['errors']:7}")


if __name__ == "__main__":
    main()
