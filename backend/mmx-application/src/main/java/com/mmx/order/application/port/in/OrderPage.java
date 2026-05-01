package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.MoneyMarketOrder;

import java.util.List;

/** Zero-based page of orders aligned with REST list endpoints. */
public record OrderPage(List<MoneyMarketOrder> content, long totalElements, int page, int size) {}
