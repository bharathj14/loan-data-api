-- Integration test schema (used when Docker / Testcontainers is available)
-- Mirrors src/main/resources/db/local/01_schema.sql

CREATE TABLE IF NOT EXISTS customer
(
    customer_id   VARCHAR(50)  NOT NULL PRIMARY KEY,
    full_name     VARCHAR(200) NOT NULL,
    customer_type VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS account
(
    account_number VARCHAR(50) NOT NULL PRIMARY KEY,
    account_type   VARCHAR(20) NOT NULL,
    product_code   VARCHAR(50) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    opened_date    DATE,
    currency       CHAR(3)     NOT NULL
);

CREATE TABLE IF NOT EXISTS customer_account_relationship
(
    customer_id       VARCHAR(50) NOT NULL REFERENCES customer (customer_id),
    account_number    VARCHAR(50) NOT NULL REFERENCES account (account_number),
    relationship_type VARCHAR(30) NOT NULL,
    start_date        DATE,
    end_date          DATE,
    PRIMARY KEY (customer_id, account_number, relationship_type)
);
