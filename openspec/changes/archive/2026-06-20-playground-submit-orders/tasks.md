## 1. Frontend intake model alignment

- [x] 1.1 Update `frontend/src/app/core/models/order.model.ts` `ReceiveOrderRequest`: add required `institutionCode`, remove `desiredCounterpartyComment`; align optional fields with `specs/002-trader-orders-views/contracts/openapi.yaml`.
- [x] 1.2 Fix any frontend references/mocks that still use `desiredCounterpartyComment` on intake requests.

## 2. Payload mapper (red-first)

- [x] 2.1 **frontend** — Write failing `order-creation-payload.mapper.spec.ts`: `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm test -- --include='**/order-creation-payload.mapper.spec.ts' --no-watch` — asserts `operation` → `orderOperation`, `institutionCode` preserved, `counterparty` omitted, conditional `tenor` / `noticePeriod` / `sourceContractNumber` / `minimumRate`.
- [x] 2.2 **frontend** — Add `order-creation-payload.mapper.ts` with `mapOrderCreationPayloadToReceiveRequest(payload, externalOrderReference)` and `generatePlaygroundExternalReference()` until green.

## 3. Playground submit UI (red-first)

- [x] 3.1 **frontend** — Write failing playground spec cases: Send posts to `{apiBase}/api/v1/orders` with mapped body; 201 shows `orderId`; 400 shows error and allows retry. `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm test -- --include='**/widget-playground.component.spec.ts' --no-watch`
- [x] 3.2 **frontend** — Extend `PlaygroundEvent` with submit state; add Send button, loading, success, and error UI in event log; implement `submitOrder(eventIndex)` using `HttpClient` and `activeApiBaseUrl()` until green.

## 4. Final verification

- [x] 4.1 Run `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm run test` — green.
