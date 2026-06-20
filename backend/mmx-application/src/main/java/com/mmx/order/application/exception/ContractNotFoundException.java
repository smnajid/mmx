package com.mmx.order.application.exception;

public final class ContractNotFoundException extends RuntimeException {

    public ContractNotFoundException(String contractNumber) {
        super("No executed OnCall Subscription found for contract number: " + contractNumber);
    }
}
