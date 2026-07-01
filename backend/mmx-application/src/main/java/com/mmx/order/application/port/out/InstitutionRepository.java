package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.List;
import java.util.Optional;

public interface InstitutionRepository {

    List<Institution> findAll();

    List<Institution> findNativeByLegalEntityCode(LegalEntityCode legalEntityCode);

    List<Institution> findActive();

    Optional<Institution> findByInstitutionCode(String institutionCode);

    boolean existsAny();

    int maxSuffixForAcronym(String acronymBase);

    Institution save(Institution institution);
}
