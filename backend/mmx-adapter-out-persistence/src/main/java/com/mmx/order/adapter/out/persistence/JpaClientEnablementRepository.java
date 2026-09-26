package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.entity.ClientEnablementEntity;
import com.mmx.order.adapter.out.persistence.entity.ClientEnablementId;
import com.mmx.order.adapter.out.persistence.repository.SpringDataClientEnablementRepository;
import com.mmx.order.application.port.out.ClientEnablementRepository;
import com.mmx.order.domain.model.ClientEnablement;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class JpaClientEnablementRepository implements ClientEnablementRepository {

    private final SpringDataClientEnablementRepository springDataRepository;

    public JpaClientEnablementRepository(SpringDataClientEnablementRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public ClientEnablement find(String institutionCode, String currency) {
        return springDataRepository
                .findById(new ClientEnablementId(institutionCode, currency))
                .map(JpaClientEnablementRepository::toDomain)
                .orElseGet(() -> ClientEnablement.empty(institutionCode, currency));
    }

    @Override
    public List<ClientEnablement> findByInstitutionCode(String institutionCode) {
        return springDataRepository.findByIdInstitutionCodeOrderByIdCurrencyAsc(institutionCode).stream()
                .map(JpaClientEnablementRepository::toDomain)
                .toList();
    }

    @Override
    public void save(LegalEntityCode owningLegalEntityCode, ClientEnablement enablement) {
        Instant now = Instant.now();
        ClientEnablementId id = new ClientEnablementId(enablement.institutionCode(), enablement.currency());
        ClientEnablementEntity entity =
                springDataRepository.findById(id).orElseGet(() -> {
                    ClientEnablementEntity created = new ClientEnablementEntity();
                    created.setId(id);
                    created.setCreatedAt(now);
                    return created;
                });
        entity.setLegalEntityCode(owningLegalEntityCode.value());
        Set<Tenor> tenors = enablement.tenors();
        entity.setTenor1w(tenors.contains(Tenor._1W));
        entity.setTenor2w(tenors.contains(Tenor._2W));
        entity.setTenor1m(tenors.contains(Tenor._1M));
        entity.setTenor3m(tenors.contains(Tenor._3M));
        entity.setTenor6m(tenors.contains(Tenor._6M));
        entity.setTenor1y(tenors.contains(Tenor._1Y));
        entity.setNotice24h(enablement.noticePeriods().contains(NoticePeriod._24H));
        entity.setNotice48h(enablement.noticePeriods().contains(NoticePeriod._48H));
        entity.setUpdatedAt(now);
        springDataRepository.save(entity);
    }

    private static ClientEnablement toDomain(ClientEnablementEntity entity) {
        EnumSet<Tenor> tenors = EnumSet.noneOf(Tenor.class);
        if (entity.isTenor1w()) tenors.add(Tenor._1W);
        if (entity.isTenor2w()) tenors.add(Tenor._2W);
        if (entity.isTenor1m()) tenors.add(Tenor._1M);
        if (entity.isTenor3m()) tenors.add(Tenor._3M);
        if (entity.isTenor6m()) tenors.add(Tenor._6M);
        if (entity.isTenor1y()) tenors.add(Tenor._1Y);
        EnumSet<NoticePeriod> notices = EnumSet.noneOf(NoticePeriod.class);
        if (entity.isNotice24h()) notices.add(NoticePeriod._24H);
        if (entity.isNotice48h()) notices.add(NoticePeriod._48H);
        return new ClientEnablement(entity.getId().getInstitutionCode(), entity.getId().getCurrency(), tenors, notices);
    }
}
