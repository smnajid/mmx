package com.mmx.order.application.command;

import java.util.UUID;

public record CancelOnCallRateCommand(String institutionCode, UUID segmentId) {}
