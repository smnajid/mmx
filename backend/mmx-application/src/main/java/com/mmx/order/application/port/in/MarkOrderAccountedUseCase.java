package com.mmx.order.application.port.in;

import java.util.UUID;

public interface MarkOrderAccountedUseCase {

    void markAccounted(UUID orderId);
}
