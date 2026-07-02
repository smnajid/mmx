package com.mmx.order.config;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.RouteOrderUseCase;
import com.mmx.order.application.service.RouteOrderService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class TransactionalRouteOrderUseCase implements RouteOrderUseCase {

    private final RouteOrderService delegate;

    public TransactionalRouteOrderUseCase(RouteOrderService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public Result route(ReceiveOrderCommand command) {
        return delegate.route(command);
    }
}
