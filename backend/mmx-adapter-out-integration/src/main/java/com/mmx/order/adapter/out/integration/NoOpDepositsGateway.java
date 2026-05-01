package com.mmx.order.adapter.out.integration;

import com.mmx.order.application.port.out.DepositsGateway;
import com.mmx.order.domain.model.MoneyMarketOrder;

public class NoOpDepositsGateway implements DepositsGateway {

    @Override
    public void notifyExecution(MoneyMarketOrder order) {
        // V1: no-op — downstream Deposits system integration is out of scope
    }
}
