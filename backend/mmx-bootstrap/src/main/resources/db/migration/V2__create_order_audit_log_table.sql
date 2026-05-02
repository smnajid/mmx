CREATE TABLE order_audit_log (
    id         BIGSERIAL       NOT NULL,
    order_id   UUID            NOT NULL,
    event_type VARCHAR(50)     NOT NULL,
    actor_id   VARCHAR(100)    NOT NULL,
    event_time TIMESTAMP WITH TIME ZONE NOT NULL,
    details    JSONB           NULL,
    CONSTRAINT pk_order_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_order FOREIGN KEY (order_id)
        REFERENCES money_market_order (id)
);
