package com.mmx.order.application.service;

import com.mmx.order.application.port.in.GetOrderDetailsUseCase;
import com.mmx.order.application.port.in.ListReceivedOnCallOrdersUseCase;
import com.mmx.order.application.port.in.ListReceivedTermOrdersUseCase;
import com.mmx.order.application.port.in.OrderPage;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class OrderQueryService implements ListReceivedTermOrdersUseCase, ListReceivedOnCallOrdersUseCase,
        GetOrderDetailsUseCase {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderPage listReceivedTermOrders(int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByStatusAndOrderType(OrderStatus.RECEIVED, OrderType.TERM);
        return paginate(all, page, size);
    }

    @Override
    public OrderPage listReceivedOnCallOrders(int page, int size) {
        List<MoneyMarketOrder> all =
                orderRepository.findByStatusAndOrderType(OrderStatus.RECEIVED, OrderType.ON_CALL);
        return paginate(all, page, size);
    }

    @Override
    public Optional<MoneyMarketOrder> getOrderDetails(UUID orderId) {
        return orderRepository.findById(orderId);
    }

    private static OrderPage paginate(List<MoneyMarketOrder> all, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        long totalElements = all.size();
        int fromIndex = page * size;
        if (fromIndex >= totalElements) {
            return new OrderPage(List.of(), totalElements, page, size);
        }
        int toIndex = Math.min(fromIndex + size, (int) totalElements);
        return new OrderPage(all.subList(fromIndex, toIndex), totalElements, page, size);
    }
}
