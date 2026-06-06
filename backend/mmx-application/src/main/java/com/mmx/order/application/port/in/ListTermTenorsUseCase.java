package com.mmx.order.application.port.in;

import com.mmx.order.application.ordercreation.TenorsResult;

public interface ListTermTenorsUseCase {

    TenorsResult listTenors(String currency);
}
