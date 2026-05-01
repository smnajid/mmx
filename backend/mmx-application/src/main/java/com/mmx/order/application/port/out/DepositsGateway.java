package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.MoneyMarketOrder;

public interface DepositsGateway {

    /** Notify the downstream Deposits system after execution. No-op in V1. */
    void notifyExecution(MoneyMarketOrder order);
}
