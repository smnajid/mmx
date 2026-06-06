package com.mmx.order.application.port.in;

import com.mmx.order.application.ordercreation.CounterpartiesResult;
import com.mmx.order.domain.model.NoticePeriod;

import java.time.LocalDate;

public interface ListOnCallCounterpartiesUseCase {

    CounterpartiesResult listCounterparties(
            String currency, NoticePeriod noticePeriod, LocalDate valueDate);
}
