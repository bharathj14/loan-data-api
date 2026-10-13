-- Schema layer: seed data for local development and tests.
-- Covers all required scenarios: many-accounts, co-borrower, business customer,
-- guarantor/authorized-user, no-accounts, and ended relationship.

-- Scenario 3: business customer included as CUST-0003 (SMALL_BUSINESS).
-- Scenario 5: CUST-0005 has no relationship rows (customer with no accounts).
INSERT INTO loan_data.customer (customer_id, full_name, customer_type, status, created_at)
VALUES ('CUST-0001', 'Alice Sample',  'RETAIL',          'ACTIVE', NOW()),
       ('CUST-0002', 'Bob Example',   'RETAIL',          'ACTIVE', NOW()),
       ('CUST-0003', 'Carol Demo',    'SMALL_BUSINESS',  'ACTIVE', NOW()),
       ('CUST-0004', 'Dave Test',     'RETAIL',          'ACTIVE', NOW()),
       ('CUST-0005', 'Eve Nobody',    'RETAIL',          'ACTIVE', NOW())
ON CONFLICT DO NOTHING;

-- Five loan accounts LN1000000001–LN1000000005.
INSERT INTO loan_data.account (account_number, account_type, product_code, status, opened_date, currency)
VALUES ('LN1000000001', 'LOAN', 'PERSONAL_LOAN', 'ACTIVE', '2022-01-15', 'USD'),
       ('LN1000000002', 'LOAN', 'MORTGAGE',      'ACTIVE', '2020-06-01', 'USD'),
       ('LN1000000003', 'LOAN', 'PERSONAL_LOAN', 'ACTIVE', '2021-09-10', 'USD'),
       ('LN1000000004', 'LOAN', 'AUTO_LOAN',     'ACTIVE', '2023-03-20', 'USD'),
       ('LN1000000005', 'LOAN', 'MORTGAGE',      'ACTIVE', '2019-11-05', 'USD')
ON CONFLICT DO NOTHING;

-- Scenario 1: CUST-0001 holds PRIMARY on three loan accounts.
-- Scenario 2: CUST-0002 is CO_BORROWER on LN1000000001 (shared with CUST-0001).
-- Scenario 4 (GUARANTOR): CUST-0003 is GUARANTOR on LN1000000001.
-- Scenario 4 (AUTHORIZED_USER): CUST-0004 is AUTHORIZED_USER on LN1000000002.
-- Scenario 6 (ended): CUST-0004 had an AUTHORIZED_USER relationship on LN1000000003, now ended.
-- CUST-0002 is PRIMARY on LN1000000005.
-- CUST-0003 is PRIMARY on LN1000000004 (business customer's own account).
INSERT INTO loan_data.customer_account_relationship
    (customer_id, account_number, relationship_type, start_date, end_date)
VALUES
    ('CUST-0001', 'LN1000000001', 'PRIMARY',          '2022-01-15', NULL),
    ('CUST-0001', 'LN1000000002', 'PRIMARY',          '2020-06-01', NULL),
    ('CUST-0001', 'LN1000000003', 'PRIMARY',          '2021-09-10', NULL),
    ('CUST-0002', 'LN1000000001', 'CO_BORROWER',      '2022-01-15', NULL),
    ('CUST-0002', 'LN1000000005', 'PRIMARY',          '2019-11-05', NULL),
    ('CUST-0003', 'LN1000000004', 'PRIMARY',          '2023-03-20', NULL),
    ('CUST-0003', 'LN1000000001', 'GUARANTOR',        '2022-01-15', NULL),
    ('CUST-0004', 'LN1000000002', 'AUTHORIZED_USER',  '2020-06-01', NULL),
    ('CUST-0004', 'LN1000000003', 'AUTHORIZED_USER',  '2021-09-10', '2024-03-31')
ON CONFLICT DO NOTHING;
