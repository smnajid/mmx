package com.mmx.order.adapter.out.persistence.mapper;

import com.mmx.order.adapter.out.persistence.entity.TermRateEntity;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.Tenor;

public final class TermRatePersistenceMapper {

    public TermRateAuditRow toDomain(TermRateEntity entity) {
        Tenor tenor =
                Tenor.fromCode(entity.getTenor())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Unknown tenor in persistence: " + entity.getTenor()));
        return new TermRateAuditRow(
                entity.getTradingDate(),
                entity.getInstitutionCode(),
                entity.getCurrency(),
                tenor,
                entity.getRate(),
                entity.getUploadedAt(),
                entity.getUploadedBy());
    }

    public TermRateEntity toEntity(TermRateAuditRow row) {
        TermRateEntity entity = new TermRateEntity();
        entity.setTradingDate(row.tradingDate());
        entity.setInstitutionCode(row.institutionCode());
        entity.setCurrency(row.currency());
        entity.setTenor(row.tenor().getCode());
        entity.setRate(row.rate());
        entity.setUploadedAt(row.uploadedAt());
        entity.setUploadedBy(row.uploadedBy());
        entity.setLegalEntityCode("LOC");
        return entity;
    }
}
