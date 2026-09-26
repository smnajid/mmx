package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.Institution;

/**
 * Full replacement of an institution's Term and OnCall counterparty accounts ({@code null} clears) by the
 * owning LegalEntity's settings role: a Trader on a hub-native institution, a ClientRepresentative on an
 * onboarded institution. Allowed whether or not the institution is closed to new business.
 */
public interface UpdateCounterpartyAccountsUseCase {

    Institution update(UpdateCommand command);

    record UpdateCommand(
            ScopeContext scope, String institutionCode, String termCounterpartyAccount, String onCallCounterpartyAccount) {}
}
