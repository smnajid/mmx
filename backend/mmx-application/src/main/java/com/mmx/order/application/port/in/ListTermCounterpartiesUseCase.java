package com.mmx.order.application.port.in;

import com.mmx.order.application.ordercreation.CounterpartiesResult;
import com.mmx.order.domain.model.Tenor;

public interface ListTermCounterpartiesUseCase {

    CounterpartiesResult listCounterparties(String currency, Tenor tenor);
}
