package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.List;
import java.util.Optional;

/** The institutions stored in this deployment: TradingHub native ones and TradingClient onboarded ones. */
public interface InstitutionRepository {

    List<Institution> findAll();

    List<Institution> findNativeByLegalEntityCode(LegalEntityCode legalEntityCode);

    List<Institution> findActive();

    Optional<Institution> findByInstitutionCode(String institutionCode);

    boolean existsAny();

    int maxSuffixForAcronym(String acronymBase);

    Institution save(Institution institution);

    /** A TradingClient's onboarded institutions, including offboarded ones. */
    default List<Institution> findOnboardedByLegalEntityCode(LegalEntityCode legalEntityCode) {
        return findAll().stream()
                .filter(Institution::isOnboarded)
                .filter(i -> legalEntityCode.equals(i.getOwningLegalEntityCode()))
                .toList();
    }

    /** The client's single onboarded institution linked to a hub institution, if any. */
    default Optional<Institution> findOnboarded(LegalEntityCode legalEntityCode, HubInstitutionLink hubLink) {
        return findOnboardedByLegalEntityCode(legalEntityCode).stream()
                .filter(i -> i.getHubLink().filter(hubLink::equals).isPresent())
                .findFirst();
    }
}
