package com.mmx.order.application.port.in;

import com.mmx.order.application.ordercreation.LiveContractsResult;
import com.mmx.order.domain.model.OrderType;

public interface ListLiveContractsUseCase {

    LiveContractsResult listLiveContracts(String portfolioNumber, OrderType orderType);
}
