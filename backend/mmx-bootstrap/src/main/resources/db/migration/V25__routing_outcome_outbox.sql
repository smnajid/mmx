-- Leg-B routing-outcome outbox: LODH commits a row same-tx as the hub-side transition it mirrors.
-- Drained by the routing-outcome relay onto the org-suffixed topic mmx.routed-order-outcome.{orgCode}.
CREATE TABLE routing_outcome_outbox (
    id                 UUID        NOT NULL,
    hub_order_id       UUID        NOT NULL,
    originating_le     VARCHAR(3)  NOT NULL,
    routing_id         UUID        NOT NULL,
    outcome_type       VARCHAR(20) NOT NULL,
    payload            TEXT        NOT NULL,
    status             VARCHAR(20) NOT NULL,
    publish_attempts   INT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    last_attempt_at    TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT pk_routing_outcome_outbox PRIMARY KEY (id),
    CONSTRAINT fk_routing_outcome_outbox_order FOREIGN KEY (hub_order_id) REFERENCES money_market_order (id)
);

CREATE INDEX idx_routing_outcome_outbox_status_created ON routing_outcome_outbox (status, created_at);
