-- Local seed data: generic sample records only — no real customer names or amounts.

INSERT INTO customer (customer_id, full_name, customer_type, status, created_at)
VALUES ('CUST-0001', 'Alice Sample', 'RETAIL', 'ACTIVE', NOW()),
       ('CUST-0002', 'Bob Example', 'RETAIL', 'ACTIVE', NOW()),
       ('CUST-0003', 'Carol Demo', 'SMALL_BUSINESS', 'ACTIVE', NOW())
ON CONFLICT DO NOTHING;

INSERT INTO account (account_number, account_type, product_code, status, opened_date, currency)
VALUES ('LN100001', 'LOAN', 'PERSONAL_LOAN', 'ACTIVE', '2022-01-15', 'USD'),
       ('LN100002', 'LOAN', 'MORTGAGE', 'ACTIVE', '2020-06-01', 'USD'),
       ('DEP200001', 'DEPOSIT', 'SAVINGS', 'ACTIVE', '2021-03-10', 'USD'),
       ('CC300001', 'CREDIT_CARD', 'PLATINUM_CARD', 'ACTIVE', '2023-07-20', 'USD')
ON CONFLICT DO NOTHING;

INSERT INTO customer_account_relationship (customer_id, account_number, relationship_type,
                                           start_date, end_date)
VALUES ('CUST-0001', 'LN100001', 'PRIMARY', '2022-01-15', NULL),
       ('CUST-0001', 'DEP200001', 'PRIMARY', '2021-03-10', NULL),
       ('CUST-0001', 'CC300001', 'PRIMARY', '2023-07-20', NULL),
       ('CUST-0002', 'LN100001', 'CO_BORROWER', '2022-01-15', NULL),
       ('CUST-0002', 'LN100002', 'PRIMARY', '2020-06-01', NULL),
       ('CUST-0003', 'LN100002', 'GUARANTOR', '2020-06-01', NULL)
ON CONFLICT DO NOTHING;
