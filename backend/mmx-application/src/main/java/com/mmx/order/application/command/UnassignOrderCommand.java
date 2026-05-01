package com.mmx.order.application.command;

import com.mmx.order.domain.model.TraderId;

import java.util.UUID;

public record UnassignOrderCommand(UUID orderId, TraderId traderId) {}
