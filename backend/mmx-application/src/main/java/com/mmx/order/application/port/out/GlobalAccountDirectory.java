package com.mmx.order.application.port.out;

import com.mmx.order.domain.exception.RoutingFailure;
import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.Optional;

/** Resolves the global account for a client at a hub for a currency. */
public interface GlobalAccountDirectory {

  /**
   * @return the configured account, or empty when no tuple exists (caller may throw {@link
   *     RoutingFailure})
   */
  Optional<GlobalAccount> resolve(
      LegalEntityCode clientLegalEntityCode,
      LegalEntityCode hubLegalEntityCode,
      String currency);
}
