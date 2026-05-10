package com.mmx.order.adapter.out.integration;

import com.mmx.order.application.port.out.BackOfficeGateway;
import com.mmx.order.domain.model.MoneyMarketOrder;

public class NoOpBackOfficeGateway implements BackOfficeGateway {

    @Override
    public void notifyExecution(MoneyMarketOrder order) {
        // POC: no-op outbound call — replace with HTTP adapter when wiring a real back-office system
    }
}
