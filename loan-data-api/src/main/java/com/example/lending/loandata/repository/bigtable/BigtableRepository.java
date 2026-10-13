// Repository layer: read-only Bigtable access for loan row data.
package com.example.lending.loandata.repository.bigtable;

import com.example.lending.loandata.exception.DatastoreException;
import com.example.lending.loandata.observability.LogMask;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.models.Filters;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.TableId;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * Read-only Bigtable access layer. Exposes plain {@link Map} results so no Bigtable types leak
 * above this layer. The calling service converts maps into DTOs via mappers.
 */
@Repository
public class BigtableRepository {

  static final String TABLE_LOANS = "loans";
  static final String FAMILY_DETAILS = "details";

  private final BigtableDataClient client;

  public BigtableRepository(BigtableDataClient client) {
    this.client = client;
  }

  /**
   * Reads all columns in the {@code details} column family for the given loan number row key.
   *
   * @return a map of column qualifier → cell value (latest cell only), or {@link Optional#empty()}
   *     if the row does not exist.
   */
  public Optional<Map<String, String>> findLoanRow(String loanNumber) {
    try {
      Row row =
          client.readRow(
              TableId.of(TABLE_LOANS),
              loanNumber,
              Filters.FILTERS
                  .chain()
                  .filter(Filters.FILTERS.family().exactMatch(FAMILY_DETAILS))
                  .filter(Filters.FILTERS.limit().cellsPerColumn(1)));

      if (row == null || row.getCells().isEmpty()) {
        return Optional.empty();
      }

      Map<String, String> columns =
          row.getCells(FAMILY_DETAILS).stream()
              .collect(
                  Collectors.toMap(
                      cell -> cell.getQualifier().toStringUtf8(),
                      cell -> cell.getValue().toStringUtf8(),
                      // Keep the first cell if somehow more than one slips through
                      (a, b) -> a));

      return Optional.of(columns);
    } catch (Exception e) {
      throw new DatastoreException(
          "Bigtable read failed for loan: " + LogMask.accountOrLoanNumber(loanNumber), e);
    }
  }

  /**
   * Returns selected columns for a loan summary (used by the parallel enrichment path).
   *
   * @return column map or empty if the row does not exist.
   */
  public Optional<Map<String, String>> findLoanSummaryRow(String loanNumber) {
    try {
      Row row =
          client.readRow(
              TableId.of(TABLE_LOANS),
              loanNumber,
              Filters.FILTERS
                  .chain()
                  .filter(Filters.FILTERS.family().exactMatch(FAMILY_DETAILS))
                  .filter(
                      Filters.FILTERS
                          .qualifier()
                          .regex("loan_number|product_type|status|outstanding_balance|currency"))
                  .filter(Filters.FILTERS.limit().cellsPerColumn(1)));

      if (row == null || row.getCells().isEmpty()) {
        return Optional.empty();
      }

      Map<String, String> columns =
          row.getCells().stream()
              .collect(
                  Collectors.toMap(
                      cell -> cell.getQualifier().toStringUtf8(),
                      cell -> cell.getValue().toStringUtf8(),
                      (a, b) -> a));

      return Optional.of(columns);
    } catch (Exception e) {
      throw new DatastoreException(
          "Bigtable summary read failed for loan: " + LogMask.accountOrLoanNumber(loanNumber), e);
    }
  }
}
