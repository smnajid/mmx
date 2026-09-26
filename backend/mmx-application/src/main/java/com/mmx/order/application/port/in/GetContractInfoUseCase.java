package com.mmx.order.application.port.in;

import com.mmx.order.application.ordercreation.ContractInfoResult;

public interface GetContractInfoUseCase {

    ContractInfoResult getContractInfo(String contractNumber);
}
