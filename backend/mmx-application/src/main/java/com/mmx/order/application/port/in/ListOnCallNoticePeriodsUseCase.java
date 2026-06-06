package com.mmx.order.application.port.in;

import com.mmx.order.application.ordercreation.NoticePeriodsResult;

public interface ListOnCallNoticePeriodsUseCase {

    NoticePeriodsResult listNoticePeriods(String currency);
}
