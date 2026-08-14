# DevBank — korporátní úvěry

Profesionální portfolio demonstrátor zpracování žádostí o korporátní úvěry. Jde výhradně o demo prostředí s fiktivními daty; systém neprovádí skutečný úvěrový scoring.

## Spuštění

Požadavky: Docker Desktop a Docker Compose.

```powershell
docker compose up --build
```

Po naběhnutí služeb otevřete:

- UI: http://localhost:3000/applications
- OpenAPI: http://localhost:8080/swagger-ui.html
- readiness: http://localhost:8080/actuator/health/readiness

Zastavení:

```powershell
docker compose down
```

Lokální API při startu bezpečně doplní deterministická demo data. Restart ani více API instancí nevytvoří kopie. V produkčním profilu je seeder vypnutý.

## Architektura

Tok: `React -> Loan API -> PostgreSQL + outbox -> Kafka -> Processing Worker -> PostgreSQL`.

Loan API přijímá REST commandy, vlastní hlavní stav žádosti a ukládá změnu, audit a outbox ve stejné transakci. Worker samostatně konzumuje `LoanApplicationSubmitted`, provede předběžnou validační a procesní kontrolu, deduplikuje event a posune žádost do `UNDER_REVIEW`. Podrobnosti jsou v [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Ověření

Backend:

```powershell
cd backend
mvn verify
```

Frontend:

```powershell
cd frontend
npm ci
npm run lint
npm test
npm run build
```

Produktová a architektonická kritéria jsou v [brief.md](brief.md).
