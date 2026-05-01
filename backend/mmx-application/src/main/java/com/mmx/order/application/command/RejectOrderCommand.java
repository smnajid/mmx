package com.mmx.order.application.command;

import java.util.UUID;

public record RejectOrderCommand(UUID orderId, String reason) {}
