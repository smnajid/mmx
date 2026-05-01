package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;

public interface ReferenceGenerator {

    DealingReference generateDealingReference();

    ContractNumber generateContractNumber();
}
