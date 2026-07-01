package com.mmx.order.adapter.out.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class MmxUserScopeId implements Serializable {

    private String userId;
    private String legalEntityCode;
    private String role;

    public MmxUserScopeId() {}

    public MmxUserScopeId(String userId, String legalEntityCode, String role) {
        this.userId = userId;
        this.legalEntityCode = legalEntityCode;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MmxUserScopeId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && Objects.equals(legalEntityCode, that.legalEntityCode)
                && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, legalEntityCode, role);
    }
}
