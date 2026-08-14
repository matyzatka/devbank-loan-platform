# Frontend DevBank

React/TypeScript klient pro správu žádostí o korporátní úvěry. UI je v češtině a označené jako demo prostředí.

Pro samostatný vývoj používá Vite reverse proxy. Cíl backendu je konfigurovatelný a `localhost` je pouze lokální výchozí hodnota:

```powershell
$env:VITE_BACKEND_URL="http://localhost:8080"
npm ci
npm run dev
```

Produkční Docker image používá Nginx proxy a runtime proměnnou `BACKEND_URL`; image proto není svázaný s konkrétním hostname ani portem backendu.

```powershell
docker build -t devbank-frontend:local .
docker run --rm -p 3000:80 -e BACKEND_URL=http://host.docker.internal:8080 devbank-frontend:local
```

Kontroly kvality:

```powershell
npm run lint
npm test
npm run build
```
