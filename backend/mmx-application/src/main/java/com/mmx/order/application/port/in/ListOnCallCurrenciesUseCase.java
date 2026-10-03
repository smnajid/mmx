package com.mmx.order.application.port.in;

import com.mmx.order.application.ordercreation.OnCallCurrenciesResult;
import com.mmx.order.domain.model.LegalEntityCode;

public interface ListOnCallCurrenciesUseCase {

    OnCallCurrenciesResult listCurrencies(LegalEntityCode legalEntityCode);
}
