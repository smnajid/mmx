package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.InstitutionClosedToNewBusinessException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.OrderOperation;

import java.util.Optional;

/**
 * Closed to new business: Subscription and Increase add exposure and are refused on a closed
 * institution (offboarded, deactivated, or outside the effective enablement); Decrease and Redemption
 * against existing contracts are always allowed.
 */
public final class NewBusinessPolicy {

    private NewBusinessPolicy() {}

    public static boolean addsExposure(OrderOperation operation) {
        return operation == OrderOperation.SUBSCRIPTION || operation == OrderOperation.INCREASE;
    }

    public static void requireOpenForNewBusiness(Institution institution, OrderOperation operation) {
        refusal(institution, operation).ifPresent(reason -> {
            throw new InstitutionClosedToNewBusinessException(reason);
        });
    }

    /** Why {@code operation} is refused on {@code institution}, if it is. */
    public static Optional<String> refusal(Institution institution, OrderOperation operation) {
        if (addsExposure(operation) && institution.isClosedToNewBusiness()) {
            return Optional.of("Institution " + institution.getInstitutionCode() + " is closed to new business; "
                    + operation + " is refused");
        }
        return Optional.empty();
    }
}
