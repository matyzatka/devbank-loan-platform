# Plán budoucího AWS deploymentu

Dokument vymezuje release a provozní lifecycle pro [cílovou AWS architekturu](aws-architecture.md). Implementace infrastruktury není součástí aktuálního stavu repozitáře.

## Předpoklady a rozhodovací brána

Lokální CI, Compose a smoke test zůstávají první bránou. Před implementací AWS infrastruktury se musí schválit varianta A nebo B, region, rozpočtový limit, doména, délka existence prostředí a vlastník cleanupu. Image vznikají z existujících `backend/Dockerfile` a `frontend/Dockerfile`.

## Fáze realizace

1. **Infrastructure as Code:** připravit VPC, subnety, routes, endpoints, security groups, ECR, ECS, ALB, log groups, Secrets Manager a zvolenou datovou vrstvu. Infrastructure workflow musí být oddělený od aplikačního deploymentu.
2. **Registry:** vytvořit oddělené ECR repositories `devbank-backend` a `devbank-frontend`, zapnout immutable tags, lifecycle policy a scanning.
3. **Datová vrstva:** pro ukázkové prostředí vytvořit RDS PostgreSQL Single-AZ a dočasný jednouzlový Kafka task; pro produkční prostředí RDS Multi-AZ a MSK. Před rolloutem ověřit TLS, DNS, přihlašovací údaje, topic a DB konektivitu.
4. **Task definitions:** definovat frontend, API, worker a jednorázový migration task. Image vždy referencovat digestem nebo unikátním Git SHA, nikdy `latest`.
5. **Observabilita:** založit log groups, dashboard a alarmy ještě před prvním aplikačním deploymentem.
6. **Aplikační rollout:** spustit migraci, aktualizovat API/worker/frontend a provést vzdálený smoke test s fiktivní žádostí.
7. **Acceptance:** ověřit health, stavový přechod, audit, event correlation, idempotentní replay a rollback.

## Konfigurace aplikačních rolí

API a worker používají stejný backendový image označený Git SHA. Jejich odpovědnost určuje ECS task definition:

| Proměnná | Loan API | Processing Worker |
|---|---:|---:|
| `SPRING_PROFILES_ACTIVE` | `prod` | `prod` |
| `LOAN_PLATFORM_API_ENABLED` | `true` | `false` |
| `LOAN_PLATFORM_WORKER_ENABLED` | `false` | `true` |
| `LOAN_PLATFORM_OUTBOX_PUBLISHER_ENABLED` | `true` | `false` |
| `LOAN_PLATFORM_DEMO_DATA_ENABLED` | `false` | `false` |
| `SPRING_MAIN_WEB_APPLICATION_TYPE` | výchozí | `none` |

JDBC URL, Kafka bootstrap servery, topic a consumer group jsou necitlivé runtime parametry. Hesla přicházejí z Secrets Manageru podle [bezpečnostního modelu](aws-security.md).

Flyway v ukázkovém prostředí běží při startu aplikace. Produkční rollout používá jednorázový ECS migration task před aktualizací služeb; aplikační databázový účet díky tomu dlouhodobě nepotřebuje DDL oprávnění. Migrace musí zůstat zpětně kompatibilní s předchozí verzí aplikace.

## CI/CD z GitHub Actions

Stávající CI bez deploye zůstane povinné pro každý push a pull request. Nový deployment workflow se přidá až v další schválené fázi:

```mermaid
flowchart LR
    PR["Pull request"] --> CI["Maven + npm + Docker + smoke"]
    CI -->|"merge na chráněnou větev"| OIDC["GitHub OIDC → omezená AWS role"]
    OIDC --> BUILD["Build obou image"]
    BUILD --> SCAN["Scan a policy gate"]
    SCAN --> ECR["Push image s Git SHA + digest"]
    ECR --> MIG["Jednorázový Flyway migration task"]
    MIG --> ECS["Nová ECS task definition revision"]
    ECS --> DEPLOY["Rolling nebo blue/green rollout"]
    DEPLOY --> CHECK["Health + event-flow smoke test"]
    CHECK -->|"chyba"| RB["Rollback na předchozí digest"]
```

Deployment workflow má používat GitHub Environment s required reviewerem. OIDC claim omezuje repository a environment. Build probíhá jednou; tentýž digest se promuje mezi prostředími. Workflow nesmí číst aplikační secrets.

## Health check a observabilita

- ALB kontroluje frontend `/` a interní API `/actuator/health/readiness`;
- ECS deployment circuit breaker automaticky označí rollout za neúspěšný;
- liveness API používá `/actuator/health/liveness`, ale nesmí nahrazovat readiness;
- CloudWatch Logs přijímá strukturované JSON logy z `application-prod.yml`;
- log groups jsou oddělené pro frontend, API, worker a migraci;
- dashboard sleduje ALB 4xx/5xx a latency, ECS CPU/memory/restarts, RDS connections/storage/latency, Kafka consumer lag a outbox pending metriku;
- alarmy pokrývají unhealthy targets, opakované restarty, rostoucí outbox, consumer lag, nedostupnost DB/Kafka a vyčerpání kapacity.

