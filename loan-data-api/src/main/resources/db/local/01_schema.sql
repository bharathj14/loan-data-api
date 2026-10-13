-- Schema layer: DDL for loan_data schema — tables for AlloyDB/Postgres.

CREATE SCHEMA IF NOT EXISTS loan_data;

CREATE TABLE IF NOT EXISTS loan_data.customer
(
    customer_id   VARCHAR(50)  NOT NULL PRIMARY KEY,
    full_name     VARCHAR(200) NOT NULL,
    customer_type VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS loan_data.account
(
    account_number VARCHAR(50) NOT NULL PRIMARY KEY,
    account_type   VARCHAR(20) NOT NULL,
    product_code   VARCHAR(50) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    opened_date    DATE,
    currency       CHAR(3)     NOT NULL
);

CREATE TABLE IF NOT EXISTS loan_data.customer_account_relationship
(
    customer_id       VARCHAR(50) NOT NULL REFERENCES loan_data.customer (customer_id),
    account_number    VARCHAR(50) NOT NULL REFERENCES loan_data.account (account_number),
    relationship_type VARCHAR(30) NOT NULL,
    start_date        DATE,
    end_date          DATE,
    PRIMARY KEY (customer_id, account_number, relationship_type)
);
