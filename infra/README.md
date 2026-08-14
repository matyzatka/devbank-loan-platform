# Lokální infrastruktura

Docker Compose spouští PostgreSQL, jednouzlový Kafka broker, Loan API, Processing Worker a frontend. Topologie je určená pro lokální vývoj a ukázkové prostředí; produkční varianta je popsána v AWS dokumentaci.

```powershell
docker compose up --build
docker compose ps
docker compose logs -f loan-api processing-worker
docker compose down
```

Demo data zapíná pouze proměnná `LOAN_PLATFORM_DEMO_DATA_ENABLED=true` u Loan API. Worker je neseeduje. Kafka a jOOQ běží na úrovni `WARN`, aby provozní log ukazoval business změny a korelační identifikátory.
