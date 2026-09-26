package com.mmx.order.domain.exception;

import java.time.LocalDate;

public class OnCallBackdatedValueDateException extends RuntimeException {

    public OnCallBackdatedValueDateException(LocalDate valueDate, LocalDate today) {
        super("OnCall value date " + valueDate + " is before today " + today);
    }
}
