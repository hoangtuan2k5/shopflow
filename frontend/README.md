# ShopFlow Frontend

Vue 3 + Vite frontend for the ShopFlow monorepo.

## Local Setup

```sh
cp .env.example .env.local
npm install
npm run dev
```

The local app runs at `http://localhost:5173`.

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Backend API base URL for local development. |

## Checks

```sh
npm run lint:ci
npm run build
```
