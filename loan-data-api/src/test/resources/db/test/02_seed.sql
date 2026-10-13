-- Integration test seed data (used when Docker / Testcontainers is available)

INSERT INTO customer (customer_id, full_name, customer_type, status, created_at)
VALUES ('CUST-0001', 'Alice Sample', 'RETAIL', 'ACTIVE', NOW()),
       ('CUST-0002', 'Bob Example', 'RETAIL', 'ACTIVE', NOW());

INSERT INTO account (account_number, account_type, product_code, status, opened_date, currency)
VALUES ('LN100001', 'LOAN', 'PERSONAL_LOAN', 'ACTIVE', '2022-01-15', 'USD'),
       ('DEP200001', 'DEPOSIT', 'SAVINGS', 'ACTIVE', '2021-03-10', 'USD');

INSERT INTO customer_account_relationship (customer_id, account_number, relationship_type,
                                           start_date, end_date)
VALUES ('CUST-0001', 'LN100001', 'PRIMARY', '2022-01-15', NULL),
       ('CUST-0001', 'DEP200001', 'PRIMARY', '2021-03-10', NULL);
