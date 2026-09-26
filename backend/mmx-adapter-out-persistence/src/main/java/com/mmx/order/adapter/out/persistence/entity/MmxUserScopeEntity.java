package com.mmx.order.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "mmx_user_scope")
@IdClass(MmxUserScopeId.class)
public class MmxUserScopeEntity {

    @Id
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Id
    @Column(name = "legal_entity_code", nullable = false, length = 3)
    private String legalEntityCode;

    @Id
    @Column(name = "role", nullable = false, length = 30)
    private String role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private MmxUserEntity user;

    public MmxUserScopeEntity() {}

    public MmxUserScopeEntity(String userId, String legalEntityCode, String role) {
        this.userId = userId;
        this.legalEntityCode = legalEntityCode;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getLegalEntityCode() {
        return legalEntityCode;
    }

    public void setLegalEntityCode(String legalEntityCode) {
        this.legalEntityCode = legalEntityCode;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public MmxUserEntity getUser() {
        return user;
    }

    public void setUser(MmxUserEntity user) {
        this.user = user;
    }
}
