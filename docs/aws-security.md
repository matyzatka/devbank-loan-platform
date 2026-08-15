# Bezpečnostní návrh pro AWS

Dokument stanovuje ochranné mechanismy [cílové AWS architektury](aws-architecture.md). Přihlašovací údaje ani hodnoty secrets nejsou součástí repozitáře.

## Zásady

- žádné dlouhodobé AWS access keys v GitHubu, ECS tasku, image ani repozitáři;
- GitHub Actions používá krátkodobé přihlašovací údaje přes OIDC a `AssumeRoleWithWebIdentity`;
- každá runtime role má pouze oprávnění potřebná pro svůj proces;
- preferovaným veřejným vstupem je CloudFront s HTTPS a přesměrováním HTTP;
- ALB přijímá HTTP od CloudFront a zůstává přímo dosažitelný jako explicitní kompromis prostředí bez vlastní domény;
- produkční data jsou šifrovaná při přenosu i v klidu;
- secrets se nepropagují do logů, build arguments ani image layers;
- ukázkové prostředí používá výhradně fiktivní data.

## Síť a security groups

VPC má veřejné subnety pouze pro ALB a dvě izolované aplikační subnety pro ECS, RDS a privátní endpoints. Tasky nemají veřejné IP adresy ani cestu přes NAT Gateway. ECR API/DKR, CloudWatch Logs a Secrets Manager jsou dostupné přes interface endpoints; vrstvy image přes S3 gateway endpoint.

| Security group | Povolený ingress | Povolený egress |
|---|---|---|
| `alb-sg` | internet `80` v krátkodobé variantě; `443` v produkci | `frontend-sg:80` |
| `frontend-sg` | `alb-sg:80` | `api-sg:8080`, DNS a nezbytné AWS endpoints |
| `application-sg` | `frontend-sg:8080` | `db-sg:5432`, `kafka-sg:19092`, DNS a privátní AWS endpoints |
| `db-sg` | `application-sg:5432` | žádný iniciovaný provoz |
| `kafka-sg` | `application-sg:19092` | DNS a privátní AWS endpoints |
| `endpoint-sg` | frontend, application a Kafka SG na `443` | odpovědi na navázaná spojení |

Security groups se odkazují navzájem, nepoužívají široké CIDR rozsahy pro datové porty. ECS tasky nemají public IP. CloudFront ukončuje veřejné TLS pomocí spravovaného certifikátu pro doménu AWS a přidává HSTS i další browser security headers. Výchozí certifikát neumožňuje vlastní minimální TLS policy. Produkční varianta používá vlastní doménu a ACM certifikát, šifruje spojení k originu a omezuje ALB pouze na důvěryhodný edge provoz.

## IAM role

### ECS task execution role

Společná execution role může pouze:

- stáhnout image z konkrétních ECR repositories;
- zapsat log stream do konkrétních CloudWatch log groups;
- načíst pouze secrets uvedené v dané task definition;
- použít konkrétní KMS klíč jen pro dešifrování těchto secrets.

### Aplikační task role

Runtime task role nemá žádná AWS API oprávnění. ECR pull, zápis do konkrétní log group a načtení databázového secretu zajišťují oddělené execution role. Jednouzlová Kafka komunikuje pouze uvnitř VPC a nepoužívá AWS IAM autentizaci; to je explicitní omezení ukázkové varianty.

### GitHub deployment role

Trust policy omezuje OIDC issuer na `token.actions.githubusercontent.com`, konkrétní GitHub repository, chráněnou větev nebo schválené environment. Role smí:

- získat ECR authorization token a pushnout image pouze do DevBank repositories;
- registrovat novou revision pouze určených ECS task definitions;
- aktualizovat pouze určené ECS services;
- použít `iam:PassRole` pouze pro konkrétní ECS execution/task role;
- číst stav rolloutů, nikoli vytvářet síť, databáze nebo IAM role.

Infrastructure provisioning má mít jinou roli a jiný schvalovaný workflow než aplikační deployment.

## Secrets a konfigurace

Secrets Manager obsahuje generované databázové heslo a uživatelské jméno. ECS je injektuje přímo do `SPRING_DATASOURCE_PASSWORD` a `SPRING_DATASOURCE_USERNAME`. Necitlivé hodnoty, například JDBC endpoint, topic, group ID, port a role procesu, jsou součástí task definition.

- secrets se nepředávají jako Docker build args;
- GitHub Actions jejich hodnotu nepotřebuje;
- rotace databázového hesla vyžaduje koordinované obnovení spojení nebo restart tasků;
- log configuration nesmí vypisovat environment ani Spring configuration dump;
- Actuator nevystavuje hodnoty konfigurace a health detail zůstává skrytý.

## Ochrana dat a logů

- RDS, ECR, Secrets Manager a CloudWatch Logs používají šifrování v klidu;
- produkční TLS certifikát spravuje ACM;
- logy obsahují `requestId`, `applicationId` a `eventId`, nikoli celé žádosti, přihlašovací údaje nebo citlivé payloady;
- CloudWatch retention je explicitní, ne nekonečná;
- přístup k logům a DB je auditovatelný přes CloudTrail;
- ECR zapíná immutable tags a enhanced/on-push scanning; kritický nález blokuje promotion.

## Další produkční kontroly

Ukázkové prostředí neobsahuje plnou autentizaci a autorizaci. Před zpracováním skutečných úvěrových dat musí produkční varianta doplnit zejména:

- identita uživatele a role-based authorization;
- WAF pravidla, rate limiting a ochrana proti zneužití;
- klasifikace dat, retenční pravidla, GDPR postupy a audit přístupu;
- správa KMS klíčů a oddělení povinností;
- vulnerability management image a závislostí;
- pravidelné testy obnovy, incident response a bezpečnostní monitoring;
- privátní administrativní přístup přes SSM, nikoli otevřené SSH nebo databázové porty.
