#!/usr/bin/env bash
# seed-local.sh — creates the Cloud Bigtable 'loans' table in the local emulator
# and loads sample loan rows via the cbt CLI.
#
# Prerequisites:
#   - cbt installed: https://cloud.google.com/bigtable/docs/cbt-reference
#     (included in the Google Cloud SDK: gcloud components install cbt)
#   - The Bigtable emulator is running (started by docker compose up)
#
# Usage:
#   BIGTABLE_EMULATOR_HOST=localhost:9035 bash scripts/seed-local.sh
#
# The PostgreSQL schema and seed data are loaded automatically by Spring Boot on
# startup from src/main/resources/db/local/ when the local profile is active.

set -euo pipefail

EMULATOR_HOST="${BIGTABLE_EMULATOR_HOST:-localhost:9035}"
PROJECT="local-project"
INSTANCE="local-instance"
TABLE="loans"
FAMILY="details"

export BIGTABLE_EMULATOR_HOST="${EMULATOR_HOST}"

echo "==> Connecting to Bigtable emulator at ${EMULATOR_HOST} (project=${PROJECT}, instance=${INSTANCE})"

# Create table and column family (ignore "already exists" errors on re-runs)
echo "--> Creating table '${TABLE}' ..."
cbt -project "${PROJECT}" -instance "${INSTANCE}" createtable "${TABLE}" || true

echo "--> Creating column family '${FAMILY}' ..."
cbt -project "${PROJECT}" -instance "${INSTANCE}" createfamily "${TABLE}" "${FAMILY}" || true

echo "--> Seeding loan rows ..."

cbt -project "${PROJECT}" -instance "${INSTANCE}" set "${TABLE}" LN100001 \
  "${FAMILY}:loan_number=LN100001" \
  "${FAMILY}:product_type=PERSONAL_LOAN" \
  "${FAMILY}:status=ACTIVE" \
  "${FAMILY}:principal_amount=10000.00" \
  "${FAMILY}:outstanding_balance=8500.00" \
  "${FAMILY}:interest_rate=5.25" \
  "${FAMILY}:currency=USD" \
  "${FAMILY}:origination_date=2022-01-15" \
  "${FAMILY}:maturity_date=2027-01-15" \
  "${FAMILY}:next_payment_due_date=2024-02-01" \
  "${FAMILY}:next_payment_amount=250.00" \
  "${FAMILY}:borrower_customer_id=CUST-0001"

cbt -project "${PROJECT}" -instance "${INSTANCE}" set "${TABLE}" LN100002 \
  "${FAMILY}:loan_number=LN100002" \
  "${FAMILY}:product_type=MORTGAGE" \
  "${FAMILY}:status=ACTIVE" \
  "${FAMILY}:principal_amount=250000.00" \
  "${FAMILY}:outstanding_balance=230000.00" \
  "${FAMILY}:interest_rate=3.75" \
  "${FAMILY}:currency=USD" \
  "${FAMILY}:origination_date=2020-06-01" \
  "${FAMILY}:maturity_date=2050-06-01" \
  "${FAMILY}:next_payment_due_date=2024-02-01" \
  "${FAMILY}:next_payment_amount=1150.00" \
  "${FAMILY}:borrower_customer_id=CUST-0002"

cbt -project "${PROJECT}" -instance "${INSTANCE}" set "${TABLE}" LN100003 \
  "${FAMILY}:loan_number=LN100003" \
  "${FAMILY}:product_type=AUTO_LOAN" \
  "${FAMILY}:status=CLOSED" \
  "${FAMILY}:principal_amount=20000.00" \
  "${FAMILY}:outstanding_balance=0.00" \
  "${FAMILY}:interest_rate=4.50" \
  "${FAMILY}:currency=USD" \
  "${FAMILY}:origination_date=2019-03-10" \
  "${FAMILY}:maturity_date=2024-03-10" \
  "${FAMILY}:next_payment_due_date=" \
  "${FAMILY}:next_payment_amount=0.00" \
  "${FAMILY}:borrower_customer_id=CUST-0001"

echo "==> Done. Seeded loans: LN100001, LN100002, LN100003"
echo ""
echo "Verify with:"
echo "  BIGTABLE_EMULATOR_HOST=${EMULATOR_HOST} cbt -project ${PROJECT} -instance ${INSTANCE} read ${TABLE}"
