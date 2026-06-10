package com.pfe.clientdashboard.transaction.repository;

import com.pfe.clientdashboard.transaction.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionGlobalRepository extends JpaRepository<Transaction, String>,
        JpaSpecificationExecutor<Transaction> {

    @Query("""
        SELECT t FROM Transaction t
        LEFT JOIN FETCH t.provider
        LEFT JOIN FETCH t.transactionType
        WHERE t.client.id = :clientId
        ORDER BY t.transactionDate DESC
    """)
    List<Transaction> findByClientId(@Param("clientId") String clientId);

    @Query(value = """
        SELECT tt.type,
               SUM(t.amount) AS total_amount,
               COUNT(t.id)   AS total_count
        FROM transaction t
        JOIN type_transaction tt ON tt.id = t.transaction_type_id
        WHERE t.reversal_flag <> 'R'
          AND t.transaction_date >= CAST(:from AS timestamp)
          AND t.transaction_date <= CAST(:to   AS timestamp)
        GROUP BY tt.type
    """, nativeQuery = true)
    List<Object[]> globalTotalsAllUsers(
            @Param("from") String from,
            @Param("to")   String to);

    @Query(value = """
        SELECT tt.category,
               SUM(t.amount) AS total_amount,
               COUNT(t.id)   AS total_count,
               AVG(t.amount) AS avg_amount
        FROM transaction t
        JOIN type_transaction tt ON tt.id = t.transaction_type_id
        WHERE tt.type = 'D' AND t.reversal_flag <> 'R'
          AND t.transaction_date >= CAST(:from AS timestamp)
          AND t.transaction_date <= CAST(:to   AS timestamp)
        GROUP BY tt.category
        ORDER BY SUM(t.amount) DESC
    """, nativeQuery = true)
    List<Object[]> expenseRateByCategoryAllUsers(
            @Param("from") String from,
            @Param("to")   String to);

    @Query(value = """
        SELECT tt.category,
               SUM(t.amount) AS total_amount,
               COUNT(t.id)   AS total_count,
               AVG(t.amount) AS avg_amount
        FROM transaction t
        JOIN type_transaction tt ON tt.id = t.transaction_type_id
        WHERE tt.type = 'C' AND t.reversal_flag <> 'R'
          AND t.transaction_date >= CAST(:from AS timestamp)
          AND t.transaction_date <= CAST(:to   AS timestamp)
        GROUP BY tt.category
        ORDER BY SUM(t.amount) DESC
    """, nativeQuery = true)
    List<Object[]> revenueRateByCategoryAllUsers(
            @Param("from") String from,
            @Param("to")   String to);

    @Query(value = """
        SELECT tt.title    AS sub_cat,
               SUM(t.amount) AS total_amount,
               COUNT(t.id)   AS total_count,
               AVG(t.amount) AS avg_amount
        FROM transaction t
        JOIN type_transaction tt ON tt.id = t.transaction_type_id
        WHERE tt.type = 'D' AND tt.category = :category
          AND t.reversal_flag <> 'R'
          AND t.transaction_date >= CAST(:from AS timestamp)
          AND t.transaction_date <= CAST(:to   AS timestamp)
        GROUP BY tt.title
        ORDER BY SUM(t.amount) DESC
    """, nativeQuery = true)
    List<Object[]> expenseSubCategoriesByCategory(
            @Param("category") String category,
            @Param("from")     String from,
            @Param("to")       String to);

    @Query(value = """
        SELECT tt.title    AS sub_cat,
               SUM(t.amount) AS total_amount,
               COUNT(t.id)   AS total_count,
               AVG(t.amount) AS avg_amount
        FROM transaction t
        JOIN type_transaction tt ON tt.id = t.transaction_type_id
        WHERE tt.type = 'C' AND tt.category = :category
          AND t.reversal_flag <> 'R'
          AND t.transaction_date >= CAST(:from AS timestamp)
          AND t.transaction_date <= CAST(:to   AS timestamp)
        GROUP BY tt.title
        ORDER BY SUM(t.amount) DESC
    """, nativeQuery = true)
    List<Object[]> revenueSubCategoriesByCategory(
            @Param("category") String category,
            @Param("from")     String from,
            @Param("to")       String to);

    @Query(value = """
        SELECT DISTINCT tt.category
        FROM transaction t
        JOIN type_transaction tt ON tt.id = t.transaction_type_id
        WHERE tt.type = 'D' AND t.reversal_flag <> 'R'
          AND t.transaction_date >= CAST(:from AS timestamp)
          AND t.transaction_date <= CAST(:to   AS timestamp)
        ORDER BY tt.category ASC
    """, nativeQuery = true)
    List<String> distinctExpenseCategories(
            @Param("from") String from,
            @Param("to")   String to);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.provider.id = :providerId")
    long countByProvider(@Param("providerId") String providerId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.provider.id = :providerId")
    BigDecimal sumAmountByProvider(@Param("providerId") String providerId);

    @Query("SELECT COALESCE(AVG(t.amount), 0) FROM Transaction t WHERE t.provider.id = :providerId")
    BigDecimal avgAmountByProvider(@Param("providerId") String providerId);

    @Query("SELECT COALESCE(MIN(t.amount), 0) FROM Transaction t WHERE t.provider.id = :providerId")
    BigDecimal minAmountByProvider(@Param("providerId") String providerId);

    @Query("SELECT COALESCE(MAX(t.amount), 0) FROM Transaction t WHERE t.provider.id = :providerId")
    BigDecimal maxAmountByProvider(@Param("providerId") String providerId);

    @Query("SELECT COUNT(DISTINCT t.client.id) FROM Transaction t WHERE t.provider.id = :providerId")
    long countDistinctClientsByProvider(@Param("providerId") String providerId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.provider.id = :providerId AND t.reversalFlag = 'Y'")
    long countReversalsByProvider(@Param("providerId") String providerId);

    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.provider.id = :providerId
        AND t.transactionDate >= :from AND t.transactionDate <= :to
    """)
    long countByProviderWithFilter(@Param("providerId") String providerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.provider.id = :providerId
        AND t.transactionDate >= :from AND t.transactionDate <= :to
    """)
    BigDecimal sumAmountByProviderWithFilter(@Param("providerId") String providerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COALESCE(AVG(t.amount), 0) FROM Transaction t
        WHERE t.provider.id = :providerId
        AND t.transactionDate >= :from AND t.transactionDate <= :to
    """)
    BigDecimal avgAmountByProviderWithFilter(@Param("providerId") String providerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COALESCE(MIN(t.amount), 0) FROM Transaction t
        WHERE t.provider.id = :providerId
        AND t.transactionDate >= :from AND t.transactionDate <= :to
    """)
    BigDecimal minAmountByProviderWithFilter(@Param("providerId") String providerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COALESCE(MAX(t.amount), 0) FROM Transaction t
        WHERE t.provider.id = :providerId
        AND t.transactionDate >= :from AND t.transactionDate <= :to
    """)
    BigDecimal maxAmountByProviderWithFilter(@Param("providerId") String providerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COUNT(DISTINCT t.client.id) FROM Transaction t
        WHERE t.provider.id = :providerId
        AND t.transactionDate >= :from AND t.transactionDate <= :to
    """)
    long countDistinctClientsByProviderWithFilter(@Param("providerId") String providerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.provider.id = :providerId AND t.transactionType.type = 'D'
    """)
    long countDebitByProvider(@Param("providerId") String providerId);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.provider.id = :providerId AND t.transactionType.type = 'D'
    """)
    BigDecimal sumDebitByProvider(@Param("providerId") String providerId);

    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.provider.id = :providerId AND t.transactionType.type = 'C'
    """)
    long countCreditByProvider(@Param("providerId") String providerId);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.provider.id = :providerId AND t.transactionType.type = 'C'
    """)
    BigDecimal sumCreditByProvider(@Param("providerId") String providerId);

    @Query("""
        SELECT t.transactionType, COUNT(t), SUM(t.amount)
        FROM Transaction t
        WHERE t.provider.id = :providerId AND t.transactionType IS NOT NULL
        GROUP BY t.transactionType ORDER BY COUNT(t) DESC
    """)
    List<Object[]> getTransactionTypeStatsByProvider(@Param("providerId") String providerId);

    @Query("""
        SELECT t.transactionType, COUNT(t), SUM(t.amount)
        FROM Transaction t
        WHERE t.provider.id = :providerId
        AND t.transactionType IS NOT NULL
        AND t.transactionDate >= :from AND t.transactionDate <= :to
        GROUP BY t.transactionType ORDER BY COUNT(t) DESC
    """)
    List<Object[]> getTransactionTypeStatsByProviderWithFilter(@Param("providerId") String providerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT t.client, COUNT(t), SUM(t.amount)
        FROM Transaction t WHERE t.provider.id = :providerId
        GROUP BY t.client ORDER BY SUM(t.amount) DESC LIMIT 10
    """)
    List<Object[]> getTopClientsByAmountForProvider(@Param("providerId") String providerId);

    @Query("""
        SELECT t.client, COUNT(t), SUM(t.amount)
        FROM Transaction t WHERE t.provider.id = :providerId
        AND t.transactionDate >= :from AND t.transactionDate <= :to
        GROUP BY t.client ORDER BY SUM(t.amount) DESC LIMIT 10
    """)
    List<Object[]> getTopClientsByAmountWithFilter(@Param("providerId") String providerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT t.client, COUNT(t), SUM(t.amount)
        FROM Transaction t WHERE t.provider.id = :providerId
        GROUP BY t.client ORDER BY COUNT(t) DESC LIMIT 10
    """)
    List<Object[]> getTopClientsByCountForProvider(@Param("providerId") String providerId);

    @Query("""
        SELECT t.client, COUNT(t), SUM(t.amount)
        FROM Transaction t WHERE t.provider.id = :providerId
        AND t.transactionDate >= :from AND t.transactionDate <= :to
        GROUP BY t.client ORDER BY COUNT(t) DESC LIMIT 10
    """)
    List<Object[]> getTopClientsByCountWithFilter(@Param("providerId") String providerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COUNT(sub.clientId) FROM (
            SELECT t.client.id AS clientId FROM Transaction t
            WHERE t.provider.id = :providerId
            GROUP BY t.client.id HAVING COUNT(t) > 1
        ) sub
    """)
    long countRecurringClients(@Param("providerId") String providerId);

    @Query("""
        SELECT COUNT(sub.clientId) FROM (
            SELECT t.client.id AS clientId FROM Transaction t
            WHERE t.provider.id = :providerId
            AND t.transactionDate >= :from AND t.transactionDate <= :to
            GROUP BY t.client.id HAVING COUNT(t) > 1
        ) sub
    """)
    long countRecurringClientsWithFilter(@Param("providerId") String providerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.provider.id = :providerId AND t.amount < 100")
    long countSmallTransactions(@Param("providerId") String providerId);

    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.provider.id = :providerId AND t.amount < 100
        AND t.transactionDate >= :from AND t.transactionDate <= :to
    """)
    long countSmallTransactionsWithFilter(@Param("providerId") String providerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.provider.id = :providerId AND t.amount >= 100 AND t.amount <= 1000")
    long countMediumTransactions(@Param("providerId") String providerId);

    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.provider.id = :providerId AND t.amount >= 100 AND t.amount <= 1000
        AND t.transactionDate >= :from AND t.transactionDate <= :to
    """)
    long countMediumTransactionsWithFilter(@Param("providerId") String providerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.provider.id = :providerId AND t.amount > 1000")
    long countLargeTransactions(@Param("providerId") String providerId);

    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.provider.id = :providerId AND t.amount > 1000
        AND t.transactionDate >= :from AND t.transactionDate <= :to
    """)
    long countLargeTransactionsWithFilter(@Param("providerId") String providerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT CAST(t.transactionDate AS date), COUNT(t), SUM(t.amount)
        FROM Transaction t WHERE t.provider.id = :providerId
        GROUP BY CAST(t.transactionDate AS date)
        ORDER BY CAST(t.transactionDate AS date)
    """)
    List<Object[]> getDailyStatsByProvider(@Param("providerId") String providerId);

    @Query("""
        SELECT MIN(t.transactionDate), MAX(t.transactionDate)
        FROM Transaction t WHERE t.provider.id = :providerId
    """)
    List<Object[]> getDateRangeByProvider(@Param("providerId") String providerId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.provider IS NOT NULL")
    long grandTotalCountNoFilter();

    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.provider IS NOT NULL
        AND t.transactionDate >= :from AND t.transactionDate <= :to
    """)
    long grandTotalCountWithFilter(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.provider IS NOT NULL")
    BigDecimal grandTotalAmountNoFilter();

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.provider IS NOT NULL
        AND t.transactionDate >= :from AND t.transactionDate <= :to
    """)
    BigDecimal grandTotalAmountWithFilter(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT t.provider.id, COUNT(t), COALESCE(SUM(t.amount), 0)
        FROM Transaction t WHERE t.provider IS NOT NULL
        GROUP BY t.provider.id
    """)
    List<Object[]> getStatsGroupedByProviderNoFilter();

    @Query("""
        SELECT t.provider.id, COUNT(t), COALESCE(SUM(t.amount), 0)
        FROM Transaction t WHERE t.provider IS NOT NULL
        AND t.transactionDate >= :from AND t.transactionDate <= :to
        GROUP BY t.provider.id
    """)
    List<Object[]> getStatsGroupedByProviderWithFilter(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT t.client, COUNT(t), SUM(t.amount)
        FROM Transaction t
        WHERE t.provider.id = :providerId
        GROUP BY t.client ORDER BY SUM(t.amount) DESC LIMIT 10
    """)
    List<Object[]> getTopClientsByProvider(@Param("providerId") String providerId);

    @Query("SELECT t.id, t.transactionDate, t.amount, tt.category, " +
            "FUNCTION('HOUR', t.transactionDate) as hour " +
            "FROM Transaction t JOIN t.transactionType tt " +
            "WHERE t.client.id = :clientId")
    List<Object[]> findRawFeaturesForClassification(@Param("clientId") String clientId);
}