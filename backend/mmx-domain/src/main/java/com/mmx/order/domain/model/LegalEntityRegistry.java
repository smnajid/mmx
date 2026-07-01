package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.DuplicateLegalEntityCodeException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory registry enforcing global LegalEntityCode uniqueness. */
public final class LegalEntityRegistry {

    private final Map<LegalEntityCode, LegalEntity> byCode = new HashMap<>();

    public LegalEntity register(LegalEntity entity) {
        if (byCode.containsKey(entity.getCode())) {
            throw new DuplicateLegalEntityCodeException(
                    "LegalEntityCode already registered: " + entity.getCode());
        }
        byCode.put(entity.getCode(), entity);
        return entity;
    }

    public Optional<LegalEntity> findByCode(LegalEntityCode code) {
        return Optional.ofNullable(byCode.get(code));
    }
}
