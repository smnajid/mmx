package com.mmx.order.application.port.in;

import com.mmx.order.application.ordercreation.NoticePeriodsResult;
import com.mmx.order.domain.model.LegalEntityCode;

public interface ListOnCallNoticePeriodsUseCase {

    NoticePeriodsResult listNoticePeriods(LegalEntityCode legalEntityCode, String currency);
}
