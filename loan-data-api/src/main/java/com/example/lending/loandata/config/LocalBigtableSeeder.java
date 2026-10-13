// Config layer: seeds Bigtable emulator with test data for the local profile.
package com.example.lending.loandata.config;

import com.google.cloud.bigtable.admin.v2.BigtableTableAdminClient;
import com.google.cloud.bigtable.admin.v2.models.CreateTableRequest;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.models.BulkMutation;
import com.google.cloud.bigtable.data.v2.models.Mutation;
import com.google.protobuf.ByteString;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Seeds the local Bigtable emulator with loan rows matching the SQL seed accounts. */
@Component
@Profile("local")
public class LocalBigtableSeeder {

  private static final Logger log = LoggerFactory.getLogger(LocalBigtableSeeder.class);
  private static final String TABLE_ID = "loans";
  private static final String FAMILY = "details";

  private final BigtableDataClient dataClient;
  private final BigtableTableAdminClient adminClient;

  public LocalBigtableSeeder(BigtableDataClient dataClient, BigtableTableAdminClient adminClient) {
    this.dataClient = dataClient;
    this.adminClient = adminClient;
  }

  @PostConstruct
  public void seed() {
    ensureTableExists();
    seedLoanData();
  }

  private void ensureTableExists() {
    if (!adminClient.exists(TABLE_ID)) {
      adminClient.createTable(CreateTableRequest.of(TABLE_ID).addFamily(FAMILY));
      log.info("Created Bigtable table '{}'", TABLE_ID);
    }
  }

  private void seedLoanData() {
    // Seed LN1000000001 through LN1000000005; LN1000000004 has missing payment columns
    // intentionally.
    String[][] loans = {
      {"LN1000000001", "150000.00", "1200.00", "2026-11-01", "148500.00"},
      {"LN1000000002", "250000.00", "2000.00", "2026-11-15", "247000.00"},
      {"LN1000000003", "50000.00", "600.00", "2026-11-01", "49200.00"},
      {"LN1000000004", null, null, null, null}, // intentionally missing payment columns
      {"LN1000000005", "320000.00", "2400.00", "2026-12-01", "315000.00"},
    };

    BulkMutation mutations = BulkMutation.create(TABLE_ID);
    for (String[] loan : loans) {
      String loanNumber = loan[0];
      Mutation m = Mutation.create();
      setCell(m, "loan_amount", loan[1]);
      setCell(m, "next_payment_amount", loan[2]);
      setCell(m, "next_payment_due_date", loan[3]);
      setCell(m, "outstanding_balance", loan[4]);
      mutations.add(loanNumber, m);
    }
    dataClient.bulkMutateRows(mutations);
    log.info("Seeded {} loans into Bigtable table '{}'", loans.length, TABLE_ID);
  }

  private void setCell(Mutation m, String qualifier, String value) {
    if (value == null) return;
    m.setCell(FAMILY, ByteString.copyFromUtf8(qualifier), ByteString.copyFromUtf8(value));
  }
}
