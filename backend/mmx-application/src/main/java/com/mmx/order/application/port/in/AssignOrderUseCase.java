package com.mmx.order.application.port.in;

import com.mmx.order.application.command.AssignOrderCommand;
import com.mmx.order.domain.model.MoneyMarketOrder;

public interface AssignOrderUseCase {

    MoneyMarketOrder assign(AssignOrderCommand command);
}
