package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.entity.InstitutionEntity;
import com.mmx.order.adapter.out.persistence.repository.SpringDataDelegatedGrantRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOnCallRateSegmentRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataTermRateRepository;

import java.util.List;

/** Deletes persistence test rows in FK-safe order after V20 grant constraints. */
public final class PersistenceTestCleanup {

    private PersistenceTestCleanup() {}

    public static void clearInstitutionsAndRates(
            SpringDataDelegatedGrantRepository grants,
            SpringDataOnCallRateSegmentRepository onCallSegments,
            SpringDataTermRateRepository termRates,
            SpringDataInstitutionRepository institutions) {
        grants.deleteAll();
        onCallSegments.deleteAll();
        termRates.deleteAll();
        deleteAllInstitutions(institutions);
    }

    public static void clearGrantsAndInstitutions(
            SpringDataDelegatedGrantRepository grants, SpringDataInstitutionRepository institutions) {
        grants.deleteAll();
        deleteAllInstitutions(institutions);
    }

    public static void clearInstitutionsAndOnCall(
            SpringDataDelegatedGrantRepository grants,
            SpringDataOnCallRateSegmentRepository onCallSegments,
            SpringDataInstitutionRepository institutions) {
        grants.deleteAll();
        onCallSegments.deleteAll();
        deleteAllInstitutions(institutions);
    }

    private static void deleteAllInstitutions(SpringDataInstitutionRepository institutions) {
        List<InstitutionEntity> proxies =
                institutions.findAll().stream()
                        .filter(i -> i.getHubInstitutionCode() != null)
                        .toList();
        if (!proxies.isEmpty()) {
            institutions.deleteAll(proxies);
            institutions.flush();
        }
        institutions.deleteAll();
    }
}
