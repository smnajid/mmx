package com.mmx.order.adapter.out.persistence.mapper;

import com.mmx.order.adapter.out.persistence.entity.ManagedCurrencyEntity;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class ManagedCurrencyPersistenceMapper {

    public ManagedCurrency toDomain(ManagedCurrencyEntity entity) {
        return new ManagedCurrency(
                entity.getCode(),
                entity.isActive(),
                entity.getMinSubscriptionAmount(),
                entity.getMinIncreaseDecreaseAmount(),
                enabledTenors(entity),
                enabledNotices(entity));
    }

    public ManagedCurrencyEntity toEntity(ManagedCurrency currency) {
        ManagedCurrencyEntity entity = new ManagedCurrencyEntity();
        entity.setCode(currency.getCode());
        entity.setActive(currency.isActive());
        entity.setMinSubscriptionAmount(currency.getMinSubscriptionAmount());
        entity.setMinIncreaseDecreaseAmount(currency.getMinIncreaseDecreaseAmount());
        applyTenors(entity, currency.getEnabledTenors());
        applyNotices(entity, currency.getEnabledNoticePeriods());
        return entity;
    }

    private static Set<Tenor> enabledTenors(ManagedCurrencyEntity entity) {
        EnumSet<Tenor> set = EnumSet.noneOf(Tenor.class);
        if (entity.isTenor1w()) set.add(Tenor._1W);
        if (entity.isTenor2w()) set.add(Tenor._2W);
        if (entity.isTenor1m()) set.add(Tenor._1M);
        if (entity.isTenor3m()) set.add(Tenor._3M);
        if (entity.isTenor6m()) set.add(Tenor._6M);
        if (entity.isTenor1y()) set.add(Tenor._1Y);
        return set;
    }

    private static Set<NoticePeriod> enabledNotices(ManagedCurrencyEntity entity) {
        EnumSet<NoticePeriod> set = EnumSet.noneOf(NoticePeriod.class);
        if (entity.isNotice24h()) set.add(NoticePeriod._24H);
        if (entity.isNotice48h()) set.add(NoticePeriod._48H);
        return set;
    }

    private static void applyTenors(ManagedCurrencyEntity entity, Set<Tenor> tenors) {
        entity.setTenor1w(tenors.contains(Tenor._1W));
        entity.setTenor2w(tenors.contains(Tenor._2W));
        entity.setTenor1m(tenors.contains(Tenor._1M));
        entity.setTenor3m(tenors.contains(Tenor._3M));
        entity.setTenor6m(tenors.contains(Tenor._6M));
        entity.setTenor1y(tenors.contains(Tenor._1Y));
    }

    private static void applyNotices(ManagedCurrencyEntity entity, Set<NoticePeriod> notices) {
        entity.setNotice24h(notices.contains(NoticePeriod._24H));
        entity.setNotice48h(notices.contains(NoticePeriod._48H));
    }
}
