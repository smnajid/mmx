package com.mmx.order.application.command;

import java.util.UUID;

public record CancelOrderCommand(UUID orderId) {}
