package com.mmx.order.application.port.in;

import java.time.LocalDate;
import java.util.List;

public interface ListTermRateTradingDaysUseCase {

    List<LocalDate> list();
}
