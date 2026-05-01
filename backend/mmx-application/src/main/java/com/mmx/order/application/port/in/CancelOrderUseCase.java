package com.mmx.order.application.port.in;

import com.mmx.order.application.command.CancelOrderCommand;
import com.mmx.order.domain.model.MoneyMarketOrder;

public interface CancelOrderUseCase {

    MoneyMarketOrder cancel(CancelOrderCommand command);
}
