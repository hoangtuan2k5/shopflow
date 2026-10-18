# Product advisory chat API — SF-76 / SF-97

Version 1, 2026-10-18. Contract precedes implementation.

## POST /chat/messages

Public for guest, CUSTOMER, WAREHOUSE and SHOP_OWNER. Session login is not
required; CSRF is required. Fetch `GET /auth/session` first, retain cookies and
send the raw XSRF-TOKEN cookie in X-XSRF-TOKEN. Existing `csrf.spa()` handles this;
the historical SF-101 defect is already fixed.

Request:
```json
{"question":"Cần màng lọc cho Winix 5500-2","history":[{"role":"user","content":"Tôi dùng máy Winix"},{"role":"assistant","content":"Bạn cho biết mã máy nhé."}]}
```

Question is trimmed, nonblank, at most 500 UTF-16 code units. Omitted/null history
means empty. Only the last six messages (not pairs) are used; those must have role
`user` or `assistant`, nonblank content up to 2000 code units. Invalid history
cannot introduce a system role. No transcript is persisted on the server. The
browser sends only successful preceding turns, retries reuse exactly the same
question/history, and failed turns never become model history.

Response 200, exactly `reply` and `products`; products use ProductDto.ListItem:
```json
{"reply":"Có lõi ghi rõ dành cho Winix 5500-2. Bạn kiểm tra mã đầy đủ trước khi chọn nhé.\n— Claude Opus 5.5","products":[{"id":110,"name":"Winix Filter H cho 5500-2","price":900000,"stockStatus":"IN_STOCK","imageUrl":"https://media.example.invalid/filter.webp"}]}
```
No suggestion is valid:
```json
{"reply":"Chưa có đủ thông tin để xác nhận màng lọc phù hợp. Bạn cho biết hãng và mã máy đầy đủ nhé.\n— Claude Opus 5.5","products":[]}
```
Examples are illustrative, not factual product offers. VND is integer currency.
Maximum three products, stable model order without duplicates. Displayed cards
are hydrated from current PostgreSQL catalog, not model-generated facts.

## Retrieval and model contract

1. Admit one chat embedding call against the shared chat quota; use
   gemini-embedding-2, 768 dimensions, SF-75 query prefix.
2. Qdrant top five candidates with a configurable initial score floor 0.70.
   This is a retrieval cutoff to evaluate, not evidence of SKU compatibility.
3. Read active products in one query via public
   `CatalogReader.findActiveByIds(List<Long> ids): List<ProductDto.Detail>`.
   Search has a public in-process `CatalogSearch` gateway; owner HTTP index/search
   endpoints remain protected. Do not duplicate an HTTP hop or SDK.
4. Give Gemma the question, bounded history and current candidate details.
   Model output schema: `{reply: string, productIds: integer[]}`. Both branches
   use systemInstruction, thinkingLevel minimal and temperature 0.2. Primary
   gemma-4-31b-it; one fallback to gemma-4-26b-a4b-it after HTTP/network/timeout or
   invalid model JSON/schema. No repeated retry. Missing key fails before calls.
5. Filter returned IDs against hydrated active candidates, deduplicate, limit to
   three, and refresh selected IDs before returning cards. Free-form prose is
   constrained by prompt but whitelist does not prove every sentence true. Numeric
   amounts with currency/unit markers are rejected as invalid model output, using
   the same one-fallback policy; price/stock facts belong in server product cards.

Names/descriptions and history are untrusted data, not system instructions.
Prompt directs the model to ask for missing model codes, distinguish 5500 from
5500-2 and never infer compatibility from score. No invented prices, shipping,
warranty, certifications or actions. Prompt starts with the owner's exact persona
rules, versioned as a backend text resource. Persona text identifies as Claude
Opus 5.5 by owner request; the actual inference provider/model are Google/Gemma.

Persona greeting and identity exceptions apply inside reply: greeting-only returns
exact Vietnamese greeting with no signature; identity-only returns exact identity
with no signature. Other replies end with the signature on its own final line.
The fixed greeting/identity wording has priority over language detection. Other
responses follow the customer's language. UI labels independently support VI/EN.
Only final model text is shown, never thought parts. No external tool execution.

## Errors, quota and timeout

| Status | Example body | Meaning |
| --- | --- | --- |
| 400 | `{"message":"Invalid chat request","status":400}` | Invalid question/history/JSON |
| 403 | Security-filter response | Missing/invalid CSRF; handled before controller |
| 429 | `{"message":"Chat daily limit reached","status":429}` | Chat call budget exhausted; Retry-After seconds until next Vietnam midnight |
| 503 | `{"message":"Product assistant unavailable","status":503}` | Missing key, unavailable embedding/Qdrant/models, invalid final output or exhausted upstream deadline |

Quota baseline: 500 external AI attempts per Vietnam calendar day for chat,
including embedding and each generation/fallback attempt, even failed calls.
Check atomically before each attempt; no call when at cap. Usually two attempts
per question, three with fallback. Owner SF-75 indexing does not use this chat
quota. Counter is single-instance memory and resets after restart; not billing
control across replicas/restarts. No unbounded per-IP maps.

Network budget: chat embedding read up to 3s, Qdrant read up to 1s, generation
up to 6s for the primary and 10s for fallback, each bounded by remaining 18s pipeline deadline; connect timeout
also bounded. Frontend chat overrides only its request timeout to 20s, preserving
other HTTP requests' 10s default. Slow local DB/runtime can still exceed the
network budget; no promise of a hard global wall-clock SLA. No index timeout
changes. Quota and deadline errors do not trigger unbounded provider retries.

Client response validation rejects malformed JSON/product DTOs, sends no API key,
uses normal CSRF cookies and renders prose as escaped plain text, not HTML.

## Verification and scope

Offline backend mocks AI; real catalog data validates active ID whitelist.
Browser tests mock chat and session. Mutation removal of the candidate whitelist
must turn a test red. A separate real run tests both Gemma models/minimal/schema
and records observations without labeling them a deterministic model benchmark.

No upload/image interpretation, streaming, server transcript, localStorage,
agent tools, web browsing, background reindex, new migration or client SDK.
