## ADDED Requirements

### Requirement: Contract type generation is verifiable via a single CI-ready command

The frontend SHALL provide a single command that regenerates all contract types from the canonical contracts and type-checks the project against them, exiting non-zero on any failure. This delivers guarantee that generated output is current and the project compiles against it (guarantee A). The command SHALL be runnable in CI and locally without running the unit-test suite.

#### Scenario: Verification passes on a clean tree

- **WHEN** `npm run verify:contracts` is run in `frontend/` against valid contracts and a project that compiles
- **THEN** generation completes and the type-check passes, and the command exits with status 0

#### Scenario: Verification fails on an invalid or missing contract

- **WHEN** `npm run verify:contracts` is run and a referenced contract file is missing or invalid
- **THEN** the generation step fails and the command exits non-zero

#### Scenario: Verification fails on a type mismatch against generated types

- **WHEN** the frontend source uses a contract type in a way that does not type-check against the freshly generated types
- **THEN** the type-check step fails and the command exits non-zero
