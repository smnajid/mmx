package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;

import java.util.UUID;

public interface ReferenceGenerator {

    DealingReference generateDealingReference();

    ContractNumber generateContractNumber();

    UUID generateSegmentId();
}
