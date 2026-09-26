package com.mmx.order.adapter.out.integration;

import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;

import java.util.UUID;

public class UuidReferenceGenerator implements ReferenceGenerator {

    @Override
    public DealingReference generateDealingReference() {
        return new DealingReference("DL-" + UUID.randomUUID());
    }

    @Override
    public ContractNumber generateContractNumber() {
        return new ContractNumber("CN-" + UUID.randomUUID());
    }

    @Override
    public UUID generateSegmentId() {
        return UUID.randomUUID();
    }
}
