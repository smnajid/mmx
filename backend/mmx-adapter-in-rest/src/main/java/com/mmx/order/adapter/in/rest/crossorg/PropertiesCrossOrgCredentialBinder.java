package com.mmx.order.adapter.in.rest.crossorg;

import com.mmx.order.application.port.out.CrossOrgCredentialBinder;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.Map;
import java.util.Optional;

/**
 * Properties-backed {@link CrossOrgCredentialBinder}. Maps transport credentials
 * ({@code X-MMX-CrossOrg-Key} header values) to the proven {@link LegalEntityCode}. The credential
 * store is provisioned by the connection-registration task and held in configuration properties.
 */
public class PropertiesCrossOrgCredentialBinder implements CrossOrgCredentialBinder {

    private final Map<String, String> credentialToLegalEntity;

    public PropertiesCrossOrgCredentialBinder(Map<String, String> credentialToLegalEntity) {
        this.credentialToLegalEntity = Map.copyOf(credentialToLegalEntity);
    }

    @Override
    public Optional<LegalEntityCode> bindOriginatingLegalEntity(String credential) {
        if (credential == null) {
            return Optional.empty();
        }
        String code = credentialToLegalEntity.get(credential.trim());
        if (code == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new LegalEntityCode(code));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
