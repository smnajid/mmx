package com.mmx.order.adapter.out.persistence.mapper;

import com.mmx.order.adapter.out.persistence.entity.OnCallRateSegmentEntity;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OnCallRateSegmentStatus;
import org.springframework.stereotype.Component;

@Component
public class OnCallRateSegmentPersistenceMapper {

    public OnCallRateSegmentEntity toEntity(OnCallRateSegment segment) {
        return new OnCallRateSegmentEntity(
                segment.getSegmentId(),
                segment.getCurveKey().institutionCode(),
                segment.getCurveKey().currency(),
                segment.getCurveKey().noticePeriod().name(),
                segment.getRate(),
                segment.getValueDate(),
                segment.getEndDate(),
                segment.getStatus().name(),
                segment.getValidatedAt());
    }

    public OnCallRateSegment toDomain(OnCallRateSegmentEntity entity) {
        return new OnCallRateSegment(
                entity.getSegmentId(),
                new OnCallCurveKey(
                        entity.getInstitutionCode(),
                        entity.getCurrency(),
                        NoticePeriod.valueOf(entity.getNoticePeriod())),
                entity.getRate(),
                entity.getValueDate(),
                entity.getEndDate(),
                OnCallRateSegmentStatus.valueOf(entity.getStatus()),
                entity.getValidatedAt());
    }
}