Worker bez HTTP endpointu potřebuje provozní metriku: poslední úspěšné zpracování, počet chyb a consumer lag. Pouhý stav `RUNNING` neprokazuje funkční event flow.

## Rollback

1. Každý release uchovává předchozí task definition revision a image digest.
2. Selhání ALB health checku nebo post-deploy smoke testu zastaví rollout.
3. ECS service se vrátí na předchozí revision; image tag se nepřepisuje.
4. Databázové migrace jsou expand/contract a zpětně kompatibilní. Automatický rollback destruktivní migrace se neprovádí.
5. U datové chyby se zastaví zápis, vyhodnotí point-in-time restore a incidentní postup; obnova databáze není běžný aplikační rollback.
6. Kafka event schema musí být kompatibilní alespoň s předchozí verzí producenta a consumera.

Produkční varianta používá ECS blue/green deployment přes CodeDeploy nebo kontrolovaný rolling update s `minimumHealthyPercent=100`; ukázkové prostředí může použít jednoduchý rolling update.

## Cleanup

### Ukázkové prostředí

- prostředí má povinné tagy `Project`, `Environment`, `Owner`, `ExpiresAt` a `ManagedBy`;
- po skončení vymezeného provozu se nejdřív škálují ECS services na nulu, poté se odstraní ALB, tasks, EFS access points/volumes podle pravidel, log groups a registry artefakty dle retention policy;
- před odstraněním persistentních dat se ověří, že obsahují pouze fiktivní data a není požadován export;
- AWS Budgets a cost anomaly alarm upozorní, pokud prostředí zůstane běžet déle.

### Produkční varianta

- RDS deletion protection, finální snapshot a retention záloh;
- MSK a síťové zdroje se nemažou aplikačním workflow;
- cleanup vyžaduje samostatné schválení a zdokumentovaný restore test;
- ECR a logy mají lifecycle/retention pravidla místo ad-hoc mazání.

## Rizika a nákladové pasti

| Riziko | Dopad | Opatření |
|---|---|---|
| MSK Provisioned běží nepřetržitě | vysoký fixní účet i bez provozu | v ukázkovém prostředí nepoužívat; budget alarm a časové omezení |
| NAT Gateway za každou AZ | hodinové poplatky a data processing | VPC endpoints, ověřený počet NAT, jednodušší ukázková síť |
| ALB, RDS Multi-AZ a nevyužité Fargate tasky | stálé náklady | oddělit ukázkové a produkční prostředí, nastavit limity a termín odstranění |
| CloudWatch bez retention | nekontrolovaný růst log storage | explicitní retence a filtrace hlučných logů |
| ECR bez lifecycle | hromadění image | uchovat poslední releasy a aktivní digests |
| vysoký cross-AZ traffic | datové poplatky a latence | vědomé rozmístění, měření; neobětovat HA bez schválení |
| auto scaling bez stropu | nekontrolovaný účet | min/max capacity a budget/metric alarmy |
| Jednouzlová Kafka ve Fargate | ztráta dostupnosti nebo dat | pouze ukázkové prostředí; produkčně MSK |
| Flyway ze všech API tasků | závody a širší DB oprávnění | samostatný migration task |
| secrets v diagnostice prostředí | únik přihlašovacích údajů | zákaz výpisů prostředí, redakce logů, omezený přístup |

## Co ještě brání produkčnímu nasazení

- není zvolena ani implementována Infrastructure as Code;
- aplikace nemá produkční autentizaci a autorizaci;
- není potvrzen Kafka auth mechanismus ani kompatibilní konfigurace klienta pro MSK;
- chybí samostatný migrační lifecycle a ověřená backward compatibility DB/event schémat;
- chybí worker lag/heartbeat metriky, alarmy a runbooky;
- není definována disaster recovery strategie, RPO/RTO a test obnovy;
- nejsou hotové penetrační, privacy, compliance a load testy;
- nebyl schválen účet, region, DNS, certifikát, budget ani odpovědnost za provoz.

## Volba varianty

Varianta A je určena pro časově omezené ukázkové nasazení. Zachovává immutable image, oddělené ECS role, OIDC deployment, bezpečnou správu secrets, CloudWatch a kompletní event flow bez nepřiměřených stálých nákladů na managed datovou vrstvu. Varianta B je závazný výchozí bod pro trvalý provoz; před použitím reálných dat vyžaduje RDS/MSK a dokončení uvedených bezpečnostních opatření.
