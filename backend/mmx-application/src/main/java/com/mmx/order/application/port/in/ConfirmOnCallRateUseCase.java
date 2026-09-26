package com.mmx.order.application.port.in;

import java.util.UUID;

public interface ConfirmOnCallRateUseCase {

  enum Outcome {
    CONFIRMED,
    ALREADY_VALID
  }

  Outcome confirm(UUID segmentId);
}
