package com.mmx.order.application.command;

import com.mmx.order.domain.model.TraderId;

import java.util.UUID;

public record AssignOrderCommand(UUID orderId, TraderId traderId) {}
