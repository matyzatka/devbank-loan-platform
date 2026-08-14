# Architektura DevBank

## Procesní hranice

Jeden repozitář a jeden backendový artefakt se spouští ve dvou rolích. `loan-api` vystavuje HTTP API a publikuje outbox. `processing-worker` nevystavuje HTTP, konzumuje Kafka události a provádí předběžnou validační a procesní kontrolu. Procesy sdílejí PostgreSQL, ale žádný z nich nekonzumuje vlastní event bez business důvodu.

## Hlavní tok

```text
POST /applications
  -> transakce: žádost SUBMITTED + audit + LoanApplicationSubmitted v outboxu
  -> publisher odešle event do Kafka
  -> worker event deduplikuje
  -> transakce: výsledek kontroly + UNDER_REVIEW + audit + navazující event
  -> poradce potvrdí APPROVED nebo REJECTED
  -> transakce: stav + případný důvod zamítnutí + audit + outbox
```

## Konzistence a opakování

- HTTP create používá `Idempotency-Key`; stejný klíč a payload vrátí původní žádost, odlišný payload skončí konfliktem.
- Stavové změny používají klientem pozorovanou verzi a databázový compare-and-set update.
- Stav, audit a outbox se ukládají atomicky.
- Kafka zajišťuje doručení alespoň jednou; worker proto uchovává tabulku zpracovaných eventů.
- Selhání před potvrzením publikace ponechá outbox event k dalšímu pokusu. Projekt netvrdí exactly-once zpracování.

## Audit a korelace

Historie ukládá původní a nový stav, verzi, čas, aktéra, `requestId`, `eventId` a důvod zamítnutí. Logovací kontext nese `requestId`, `applicationId` a `eventId`; běžný provoz omezuje hlučné Kafka a jOOQ logy na `WARN`.

## Demo data

Seeder běží jen při `loan-platform.demo-data.enabled=true`. Transakční PostgreSQL advisory lock serializuje souběžné starty. Stabilní UUID a `ON CONFLICT DO NOTHING` zajišťují opakovatelnost. Produkční profil seeder explicitně vypíná.

## Omezení dema

Kontrola není scoring a nerozhoduje o bonitě. Autentizace, autorizace, vzdálený deployment a AWS služby nejsou v aktuálním rozsahu.
