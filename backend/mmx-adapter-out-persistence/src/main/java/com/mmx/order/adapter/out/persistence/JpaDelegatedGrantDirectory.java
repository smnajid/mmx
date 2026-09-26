package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.entity.DelegatedInstitutionGrantEntity;
import com.mmx.order.adapter.out.persistence.mapper.DelegatedGrantPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataDelegatedGrantRepository;
import com.mmx.order.application.port.out.DelegatedGrantDirectory;
import com.mmx.order.application.port.out.GrantResolution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;

import java.util.List;
import java.util.Set;

public class JpaDelegatedGrantDirectory implements DelegatedGrantDirectory {

    private final SpringDataDelegatedGrantRepository grantRepository;
    private final DelegatedGrantPersistenceMapper mapper;

    public JpaDelegatedGrantDirectory(
            SpringDataDelegatedGrantRepository grantRepository, DelegatedGrantPersistenceMapper mapper) {
        this.grantRepository = grantRepository;
        this.mapper = mapper;
    }

    @Override
    public GrantResolution resolveTenor(
            LegalEntityCode clientLegalEntityCode,
            String proxyInstitutionCode,
            String currency,
            Tenor tenor) {
        return resolve(clientLegalEntityCode, proxyInstitutionCode, currency, tenor, null);
    }

    @Override
    public GrantResolution resolveNotice(
            LegalEntityCode clientLegalEntityCode,
            String proxyInstitutionCode,
            String currency,
            NoticePeriod noticePeriod) {
        return resolve(clientLegalEntityCode, proxyInstitutionCode, currency, null, noticePeriod);
    }

    private GrantResolution resolve(
            LegalEntityCode clientLegalEntityCode,
            String proxyInstitutionCode,
            String currency,
            Tenor tenor,
            NoticePeriod notice) {
        List<DelegatedInstitutionGrantEntity> grants =
                grantRepository.findActiveGrantForProxy(
                        clientLegalEntityCode.value(), proxyInstitutionCode, currency);
        if (grants.isEmpty()) {
            return GrantResolution.NO_ACTIVE_GRANT;
        }
        DelegatedInstitutionGrantEntity grant = grants.getFirst();
        Set<Tenor> tenors = enabledTenors(grant);
        Set<NoticePeriod> notices = enabledNotices(grant);
        if (tenor != null) {
            return tenors.contains(tenor) ? GrantResolution.GRANTED : GrantResolution.NOT_IN_ENABLED_SET;
        }
        return notices.contains(notice) ? GrantResolution.GRANTED : GrantResolution.NOT_IN_ENABLED_SET;
    }

    private Set<Tenor> enabledTenors(DelegatedInstitutionGrantEntity entity) {
        return mapper.toDomain(entity).getEnabledTenors();
    }

    private Set<NoticePeriod> enabledNotices(DelegatedInstitutionGrantEntity entity) {
        return mapper.toDomain(entity).getEnabledNoticePeriods();
    }
}
