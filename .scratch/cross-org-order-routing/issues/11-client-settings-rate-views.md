Status: needs-triage

# Client-side Settings rate views (term-rate trading days, on-call segments per client institution)

## Problem

Left out of scope by `openspec/changes/client-order-creation-options` (Non-Goals). On a
remote TradingClient deployment (CGEG), the Settings rate screens still show today's behaviour:

- **Term-rate Settings** reads trading days through `RemoteTermRateRepository.findDistinctTradingDatesDesc`,
  which returns an empty list. The hub exposes no cross-org read for the list of trading days.
- **On-call rate Settings** per client institution uses `RemoteOnCallRateRepository.findByInstitutionCode`,
  which returns empty: a client institution code (e.g. `SBVL-01`) never has hub segments, and the
  hub's segments are keyed by the hub-native institution code.

The order-creation wizard is fixed by that change; these two screens remain empty for a client.

## Done when

- A decision on whether a client Settings user should see hub rates at all, and for which institutions
  (the granted ones, mapped client institution code → hub institution code).
- If yes: cross-org reads (contract `007`) for trading days and per-institution segments, grant-scoped
  like the other rate reads; the remote adapters implement them; the Settings screens render them.
- `order-routing` / `term-rate-settings-ui` / `oncall-rate-settings-ui` specs updated in the same delivery.
