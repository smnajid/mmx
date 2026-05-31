# oncall-rate-curve-management Specification

## Purpose
TBD - created by archiving change oncall-rate-curve-handoff. Update Purpose after archive.
## Requirements
### Requirement: Curve point identity and segment model

An OnCall rate curve point SHALL be identified by the composite key **`(institution, currency, noticePeriod)`** (O-01). Each curve point holds an ordered set of **rate segments**. A segment SHALL carry a stable **`segmentId`** (UUID), a `rate`, an **inclusive** `valueDate` (segment start), an **inclusive** end date, and a lifecycle `status`. The currently open segment SHALL have end date equal to the sentinel **`2999-12-31`** (no-end). Segments for a curve point SHALL be contiguous: a segment ends the day before the next segment starts (O-02).

#### Scenario: Open segment carries the no-end sentinel

- **WHEN** a curve point has a single valid segment with no successor
- **THEN** that segment's end date is `2999-12-31`

#### Scenario: Segment is addressable by segmentId

- **WHEN** a segment is created for a curve point
- **THEN** it is assigned a unique `segmentId` that identifies it for cancellation and back-office confirmation

---

### Requirement: Adding a rate supersedes the prior segment provisionally

A trader SHALL add a new rate to a curve point by supplying a `rate` and a `valueDate` where **`valueDate ≥ today`** (no backdating). The new segment SHALL be created in status **`PENDING_CONFIRMATION`** with end date `2999-12-31`. If a prior open segment exists, its end date SHALL be set to **`valueDate − 1`** at the moment the rate is added (provisional supersede, Option A — O-02). If no prior segment exists for the curve point, the new segment is the first segment and there is nothing to supersede.

#### Scenario: Adding a rate end-dates the prior open segment

- **WHEN** a trader adds a rate with `valueDate = V` to a curve point whose open segment ends at `2999-12-31`
- **THEN** the prior segment's end date becomes `V − 1` and a new `PENDING_CONFIRMATION` segment is created spanning `[V, 2999-12-31]`

#### Scenario: First-ever segment has nothing to supersede

- **WHEN** a trader adds the first rate to a curve point that has no segments
- **THEN** a `PENDING_CONFIRMATION` segment is created spanning `[valueDate, 2999-12-31]` and no prior segment is modified

#### Scenario: Backdated value date is rejected

- **WHEN** a trader submits a rate whose `valueDate` is before today
- **THEN** the system rejects the request and no segment is created or modified

---

### Requirement: At most one pending segment per curve point

A curve point SHALL have **at most one** segment in status `PENDING_CONFIRMATION` at any time. While a pending segment exists for a curve point, the system SHALL reject a request to add another rate to that same curve point.

#### Scenario: Second add is blocked while one is pending

- **WHEN** a trader adds a rate to a curve point that already has a `PENDING_CONFIRMATION` segment
- **THEN** the system rejects the request and the existing pending segment is unchanged

#### Scenario: Add is allowed again after the pending segment resolves

- **WHEN** a curve point's pending segment has become `VALID` or `CANCELED`
- **THEN** a trader may add a new rate to that curve point

---

### Requirement: A pending rate prices new orders immediately

A segment in status `PENDING_CONFIRMATION` SHALL be **active for pricing new orders** whose value date falls within the segment's date range. The `PENDING_CONFIRMATION` status SHALL signal only that the **back office has not yet refreshed in-life contracts** for that rate; it SHALL NOT prevent the rate from applying to new orders.

#### Scenario: New order is priced on the pending rate

- **WHEN** a new order's value date falls within a `PENDING_CONFIRMATION` segment's range
- **THEN** the order is priced using that segment's rate

---

### Requirement: Trader can cancel a rate only before back-office confirmation

A trader SHALL be able to cancel a segment **only** while it is in status `PENDING_CONFIRMATION`. Cancelling SHALL set the segment status to **`CANCELED`** and restore the prior segment's end date to `2999-12-31` (no-end); if the canceled segment was a first-ever segment with no prior, the canceled segment leaves the curve point with no open segment. Cancelling a segment that is already `VALID` SHALL be rejected.

#### Scenario: Cancel reverts the prior segment to no-end

- **WHEN** a trader cancels a `PENDING_CONFIRMATION` segment whose prior segment was end-dated to `valueDate − 1`
- **THEN** the canceled segment's status becomes `CANCELED` and the prior segment's end date returns to `2999-12-31`

#### Scenario: Cancel of a confirmed rate is rejected

- **WHEN** a trader attempts to cancel a segment in status `VALID`
- **THEN** the system rejects the request and the segment remains `VALID`

#### Scenario: Cancelling a first-ever pending segment leaves no open segment

- **WHEN** a trader cancels a `PENDING_CONFIRMATION` first-ever segment that has no prior segment
- **THEN** the segment's status becomes `CANCELED` and the curve point has no open segment

---

### Requirement: Back-office confirmation is an atomic, idempotent compare-and-set

The system SHALL accept an inbound back-office **confirmation** addressed by `segmentId` and resolve it as a single atomic state transition within one transaction: a segment in `PENDING_CONFIRMATION` SHALL transition to **`VALID`** (recording mmx's own server-side `validatedAt`); a `CANCELED` segment SHALL NOT transition and SHALL be reported as a conflict; an unknown `segmentId` SHALL be rejected as not found. The back office **cannot reject** — confirmation is the only inbound outcome, so a pending segment resolves only to `VALID` (by confirmation) or `CANCELED` (by trader). Confirmation SHALL be **idempotent**: a repeated confirmation for an already-`VALID` segment is a successful no-op that does not change `validatedAt`. The back office SHALL treat a successful confirmation as the precondition for impacting in-life contracts (confirm-first, impact-second), and SHALL retry on transport failure given the idempotency guarantee.

#### Scenario: Confirmation transitions pending to valid

- **WHEN** the back office confirms a segment in `PENDING_CONFIRMATION`
- **THEN** the segment status becomes `VALID`, `validatedAt` is set to mmx's server-side timestamp, and the response indicates success

#### Scenario: Confirmation of a canceled segment is a conflict

- **WHEN** the back office confirms a segment that is already `CANCELED`
- **THEN** the segment remains `CANCELED`, is not transitioned to `VALID`, and the response indicates a conflict

#### Scenario: Duplicate confirmation is an idempotent no-op

- **WHEN** the back office confirms a segment that is already `VALID`
- **THEN** the response indicates success and the segment's `validatedAt` is unchanged

#### Scenario: Confirmation of an unknown segment is rejected

- **WHEN** a confirmation arrives for a `segmentId` that does not exist
- **THEN** the system reports not found and no segment state changes

#### Scenario: Confirmation is deferred, not lost, while mmx is unavailable

- **WHEN** the back office cannot reach mmx to confirm a `PENDING_CONFIRMATION` segment
- **THEN** the segment remains `PENDING_CONFIRMATION`, continues to price new orders, and the confirmation succeeds on a later retry once mmx is available

