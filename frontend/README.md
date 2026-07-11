# ShopFlow Frontend

Vue 3 + Vite frontend for the ShopFlow monorepo.

## Local Setup

```sh
cp .env.example .env.local
npm install
npm run dev
```

The local app runs at `http://localhost:5173`.

## API Client

The temporary API client lives in `src/api`. It uses Axios and typed interfaces
against the backend OpenAPI document at `/v3/api-docs` until domain endpoints are
ready for generated clients.

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `/api` | Backend API base URL. Vite proxies `/api` to `http://localhost:8080` in development. |

## Checks

```sh
npm run lint:ci
npm run build
```
