# DevBank — produktový a architektonický brief

## Účel

DevBank je profesionální portfolio demonstrátor platformy pro zpracování korporátních úvěrů. Neobsahuje reálná klientská data, proprietární zdrojový kód ani identitu skutečné banky. Nejde o skutečný úvěrový scoring.

## Produktová identita

- název: **DevBank**;
- podtitul: **KORPORÁTNÍ ÚVĚRY**;
- přihlášený uživatel: **Matouš Zátka**;
- role: **Úvěrový poradce**;
- aplikace musí na každé obrazovce jasně uvádět, že jde o demo prostředí;
- patička ani informace o autorství nejsou součástí produktu.

## Jazyk

Veškeré uživatelské rozhraní, placeholdery, validační a API chyby, OpenAPI popisy, README a ostatní dokumentace jsou česky. Názvy tříd, endpointů, databázových objektů, eventů, technických identifikátorů a logovací zprávy mohou zůstat anglicky.

## Produktový tok

1. Založení žádosti.
2. Předběžná automatická kontrola.
3. Posouzení specialistou.
4. Schválení nebo zamítnutí.

Stavový model: `SUBMITTED -> UNDER_REVIEW -> APPROVED | REJECTED`.

Worker vlastní přechod do `UNDER_REVIEW`. Operátor smí rozhodnout pouze o žádosti ve stavu `UNDER_REVIEW`. Schválení i zamítnutí musí potvrdit v dialogu; zamítnutí vyžaduje důvod. Každá akce zobrazí jednoznačný výsledek a zachová idempotentní chování.

## Obsah a prezentace

- používat pouze realistická fiktivní firemní data;
- nepoužívat jména politiků, skutečných osob, testovací názvy ani nesmyslné částky;
- technické verze nezobrazovat v hlavním seznamu;
- audit zobrazit produktově, technické korelační údaje ponechat v rozbalitelném detailu;
- pro CZK zobrazovat celé koruny, například `2 500 000 Kč`;
- vstup částky formátovat česky, do API a databáze posílat číslo;
- seznam, detail a formulář musí používat konzistentní formát částek.

## Odpovědnosti procesů

### Loan API

- přijímá REST commandy a obsluhuje query endpointy;
- spravuje hlavní stav žádosti a HTTP idempotenci;
- ukládá stav, audit a outbox v jedné transakci;
- publikuje události z transactional outboxu.

### Loan Processing Worker

- konzumuje `LoanApplicationSubmitted`;
- provádí idempotentní předběžnou validační a procesní kontrolu;
- ukládá výsledek, posouvá žádost do `UNDER_REVIEW` a zapisuje audit i navazující event.

Procesy mohou sdílet repozitář, artefakt i PostgreSQL, ale jsou logicky oddělené a samostatně spustitelné.

## Spolehlivost a audit

Projekt prokazuje transakční hranice, optimistic locking, HTTP idempotenci, transactional outbox, doručení alespoň jednou, deduplikaci eventů, retry/DLT chování a dohledatelnost přes `requestId`, `applicationId` a `eventId`. Netvrdí exactly-once zpracování.

Povinné testy pokrývají duplicitní HTTP request, duplicitní Kafka event, neplatný přechod, optimistic locking konflikt, pád před potvrzením publikace a souběžný start více seederů bez duplicit.

## Demo data

Při lokálním spuštění se databáze doplní malou deterministickou sadou věrohodných fiktivních žádostí. Seeder je konfigurovatelný, v produkčním profilu vypnutý a bezpečný při souběžném startu více instancí. Používá stabilní identifikátory, databázový zámek, jednu transakci a konflikty ignoruje. Finální lokální sada nesmí obsahovat stará testovací data.

## Technologická hranice

Základ tvoří Java 21, Spring Boot, Maven, PostgreSQL, Flyway, jOOQ, Kafka-compatible broker, JUnit 5, Testcontainers, React, TypeScript, Vite, Docker a Docker Compose. Další technologie se nepřidávají bez konkrétního důvodu. AWS není součástí tohoto rozsahu.

Lokální tok musí být funkční a otestovaný: `API -> PostgreSQL -> outbox -> Kafka -> worker -> změna stavu`.

## Definice hotového řešení

- API a worker běží jako dva procesy a hlavní tok funguje end-to-end;
- workflow odmítá neplatné přechody a důvod zamítnutí je uložen a auditovatelný;
- auditní historie je dostupná v UI;
- UI odpovídá čtyřkrokovému toku a je celé česky;
- Compose spustí kompletní lokální systém;
- povinné testy, frontend lint a oba buildy projdou;
- demo data jsou realistická, deterministická a bezpečná pro více instancí.
