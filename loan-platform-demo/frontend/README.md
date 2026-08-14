# LoanFlow Frontend

React and TypeScript operations UI for corporate loan applications. User-facing text is intentionally Czech; source identifiers and technical documentation are English. During local development, Vite proxies `/api` and `/actuator` to Loan API on port `8080`.

```powershell
npm.cmd install
npm.cmd run dev
```

Quality checks:

```powershell
npm.cmd run lint
npm.cmd test
npm.cmd run build
```

TanStack Query manages server state, React Hook Form with Zod handles forms and validation, and React Router provides navigation. Redux and a general-purpose UI framework are intentionally omitted because the current scope does not justify them. The original yellow, black, and grey visual system uses no proprietary branding or assets.
