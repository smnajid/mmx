CREATE TABLE back_office_outbox (
    id                UUID        NOT NULL,
    order_id          UUID        NOT NULL UNIQUE,
    payload           TEXT        NOT NULL,
    status            VARCHAR(20) NOT NULL,
    publish_attempts  INT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    last_attempt_at   TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT pk_back_office_outbox PRIMARY KEY (id),
    CONSTRAINT fk_back_office_outbox_order FOREIGN KEY (order_id) REFERENCES money_market_order (id)
);

CREATE INDEX idx_back_office_outbox_status_created ON back_office_outbox (status, created_at);
