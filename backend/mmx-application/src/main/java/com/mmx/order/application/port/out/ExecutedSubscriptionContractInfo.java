package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.NoticePeriod;

public record ExecutedSubscriptionContractInfo(String currency, NoticePeriod noticePeriod) {}
