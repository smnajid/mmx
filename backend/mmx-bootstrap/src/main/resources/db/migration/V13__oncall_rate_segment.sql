CREATE TABLE oncall_rate_segment (
    segment_id         UUID                     NOT NULL,
    institution_code   VARCHAR(32)              NOT NULL,
    currency           VARCHAR(3)               NOT NULL,
    notice_period      VARCHAR(10)              NOT NULL,
    rate               DECIMAL(12, 8)           NOT NULL,
    value_date         DATE                     NOT NULL,
    end_date           DATE                     NOT NULL,
    status             VARCHAR(32)              NOT NULL,
    validated_at       TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT pk_oncall_rate_segment PRIMARY KEY (segment_id),
    CONSTRAINT fk_oncall_rate_segment_institution
        FOREIGN KEY (institution_code) REFERENCES institution (institution_code)
);

CREATE INDEX idx_oncall_rate_segment_institution
    ON oncall_rate_segment (institution_code);
