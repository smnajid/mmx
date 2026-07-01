package com.mmx.order.adapter.out.persistence.mapper;

import com.mmx.order.adapter.out.persistence.entity.DelegatedInstitutionGrantEntity;
import com.mmx.order.adapter.out.persistence.entity.DelegatedInstitutionGrantId;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Component
public class DelegatedGrantPersistenceMapper {

    public DelegatedInstitutionGrant toDomain(DelegatedInstitutionGrantEntity entity) {
        DelegatedInstitutionGrantId id = entity.getId();
        return new DelegatedInstitutionGrant(
                id.getHubInstitutionCode(),
                new LegalEntityCode(id.getClientLegalEntityCode()),
                id.getCurrency(),
                enabledTenors(entity),
                enabledNotices(entity),
                entity.isActive());
    }

    public DelegatedInstitutionGrantEntity toEntity(DelegatedInstitutionGrant grant, Instant now) {
        DelegatedInstitutionGrantEntity entity = new DelegatedInstitutionGrantEntity();
        entity.setId(
                new DelegatedInstitutionGrantId(
                        grant.getHubInstitutionCode(),
                        grant.getClientLegalEntityCode().value(),
                        grant.getCurrency()));
        entity.setActive(grant.isActive());
        applyTenors(entity, grant.getEnabledTenors());
        applyNotices(entity, grant.getEnabledNoticePeriods());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    public void updateEntity(DelegatedInstitutionGrantEntity entity, DelegatedInstitutionGrant grant, Instant now) {
        entity.setActive(grant.isActive());
        applyTenors(entity, grant.getEnabledTenors());
        applyNotices(entity, grant.getEnabledNoticePeriods());
        entity.setUpdatedAt(now);
    }

    public DelegatedGrantKey toKey(DelegatedInstitutionGrantId id) {
        return new DelegatedGrantKey(
                id.getHubInstitutionCode(), new LegalEntityCode(id.getClientLegalEntityCode()), id.getCurrency());
    }

    public DelegatedInstitutionGrantId toId(DelegatedGrantKey key) {
        return new DelegatedInstitutionGrantId(
                key.hubInstitutionCode(), key.clientLegalEntityCode().value(), key.currency());
    }

    private static Set<Tenor> enabledTenors(DelegatedInstitutionGrantEntity entity) {
        EnumSet<Tenor> set = EnumSet.noneOf(Tenor.class);
        if (entity.isTenor1w()) set.add(Tenor._1W);
        if (entity.isTenor2w()) set.add(Tenor._2W);
        if (entity.isTenor1m()) set.add(Tenor._1M);
        if (entity.isTenor3m()) set.add(Tenor._3M);
        if (entity.isTenor6m()) set.add(Tenor._6M);
        if (entity.isTenor1y()) set.add(Tenor._1Y);
        return set;
    }

    private static Set<NoticePeriod> enabledNotices(DelegatedInstitutionGrantEntity entity) {
        EnumSet<NoticePeriod> set = EnumSet.noneOf(NoticePeriod.class);
        if (entity.isNotice24h()) set.add(NoticePeriod._24H);
        if (entity.isNotice48h()) set.add(NoticePeriod._48H);
        return set;
    }

    private static void applyTenors(DelegatedInstitutionGrantEntity entity, Set<Tenor> tenors) {
        entity.setTenor1w(tenors.contains(Tenor._1W));
        entity.setTenor2w(tenors.contains(Tenor._2W));
        entity.setTenor1m(tenors.contains(Tenor._1M));
        entity.setTenor3m(tenors.contains(Tenor._3M));
        entity.setTenor6m(tenors.contains(Tenor._6M));
        entity.setTenor1y(tenors.contains(Tenor._1Y));
    }

    private static void applyNotices(DelegatedInstitutionGrantEntity entity, Set<NoticePeriod> notices) {
        entity.setNotice24h(notices.contains(NoticePeriod._24H));
        entity.setNotice48h(notices.contains(NoticePeriod._48H));
    }
}
