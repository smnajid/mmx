package com.mmx.order.application.port.in;

import com.mmx.order.application.ordercreation.TenorsResult;
import com.mmx.order.domain.model.LegalEntityCode;

public interface ListTermTenorsUseCase {

    TenorsResult listTenors(LegalEntityCode legalEntityCode, String currency);
}
