## 1. Maven setup (`backend/` parent)

- [x] 1.1 Add `com.tngtech.archunit:archunit-junit5:1.4.2` to `backend/pom.xml` `dependencyManagement`
- [x] 1.2 Declare `archunit-junit5` test dependency in `mmx-domain/pom.xml`
- [x] 1.3 Declare `archunit-junit5` test dependency in `mmx-bootstrap/pom.xml`
- [x] 1.4 Verify compile gate: `cd backend && mvn -pl mmx-domain,mmx-bootstrap -am compile`

## 2. Shared architecture rules (`mmx-domain` test sources)

- [x] 2.1 Create `com.mmx.order.architecture.ArchitectureRules` in `mmx-domain/src/test/java` with Tier 1 rules (domain/application framework isolation, adapter-out ↔ adapter-in ban, `@Entity` package confinement) and Tier 2 rules (strict port boundaries for `@RestController` / `@RestControllerAdvice`, layered architecture)
- [x] 2.2 Exclude `..adapter.in.rest.generated..` from caller-side rules per design D4/D5

## 3. Architecture tests — red first (`mmx-domain`, `mmx-bootstrap`)

- [x] 3.1 Write failing `DomainArchitectureTest` in `mmx-domain/src/test/java` importing `ArchitectureRules` domain-scoped checks — `cd backend && mvn test -pl mmx-domain -Dtest=DomainArchitectureTest`
- [x] 3.2 Write failing `HexagonalArchitectureTest` in `mmx-bootstrap/src/test/java` (tag `@Tag("architecture")`) importing full `ArchitectureRules` — `cd backend && mvn test -pl mmx-bootstrap -am -Dtest=HexagonalArchitectureTest` (expect failure on Tier 2 violations)

## 4. Refactor — assignment port boundaries (`adapter-in-rest`)

- [x] 4.1 Update `OrderManagementController` to inject `AssignOrderUseCase` and `UnassignOrderUseCase` instead of `AssignmentService`; remove `application.service` import

## 5. Refactor — extract application exceptions (`mmx-application`, `adapter-in-rest`)

- [x] 5.1 Create top-level `CurrencyNotFoundException`, `InstitutionNotFoundException`, and `ContractNotFoundException` in `com.mmx.order.application.exception`
- [x] 5.2 Update `ManageCurrencySettingsService`, `ManageInstitutionSettingsService`, and `OnCallOrderCreationOptionsService` to throw the new types; remove nested exception classes
- [x] 5.3 Update `GlobalExceptionHandler` to import from `application.exception`; remove `application.service` imports

## 6. Green architecture tests and regression on touched modules

- [x] 6.1 Implement any remaining rule adjustments until `HexagonalArchitectureTest` passes — `cd backend && mvn test -pl mmx-bootstrap -am -Dtest=HexagonalArchitectureTest`
- [x] 6.2 Confirm `DomainArchitectureTest` still passes — `cd backend && mvn test -pl mmx-domain -Dtest=DomainArchitectureTest`
- [x] 6.3 Run scoped regression on touched modules — `cd backend && mvn test -pl mmx-adapter-in-rest,mmx-application -am`

## 7. Documentation

- [x] 7.1 Add ArchUnit architecture test location and scoped Maven commands to `docs/agents/codebase-map.md` under Backend (hexagonal)

## Final verification

- [x] 8.1 Run full `cd backend && mvn test` — all modules green
