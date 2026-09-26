package com.mmx.order.domain.exception;

import com.mmx.order.domain.model.OnCallCurveKey;

public class OnCallPendingExistsException extends RuntimeException {

    public OnCallPendingExistsException(OnCallCurveKey curveKey) {
        super(
                "OnCall curve point already has a pending segment: "
                        + curveKey.institutionCode()
                        + "/"
                        + curveKey.currency()
                        + "/"
                        + curveKey.noticePeriod().getCode());
    }
}
