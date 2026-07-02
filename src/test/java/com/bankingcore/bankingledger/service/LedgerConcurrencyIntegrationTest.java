package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.BaseIntegrationTest;
import com.bankingcore.bankingledger.domain.entity.Account;
import com.bankingcore.bankingledger.domain.entity.User;
import com.bankingcore.bankingledger.domain.enums.AccountStatus;
import com.bankingcore.bankingledger.domain.enums.AccountType;
import com.bankingcore.bankingledger.domain.enums.Role;
import com.bankingcore.bankingledger.domain.repository.AccountRepository;
import com.bankingcore.bankingledger.domain.repository.UserRepository;
import com.bankingcore.bankingledger.dto.request.TransactionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private Account sourceAccount;
    private Account destAccount;
    private User owner;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();

        owner = User.builder()
                .username("test_owner")
                .email("test@example.com")
                .passwordHash("hash")
                .firstName("Test")
                .lastName("Owner")
                .role(Role.USER)
                .build();
        userRepository.save(owner);

        sourceAccount = Account.builder()
                .accountNumber("ACC-CONCURRENT-SRC")
                .currency("USD")
                .accountType(AccountType.CHECKING)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("10000.00")) // Start with 10k
                .owner(owner)
                .build();

        destAccount = Account.builder()
                .accountNumber("ACC-CONCURRENT-DEST")
                .currency("USD")
                .accountType(AccountType.CHECKING)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("0.00"))
                .owner(owner)
                .build();

        accountRepository.save(sourceAccount);
        accountRepository.save(destAccount);
    }

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    // Guards against: Lost updates and double-spending when multiple threads debit concurrently
    @Test
    void transfer_shouldPreventLostUpdates_whenMultipleThreadsDebitSameAccount() throws InterruptedException {
        int numberOfThreads = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        BigDecimal transferAmount = new BigDecimal("100.00");
        AtomicInteger successfulTransfers = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final String idempotencyKey = UUID.randomUUID().toString();
            executorService.execute(() -> {
                try {
                    startLatch.await(); // wait for all threads to be ready

                    TransactionRequest.Transfer request = new TransactionRequest.Transfer();
                    request.setSourceAccountNumber(sourceAccount.getAccountNumber());
                    request.setDestinationAccountNumber(destAccount.getAccountNumber());
                    request.setAmount(transferAmount);
                    request.setCurrency("USD");
                    request.setIdempotencyKey(idempotencyKey);
                    
                    ledgerService.transfer(request, "test_owner");
                    successfulTransfers.incrementAndGet();
                } catch (Exception e) {
                    // System.out.println("Transfer failed: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release the hounds!
        startLatch.countDown();
        doneLatch.await(); // Wait for all threads to finish
        executorService.shutdown();

        Account finalSource = accountRepository.findByAccountNumber(sourceAccount.getAccountNumber()).orElseThrow();
        Account finalDest = accountRepository.findByAccountNumber(destAccount.getAccountNumber()).orElseThrow();

        // 20 transfers of 100.00 = 2000.00 base amount.
        // Plus fees: $100 falls into low tier (1.50%). 100 * 0.0150 = 1.50 fee per transfer.
        // Total debit per success = 101.50.
        int successes = successfulTransfers.get();
        BigDecimal expectedDebit = new BigDecimal("101.50").multiply(new BigDecimal(successes));
        BigDecimal expectedSourceBalance = new BigDecimal("10000.00").subtract(expectedDebit);
        
        BigDecimal expectedCredit = new BigDecimal("100.00").multiply(new BigDecimal(successes));
        BigDecimal expectedDestBalance = expectedCredit;

        assertThat(finalSource.getBalance()).isEqualByComparingTo(expectedSourceBalance);
        assertThat(finalDest.getBalance()).isEqualByComparingTo(expectedDestBalance);
    }
}
