package com.mmx.order.application.port.in;

import com.mmx.order.application.command.CancelOnCallRateCommand;
import com.mmx.order.domain.model.OnCallRateSegment;

public interface CancelOnCallRateUseCase {

    OnCallRateSegment cancel(CancelOnCallRateCommand command);
}
