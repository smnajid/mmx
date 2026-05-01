package com.mmx.order.application.port.in;

import com.mmx.order.application.command.RejectOrderCommand;
import com.mmx.order.domain.model.MoneyMarketOrder;

public interface RejectOrderUseCase {

    MoneyMarketOrder reject(RejectOrderCommand command);
}
