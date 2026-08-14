# Lokální vývoj a provoz

## Požadavky

Pro kompletní běh stačí Docker Desktop s podporou Docker Compose. Volitelná konfigurace vychází z `.env.example`; skutečný `.env` se do Gitu neukládá.

## Kompletní prostředí

```powershell
Copy-Item .env.example .env   # volitelné
docker compose up --build
```

Na Linuxu a macOS použijte `cp .env.example .env`.

| Služba | Úloha | Lokální adresa |
|---|---|---|
| `frontend` | React UI přes Nginx | `http://localhost:3000` |
| `loan-api` | REST API a outbox publisher | `http://localhost:8080` |
| `processing-worker` | Kafka consumer a předběžná kontrola | pouze interní |
| `postgres` | stav, audit, outbox a deduplikace | `localhost:5432` |
| `kafka` | business události | `localhost:9092` |

Užitečné příkazy:

```powershell
docker compose ps
docker compose logs -f loan-api processing-worker
docker compose down
docker compose down --volumes   # včetně lokálních dat
```

## Samostatné procesy

Infrastruktura:

```powershell
docker compose up -d postgres kafka
```

Loan API:

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="local"
$env:LOAN_PLATFORM_API_ENABLED="true"
$env:LOAN_PLATFORM_WORKER_ENABLED="false"
$env:LOAN_PLATFORM_OUTBOX_PUBLISHER_ENABLED="true"
mvn spring-boot:run
```

Processing Worker spusťte v druhém terminálu:

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="local"
$env:SPRING_MAIN_WEB_APPLICATION_TYPE="none"
$env:LOAN_PLATFORM_API_ENABLED="false"
$env:LOAN_PLATFORM_WORKER_ENABLED="true"
$env:LOAN_PLATFORM_OUTBOX_PUBLISHER_ENABLED="false"
mvn spring-boot:run
```

Frontend:

```powershell
cd frontend
$env:VITE_BACKEND_URL="http://localhost:8080"
npm ci
npm run dev
```

Produkční frontendový image používá runtime proměnnou `BACKEND_URL`; backendový hostname ani port nejsou součástí sestaveného JavaScriptu.

## Runtime konfigurace

| Oblast | Proměnné |
|---|---|
| Databáze | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| Kafka | `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `SPRING_KAFKA_CONSUMER_GROUP_ID`, `KAFKA_TOPIC` |
| Role procesu | `LOAN_PLATFORM_API_ENABLED`, `LOAN_PLATFORM_WORKER_ENABLED`, `LOAN_PLATFORM_OUTBOX_PUBLISHER_ENABLED` |
| Prostředí | `SPRING_PROFILES_ACTIVE`, `SERVER_PORT`, `LOAN_PLATFORM_DEMO_DATA_ENABLED` |
| Frontend proxy | `BACKEND_URL` v Nginx, `VITE_BACKEND_URL` ve Vite |

`application-local.yml` je jediná aplikační konfigurace s `localhost` výchozími hodnotami. `application-prod.yml` vypíná referenční data a OpenAPI UI a zapíná strukturované JSON logování.

## Testy

Backend:

```powershell
cd backend
mvn test
```

Frontend:

```powershell
cd frontend
npm ci
npm run lint
npm test
npm run build
```

Kompletní event-flow smoke test:

```powershell
./scripts/smoke-test.ps1 -Build -Cleanup
```

Test sestaví celý stack, ověří frontend a readiness API, založí fiktivní žádost a čeká na auditovaný přechod do `UNDER_REVIEW`.
