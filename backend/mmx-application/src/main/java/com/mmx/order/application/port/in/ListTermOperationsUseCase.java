package com.mmx.order.application.port.in;

import com.mmx.order.application.ordercreation.OperationsResult;

public interface ListTermOperationsUseCase {

    OperationsResult listOperations(String currency);
}
