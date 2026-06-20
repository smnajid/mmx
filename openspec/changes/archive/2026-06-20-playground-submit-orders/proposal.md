## Why

The widget playground at `/dev/widget-playground` completes the wizard and logs `orderReady` payloads to an event panel, but never calls mmx intake (`POST /api/v1/orders`). Developers cannot close the loop from widget → received queue without curl or the seed script. The widget is intentionally submission-agnostic (the PM host submits); the playground is the dev stand-in for that host and should exercise the real intake contract.

## What Changes

- **Playground order submission:** each logged `orderReady` event gets a **Send to mmx** action that maps `OrderCreationPayload` to `ReceiveOrderRequest`, generates an `externalOrderReference`, and `POST`s to `/api/v1/orders` using the same API base URL as the widget configuration.
- **Payload mapping helper:** map widget fields to intake (`operation` → `orderOperation`, include `institutionCode`, omit display-only `counterparty`).
- **Submit result UI:** show success (`orderId`, HTTP status) or validation/server errors in the event log; disable re-send after success (idempotent retry only when the user explicitly generates a new reference).
- **Frontend model alignment:** update `ReceiveOrderRequest` in `frontend/src/app/core/models/order.model.ts` to match canonical OpenAPI (`institutionCode` required; `desiredCounterpartyComment` removed).
- **Widget unchanged:** the embeddable library still emits `orderReady` only; submission stays host/playground responsibility per existing widget design.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- **`pm-order-creation-widget`**: extend the playground requirement so dev hosts can submit `orderReady` payloads to mmx intake and see the result.

## Impact

| Area | Notes |
|------|-------|
| `frontend/src/app/features/widget-playground/` | Send action, mapping, submit state in event log, HTTP via configured API base |
| `frontend/src/app/core/models/order.model.ts` | Align `ReceiveOrderRequest` with `specs/002-trader-orders-views/contracts/openapi.yaml` |
| `openspec/specs/pm-order-creation-widget/spec.md` | Delta merged on archive — playground submit scenarios |
| Backend / OpenAPI | **No changes** — consumes existing `POST /api/v1/orders` |

## Out of Scope

- Auto-submit on every `orderReady` (manual Send per event only).
- Widget library submitting orders (violates host boundary).
- Trader workflow actions from the playground (assign, execute).
- Production exposure of intake or the playground route.
- Replacing or extending `scripts/seed-demo-orders.sh`.
