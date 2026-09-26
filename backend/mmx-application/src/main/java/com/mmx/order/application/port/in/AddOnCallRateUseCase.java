package com.mmx.order.application.port.in;

import com.mmx.order.application.command.AddOnCallRateCommand;
import com.mmx.order.domain.model.OnCallRateSegment;

public interface AddOnCallRateUseCase {

    OnCallRateSegment add(AddOnCallRateCommand command);
}
