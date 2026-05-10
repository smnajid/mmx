package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.MoneyMarketOrder;

public interface BackOfficeGateway {

    /** Notify back-office after execution so accounting can proceed without re-keying. */
    void notifyExecution(MoneyMarketOrder order);
}
