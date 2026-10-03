package com.mmx.order.application.port.in;

import com.mmx.order.application.ordercreation.TermCurrenciesResult;
import com.mmx.order.domain.model.LegalEntityCode;

public interface ListTermCurrenciesUseCase {

    TermCurrenciesResult listCurrencies(LegalEntityCode legalEntityCode);
}
