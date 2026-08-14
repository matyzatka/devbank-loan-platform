# LoanFlow frontend

React and TypeScript operations UI for the corporate loan workflow. Vite proxies `/api` and `/actuator` to the backend on port `8080` during local development.

```bash
npm install
npm run dev
```

Quality checks:

```bash
npm run lint
npm test
npm run build
```

Server state is managed by TanStack Query, form state by React Hook Form with Zod validation, and navigation by React Router. The UI intentionally uses an original yellow/black/grey banking-inspired visual system and no proprietary branding or assets.
