package com.mmx.order.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "institution")
public class InstitutionEntity {

    @Id
    @Column(name = "institution_code", nullable = false, length = 32)
    private String institutionCode;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "legal_entity_code", nullable = false, length = 3)
    private String legalEntityCode;

    @Column(name = "hub_legal_entity_code", length = 3)
    private String hubLegalEntityCode;

    @Column(name = "hub_institution_code", length = 32)
    private String hubInstitutionCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public InstitutionEntity() {}

    public String getInstitutionCode() {
        return institutionCode;
    }

    public void setInstitutionCode(String institutionCode) {
        this.institutionCode = institutionCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getLegalEntityCode() {
        return legalEntityCode;
    }

    public void setLegalEntityCode(String legalEntityCode) {
        this.legalEntityCode = legalEntityCode;
    }

    public String getHubLegalEntityCode() {
        return hubLegalEntityCode;
    }

    public void setHubLegalEntityCode(String hubLegalEntityCode) {
        this.hubLegalEntityCode = hubLegalEntityCode;
    }

    public String getHubInstitutionCode() {
        return hubInstitutionCode;
    }

    public void setHubInstitutionCode(String hubInstitutionCode) {
        this.hubInstitutionCode = hubInstitutionCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
