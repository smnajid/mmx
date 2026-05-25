# Managed Currency Settings API v1

Canonical OpenAPI: [openapi.yaml](./openapi.yaml). All trader operations require header `X-Trader-Id`.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/settings/currencies` | List catalog (may be empty) |
| POST | `/api/v1/settings/currencies` | Onboard currency |
| GET | `/api/v1/settings/currencies/{code}` | Get by ISO code |
| PATCH | `/api/v1/settings/currencies/{code}` | Update rules |
| POST | `/api/v1/settings/currencies/{code}/disable` | Deactivate |

## ManagedCurrencyResponse

- `code`, `active`, `minSubscriptionAmount`, `minIncreaseDecreaseAmount`
- `enabledTenors`: subset of `1W`, `2W`, `1M`, `3M`, `6M`, `1Y`
- `enabledNoticePeriods`: subset of `24H`, `48H`

At least one tenor and one notice period MUST remain enabled on update.
