# Bezpečnostní návrh pro AWS

Dokument stanovuje bezpečnostní model cílové AWS architektury. Přihlašovací údaje ani hodnoty secrets nejsou součástí repozitáře.

## Zásady

- žádné dlouhodobé AWS access keys v GitHubu, ECS tasku, image ani repozitáři;
- GitHub Actions používá krátkodobé přihlašovací údaje přes OIDC a `AssumeRoleWithWebIdentity`;
- každá runtime role má pouze oprávnění potřebná pro svůj proces;
- veřejně přístupný je pouze ALB na portu 443;
- produkční data jsou šifrovaná při přenosu i v klidu;
- secrets se nepropagují do logů, build arguments ani image layers;
- ukázkové prostředí používá výhradně fiktivní data.

## Síť a security groups

VPC má veřejné subnety pro ALB a privátní subnety pro ECS a datové služby. Produkční varianta používá minimálně dvě AZ. NAT Gateway není bezpečnostní podmínka: pokud tasky nepotřebují obecný internet, preferují se VPC endpoints pro ECR API/DKR, S3, CloudWatch Logs, Secrets Manager a STS.

| Security group | Povolený ingress | Povolený egress |
|---|---|---|
| `alb-sg` | internet `443` | `frontend-sg:80` |
| `frontend-sg` | `alb-sg:80` | `api-sg:8080`, DNS a nezbytné AWS endpoints |
| `api-sg` | `frontend-sg:8080` | `db-sg:5432`, `kafka-sg:909x`, AWS endpoints |
| `worker-sg` | žádný aplikační ingress | `db-sg:5432`, `kafka-sg:909x`, AWS endpoints |
| `db-sg` | `api-sg:5432`, `worker-sg:5432` | pouze odpovědi / nezbytná správa |
| `kafka-sg` | `api-sg` a `worker-sg` na TLS broker port | broker komunikace a nezbytná správa |

Security groups se odkazují navzájem, nepoužívají široké CIDR rozsahy pro datové porty. ECS tasky nemají public IP. HTTP se na ALB přesměruje na HTTPS a TLS policy zakáže zastaralé protokoly.

## IAM role

### ECS task execution role

Společná execution role může pouze:

- stáhnout image z konkrétních ECR repositories;
- zapsat log stream do konkrétních CloudWatch log groups;
- načíst pouze secrets uvedené v dané task definition;
- použít konkrétní KMS klíč jen pro dešifrování těchto secrets.

### API task role

API nepotřebuje ECR ani CloudWatch API oprávnění, která zajišťuje execution role. Při SASL/SCRAM autentizaci MSK může být aplikační task role bez AWS oprávnění. Při MSK IAM autentizaci dostane pouze connect/write oprávnění pro konkrétní cluster a topic. Nemá oprávnění číst jiné secrets, spravovat ECS ani měnit infrastrukturu.

### Worker task role

Stejný princip jako API, ale Kafka oprávnění pouze connect/read/describe pro konkrétní topic a consumer group. Worker nepotřebuje write oprávnění, pokud nepublikuje navazující business event; jakmile jej publikuje, rozšíření musí být omezené na konkrétní topic.

### GitHub deployment role

Trust policy omezuje OIDC issuer na `token.actions.githubusercontent.com`, konkrétní GitHub repository, chráněnou větev nebo schválené environment. Role smí:

- získat ECR authorization token a pushnout image pouze do DevBank repositories;
- registrovat novou revision pouze určených ECS task definitions;
- aktualizovat pouze určené ECS services;
- použít `iam:PassRole` pouze pro konkrétní ECS execution/task role;
- číst stav rolloutů, nikoli vytvářet síť, databáze nebo IAM role.

Infrastructure provisioning má mít jinou roli a jiný schvalovaný workflow než aplikační deployment.

## Secrets a konfigurace

Secrets Manager obsahuje databázové heslo a případně Kafka SASL přihlašovací údaje. ECS je injektuje přímo do `SPRING_DATASOURCE_PASSWORD` a souvisejících proměnných. Necitlivé hodnoty, například topic, group ID, port a feature role, jsou v task definition nebo SSM Parameter Store.

- secrets se nepředávají jako Docker build args;
- GitHub Actions jejich hodnotu nepotřebuje;
- rotace databázového hesla vyžaduje koordinované obnovení spojení nebo restart tasků;
- log configuration nesmí vypisovat environment ani Spring configuration dump;
- Actuator nevystavuje hodnoty konfigurace a health detail zůstává skrytý.

## Ochrana dat a logů

- RDS, MSK, EFS, ECR a CloudWatch log groups používají encryption at rest;
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
