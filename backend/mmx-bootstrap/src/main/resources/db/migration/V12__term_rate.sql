CREATE TABLE term_rate (
    trading_date       DATE                     NOT NULL,
    institution_code   VARCHAR(32)              NOT NULL,
    currency           VARCHAR(3)               NOT NULL,
    tenor              VARCHAR(10)              NOT NULL,
    rate               DECIMAL(12, 8)           NOT NULL,
    uploaded_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    uploaded_by        VARCHAR(100)             NOT NULL,
    PRIMARY KEY (trading_date, institution_code, currency, tenor),
    CONSTRAINT fk_term_rate_institution
        FOREIGN KEY (institution_code) REFERENCES institution (institution_code)
);

CREATE INDEX idx_term_rate_trading_date ON term_rate (trading_date);
