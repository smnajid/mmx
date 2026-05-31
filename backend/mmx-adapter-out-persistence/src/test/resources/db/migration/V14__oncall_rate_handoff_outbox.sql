CREATE TABLE oncall_rate_handoff_outbox (
    id                UUID                     NOT NULL,
    segment_id        UUID                     NOT NULL,
    payload           TEXT                     NOT NULL,
    status            VARCHAR(20)              NOT NULL,
    publish_attempts  INT                      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    last_attempt_at   TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT pk_oncall_rate_handoff_outbox PRIMARY KEY (id),
    CONSTRAINT fk_oncall_rate_handoff_outbox_segment
        FOREIGN KEY (segment_id) REFERENCES oncall_rate_segment (segment_id)
);

CREATE INDEX idx_oncall_rate_handoff_outbox_status_created
    ON oncall_rate_handoff_outbox (status, created_at);
