package com.mmx.order.application.ordercreation;

import com.mmx.order.domain.model.OrderOperation;

import java.math.BigDecimal;

public record OrderCreationOperation(OrderOperation operation, BigDecimal minAmount) {}
