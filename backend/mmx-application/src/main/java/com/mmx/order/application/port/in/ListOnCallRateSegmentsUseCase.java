package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.OnCallRateSegment;

import java.util.List;

public interface ListOnCallRateSegmentsUseCase {

    List<OnCallRateSegment> listByInstitution(String institutionCode);
}
