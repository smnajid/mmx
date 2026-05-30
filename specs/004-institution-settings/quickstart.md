# Quickstart — Institution settings

## Prerequisites

- Backend running with Flyway migrations applied
- Trader header `X-Trader-Id` (e.g. `trader-a`)

## 1. Onboard an institution

```bash
curl -s -X POST http://localhost:8080/api/v1/settings/institutions \
  -H 'Content-Type: application/json' \
  -H 'X-Trader-Id: trader-a' \
  -d '{"displayName":"HSBC"}'
```

Expect `201` with generated `institutionCode` (e.g. `HSBC-01`).

## 2. List active institutions (execute picker)

```bash
curl -s 'http://localhost:8080/api/v1/settings/institutions?activeOnly=true' \
  -H 'X-Trader-Id: trader-a'
```

## 3. Execute an assigned order

Execute requires `institutionCode` (not free-text counterparty):

```bash
curl -s -X POST "http://localhost:8080/api/v1/orders/{orderId}/execute" \
  -H 'Content-Type: application/json' \
  -H 'X-Trader-Id: trader-a' \
  -d '{"executedRate":3.5,"institutionCode":"HSBC-01"}'
```

Response `counterparty` reflects the institution `displayName`.

## Cold start

With an empty institution catalog, execute is rejected until at least one institution is onboarded. Currency intake may still work when the currency catalog is configured.
