package com.mmx.order.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "client_institution_enablement")
public class ClientEnablementEntity {

    @EmbeddedId
    private ClientEnablementId id;

    @Column(name = "legal_entity_code", nullable = false, length = 3)
    private String legalEntityCode;

    @Column(name = "tenor_1w", nullable = false) private boolean tenor1w;
    @Column(name = "tenor_2w", nullable = false) private boolean tenor2w;
    @Column(name = "tenor_1m", nullable = false) private boolean tenor1m;
    @Column(name = "tenor_3m", nullable = false) private boolean tenor3m;
    @Column(name = "tenor_6m", nullable = false) private boolean tenor6m;
    @Column(name = "tenor_1y", nullable = false) private boolean tenor1y;
    @Column(name = "notice_24h", nullable = false) private boolean notice24h;
    @Column(name = "notice_48h", nullable = false) private boolean notice48h;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ClientEnablementEntity() {}

    public ClientEnablementId getId() { return id; }
    public void setId(ClientEnablementId id) { this.id = id; }

    public String getLegalEntityCode() { return legalEntityCode; }
    public void setLegalEntityCode(String v) { this.legalEntityCode = v; }

    public boolean isTenor1w() { return tenor1w; }
    public void setTenor1w(boolean v) { this.tenor1w = v; }
    public boolean isTenor2w() { return tenor2w; }
    public void setTenor2w(boolean v) { this.tenor2w = v; }
    public boolean isTenor1m() { return tenor1m; }
    public void setTenor1m(boolean v) { this.tenor1m = v; }
    public boolean isTenor3m() { return tenor3m; }
    public void setTenor3m(boolean v) { this.tenor3m = v; }
    public boolean isTenor6m() { return tenor6m; }
    public void setTenor6m(boolean v) { this.tenor6m = v; }
    public boolean isTenor1y() { return tenor1y; }
    public void setTenor1y(boolean v) { this.tenor1y = v; }
    public boolean isNotice24h() { return notice24h; }
    public void setNotice24h(boolean v) { this.notice24h = v; }
    public boolean isNotice48h() { return notice48h; }
    public void setNotice48h(boolean v) { this.notice48h = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
