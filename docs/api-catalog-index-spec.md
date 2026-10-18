# Catalog vector index API — SF-75 / SF-93

## Purpose and fixed decisions

Owner-operated semantic retrieval for the future product advisor. This does not
change `GET /products`, storefront search or introduce a chat screen.

- Gemini Developer API (AI Studio): `models/gemini-embedding-2`.
- Collection `products`, 768 float dimensions, `Cosine` distance. This is the
  initial efficiency/quality choice; verify retrieval on Vietnamese queries.
- Point ID = positive product ID; payload `{productId, name}`. No new PostgreSQL
  table, embedding column or mapping table. Images remain on R2.
- Changing model/dimensions requires a new collection or deleting and rebuilding
  this derived index. Never compare vectors from different models/dimensions.
- Gemini Embedding 2 normalizes reduced vectors; do not normalize again.

## Embedding requests

POST `https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-2:batchEmbedContents`
for products; `:embedContent` for one query. Authentication header
`x-goog-api-key`, without Bearer. Each batch request contains `model`, `content`
and `embedContentConfig: {outputDimensionality: 768}`. Default batch size is 100.

Product text: `title: {name} | text: {description}`.
Example: `title: Màng lọc HEPA Sharp | text: Lõi thay thế cho máy lọc không khí`.
A null/empty description leaves `text: ` empty. Names are NOT NULL; if a future
source has no title, use `title: none`.

Query text: `task: search result | query: {trimmed q}`.
Example: `task: search result | query: màng lọc cho phòng ngủ`.
Do not pass `taskType`. Swapping these prefixes can silently reduce retrieval
quality without an API error. Input text is not truncated by ShopFlow; provider
input limits still apply and failures return 503.

## POST /catalog-index

SHOP_OWNER only; session and CSRF required. No request body.
Returns 200: `{"indexed":90,"removed":0,"dimensions":768}`.

Read active products ordered by ID. Ensure collection exists on this request,
not during boot. Upsert each batch using stable product IDs; only after every
batch succeeds scroll all IDs and delete those outside the active set. Empty
catalog clears the derived collection. Reindex is serialized within this backend
instance. Successful reruns leave the same IDs/count; a failed run may contain
partially updated batches but never proceeds to stale-point deletion. It is not
an atomic index swap. Run again after correcting an upstream failure.

Products changed/inactivated/deleted appear in the index after the next manual
reindex; no background synchronization. Do not edit catalog data during a run.

## GET /catalog-index/search?q=…&limit=5

SHOP_OWNER only. `q` required, nonblank; `limit` integer 1–20, default 5.
Returns 200 array sorted descending by similarity:
`[{"productId":42,"name":"Màng lọc HEPA Sharp","score":0.82}]`.
Score is cosine similarity, not confidence or proof of model compatibility.
No minimum score is imposed; unrelated queries may still return nearest products.
Collection must be indexed first; a missing collection returns 503 with reindex
instructions. No automatic collection creation on search.

## Errors

JSON `{message, status}` for application errors; security rejects before any
Gemini/Qdrant call.

| Status | Meaning |
| --- | --- |
| 400 | Missing/blank q, noninteger or out-of-range limit |
| 401 | Anonymous caller (POST must carry CSRF to reach authentication handling) |
| 403 | Wrong role or missing/invalid CSRF on POST |
| 503 | Missing Gemini key, upstream network/HTTP error or invalid embedding response |

A Qdrant 400 dimension mismatch is surfaced as 503 with its original response
body and advice to delete/rebuild the derived collection. Other provider errors
expose only service name and status, not headers, API keys or product text.

## Configuration and operations

Shared `application.yaml`: `shopflow.qdrant.{url,api-key,dimensions,collection}`
and `shopflow.gemini.{base-url,api-key,batch-size}`. Environment:
`QDRANT_URL` (local default `http://localhost:6333`), `QDRANT_API_KEY`,
`GEMINI_API_KEY`. Production URL is `http://qdrant:6333`; no public Qdrant ports.
Boot and existing features do not make requests to either external service.
CI uses mock Gemini, plus an isolated real Qdrant container for storage behavior.

Manual authentication: POST `/auth/login` with JSON username/password and save
cookies, GET `/auth/session` to refresh `XSRF-TOKEN`, then POST `/catalog-index`
with the session cookies and `X-XSRF-TOKEN` containing the cookie's raw token.
Same-origin browser/proxy paths may prepend `/api`; backend paths above do not.
Only send product names/descriptions and search text to Gemini, not customer or
order records. Root `.env` is not automatically loaded by a host-run Java process;
export it before running backend (see README).

## References (checked 2026-10-18)

- https://ai.google.dev/gemini-api/docs/embeddings
- https://ai.google.dev/api/embeddings
- https://api.qdrant.tech/api-reference/points/upsert-points
- https://api.qdrant.tech/api-reference/points/scroll-points
- https://api.qdrant.tech/api-reference/search/query-points

Out of scope: UI, chatbot, scheduled indexing, retries, embedding cache, image
embeddings, refund/order analytics and database migrations.
