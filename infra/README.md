# Lokální infrastruktura

Docker Compose spouští PostgreSQL, jednouzlový Kafka broker, Loan API, Processing Worker a frontend. Je to vývojové demo, nikoli produkční topologie.

```powershell
docker compose up --build
docker compose ps
docker compose logs -f loan-api processing-worker
docker compose down
```

Demo data zapíná pouze proměnná `LOAN_PLATFORM_DEMO_DATA_ENABLED=true` u Loan API. Worker je neseeduje. Kafka a jOOQ běží na úrovni `WARN`, aby provozní log ukazoval business změny a korelační identifikátory.
