# backend-hexagonal-architecture Specification

## Purpose

Define executable architecture guardrails for the Java backend modular monolith. Rules are enforced by ArchUnit tests and align with `docs/governance.md` Principle I (hexagonal / ports & adapters). This capability governs **structural** constraints only; product HTTP contracts and domain lifecycle rules remain in their respective specs.

## ADDED Requirements

### Requirement: Domain layer is framework-agnostic

Classes in `com.mmx.order.domain` and subpackages MUST NOT depend on Spring, JPA/Hibernate, Jackson, the application layer, or any adapter package.

#### Scenario: Domain has no Spring imports

- **WHEN** ArchUnit analyzes production classes under `com.mmx.order.domain..`
- **THEN** no dependency on `org.springframework..`, `jakarta.persistence..`, or `com.fasterxml.jackson..` is reported

#### Scenario: Domain does not depend outward

- **WHEN** ArchUnit analyzes production classes under `com.mmx.order.domain..`
- **THEN** no dependency on `com.mmx.order.application..` or `com.mmx.order.adapter..` is reported

### Requirement: Application layer is adapter-free

Classes in `com.mmx.order.application` and subpackages MUST NOT depend on Spring, JPA/Hibernate, or any adapter package.

#### Scenario: Application has no adapter imports

- **WHEN** ArchUnit analyzes production classes under `com.mmx.order.application..`
- **THEN** no dependency on `com.mmx.order.adapter..` is reported

#### Scenario: Application has no Spring imports

- **WHEN** ArchUnit analyzes production classes under `com.mmx.order.application..`
- **THEN** no dependency on `org.springframework..` or `jakarta.persistence..` is reported

### Requirement: Outbound adapters do not depend on inbound REST

Classes in `com.mmx.order.adapter.out..` MUST NOT depend on `com.mmx.order.adapter.in..`.

#### Scenario: Persistence does not import REST adapter

- **WHEN** ArchUnit analyzes production classes under `com.mmx.order.adapter.out..`
- **THEN** no dependency on `com.mmx.order.adapter.in..` is reported

### Requirement: JPA entities are confined to persistence adapter

Every class annotated with `@Entity` MUST reside in `com.mmx.order.adapter.out..entity..` (persistence and messaging outbound adapters).

#### Scenario: No entity outside outbound adapter entity packages

- **WHEN** ArchUnit collects all classes annotated with `@Entity` in `com.mmx.order`
- **THEN** each resides in `..adapter.out..entity..`

### Requirement: REST controllers use inbound ports not application services

Classes in `com.mmx.order.adapter.in.rest..` annotated with `@RestController` MUST NOT depend on classes in `com.mmx.order.application.service..`. They SHALL depend on `com.mmx.order.application.port.in..` interfaces (and commands, domain types, mappers, or generated REST types as needed).

#### Scenario: Assign and unassign use port interfaces

- **WHEN** `OrderManagementController` handles assign or unassign
- **THEN** it delegates through `AssignOrderUseCase` and `UnassignOrderUseCase` and does not reference `AssignmentService`

#### Scenario: No controller imports application services

- **WHEN** ArchUnit analyzes `@RestController` classes under `com.mmx.order.adapter.in.rest..` (excluding generated packages)
- **THEN** no dependency on `com.mmx.order.application.service..` is reported

### Requirement: REST exception handling does not depend on application services

Classes in `com.mmx.order.adapter.in.rest..` annotated with `@RestControllerAdvice` MUST NOT depend on `com.mmx.order.application.service..`. They SHALL handle exceptions from `com.mmx.order.domain.exception..`, `com.mmx.order.application.exception..`, and other allowed application packages (e.g. `application.termrate` ingest exceptions).

#### Scenario: Global exception handler uses extracted exception types

- **WHEN** `GlobalExceptionHandler` maps currency, institution, or contract not-found failures to HTTP responses
- **THEN** it references top-level types in `com.mmx.order.application.exception..` and not nested classes inside service implementations

#### Scenario: No RestControllerAdvice imports application services

- **WHEN** ArchUnit analyzes `@RestControllerAdvice` classes under `com.mmx.order.adapter.in.rest..`
- **THEN** no dependency on `com.mmx.order.application.service..` is reported

### Requirement: Layered dependency direction

Production code MUST respect hexagonal layer access: Domain is innermost; Application may access Domain; Adapters may access Application and Domain; Bootstrap (`com.mmx.order.config..`) may access all layers for wiring; inbound and outbound adapters MUST NOT access each other.

#### Scenario: Adapter in and adapter out are isolated

- **WHEN** ArchUnit applies layered architecture rules across `com.mmx.order`
- **THEN** no class in `adapter.in..` depends on `adapter.out..` and vice versa

#### Scenario: Application does not depend on adapters

- **WHEN** ArchUnit applies layered architecture rules
- **THEN** no class in `application..` depends on `adapter..`

### Requirement: Architecture tests run in CI

The backend MUST include automated ArchUnit tests that assert all requirements above. Domain-scoped rules MUST run from `mmx-domain`; full cross-module rules MUST run from `mmx-bootstrap`.

#### Scenario: Domain architecture test exists

- **WHEN** `mvn test -pl mmx-domain -Dtest=DomainArchitectureTest` is executed
- **THEN** domain isolation rules pass

#### Scenario: Full hexagonal architecture test exists

- **WHEN** `mvn test -pl mmx-bootstrap -Dtest=HexagonalArchitectureTest` is executed
- **THEN** all Tier 1 and Tier 2 architecture rules pass

#### Scenario: Full reactor includes architecture tests

- **WHEN** `cd backend && mvn test` completes successfully
- **THEN** both architecture test classes have passed
