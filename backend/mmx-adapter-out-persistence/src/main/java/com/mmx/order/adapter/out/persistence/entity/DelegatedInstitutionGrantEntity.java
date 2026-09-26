package com.mmx.order.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "delegated_institution_grant")
public class DelegatedInstitutionGrantEntity {

    @EmbeddedId
    private DelegatedInstitutionGrantId id;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "tenor_1w", nullable = false)
    private boolean tenor1w;

    @Column(name = "tenor_2w", nullable = false)
    private boolean tenor2w;

    @Column(name = "tenor_1m", nullable = false)
    private boolean tenor1m;

    @Column(name = "tenor_3m", nullable = false)
    private boolean tenor3m;

    @Column(name = "tenor_6m", nullable = false)
    private boolean tenor6m;

    @Column(name = "tenor_1y", nullable = false)
    private boolean tenor1y;

    @Column(name = "notice_24h", nullable = false)
    private boolean notice24h;

    @Column(name = "notice_48h", nullable = false)
    private boolean notice48h;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DelegatedInstitutionGrantEntity() {}

    public DelegatedInstitutionGrantId getId() {
        return id;
    }

    public void setId(DelegatedInstitutionGrantId id) {
        this.id = id;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isTenor1w() {
        return tenor1w;
    }

    public void setTenor1w(boolean tenor1w) {
        this.tenor1w = tenor1w;
    }

    public boolean isTenor2w() {
        return tenor2w;
    }

    public void setTenor2w(boolean tenor2w) {
        this.tenor2w = tenor2w;
    }

    public boolean isTenor1m() {
        return tenor1m;
    }

    public void setTenor1m(boolean tenor1m) {
        this.tenor1m = tenor1m;
    }

    public boolean isTenor3m() {
        return tenor3m;
    }

    public void setTenor3m(boolean tenor3m) {
        this.tenor3m = tenor3m;
    }

    public boolean isTenor6m() {
        return tenor6m;
    }

    public void setTenor6m(boolean tenor6m) {
        this.tenor6m = tenor6m;
    }

    public boolean isTenor1y() {
        return tenor1y;
    }

    public void setTenor1y(boolean tenor1y) {
        this.tenor1y = tenor1y;
    }

    public boolean isNotice24h() {
        return notice24h;
    }

    public void setNotice24h(boolean notice24h) {
        this.notice24h = notice24h;
    }

    public boolean isNotice48h() {
        return notice48h;
    }

    public void setNotice48h(boolean notice48h) {
        this.notice48h = notice48h;
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
