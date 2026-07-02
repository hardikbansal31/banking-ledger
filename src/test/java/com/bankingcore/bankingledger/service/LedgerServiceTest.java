package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.config.TransactionMetrics;
import com.bankingcore.bankingledger.domain.entity.Account;
import com.bankingcore.bankingledger.domain.entity.LedgerEntry;
import com.bankingcore.bankingledger.domain.entity.Transaction;
import com.bankingcore.bankingledger.domain.entity.User;
import com.bankingcore.bankingledger.domain.enums.AccountStatus;
import com.bankingcore.bankingledger.domain.enums.TransactionStatus;
import com.bankingcore.bankingledger.domain.repository.AccountRepository;
import com.bankingcore.bankingledger.domain.repository.LedgerEntryRepository;
import com.bankingcore.bankingledger.domain.repository.TransactionRepository;
import com.bankingcore.bankingledger.dto.request.TransactionRequest;
import com.bankingcore.bankingledger.dto.response.TransactionResponse;
import com.bankingcore.bankingledger.exception.CurrencyMismatchException;
import com.bankingcore.bankingledger.exception.InsufficientFundsException;
import com.bankingcore.bankingledger.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private DistributedLockService lockService;
    @Mock private TransactionStateMachine stateMachine;
    @Mock private FeeEngine feeEngine;
    @Mock private TransactionMetrics transactionMetrics;

    @InjectMocks
    private LedgerService ledgerService;

    private Account sourceAccount;
    private Account destAccount;
    private TransactionRequest.Transfer transferRequest;
    private User owner;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        owner = User.builder().username("alice").build();
        
        sourceAccount = Account.builder()
                .accountNumber("ACC-001")
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("1000.00"))
                .owner(owner)
                .build();

        destAccount = Account.builder()
                .accountNumber("ACC-002")
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("500.00"))
                .owner(User.builder().username("bob").build())
                .build();

        sourceAccount.setId(UUID.randomUUID());
        destAccount.setId(UUID.randomUUID());

        transferRequest = new TransactionRequest.Transfer();
        transferRequest.setSourceAccountNumber("ACC-001");
        transferRequest.setDestinationAccountNumber("ACC-002");
        transferRequest.setAmount(new BigDecimal("100.00"));
        transferRequest.setCurrency("USD");
        transferRequest.setIdempotencyKey("idemp-key-123");
        
        // Mock the lock service to just run the lambda directly
        lenient().when(lockService.executeWithLock(anyString(), any(Supplier.class)))
                 .thenAnswer(invocation -> {
                     Supplier<?> action = invocation.getArgument(1);
                     return action.get();
                 });
    }

    // Guards against: Basic transfer logic failure, ensuring double-entry balance correctness
    @Test
    void transfer_shouldDeductBalancesAndCreateLedgerEntries_whenValid() {
        when(transactionRepository.existsByIdempotencyKey("idemp-key-123")).thenReturn(false);
        when(accountRepository.findByAccountNumber("ACC-001")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumber("ACC-002")).thenReturn(Optional.of(destAccount));
        
        when(accountRepository.findByIdForUpdate(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdForUpdate(destAccount.getId())).thenReturn(Optional.of(destAccount));
        
        when(feeEngine.calculateFee(any(BigDecimal.class), anyString())).thenReturn(new BigDecimal("1.50"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArguments()[0]);

        TransactionResponse.Detail response = ledgerService.transfer(transferRequest, "alice");

        // 1000 - 100 (amount) - 1.50 (fee) = 898.50
        assertThat(sourceAccount.getBalance()).isEqualByComparingTo(new BigDecimal("898.50"));
        // 500 + 100 = 600.00
        assertThat(destAccount.getBalance()).isEqualByComparingTo(new BigDecimal("600.00"));

        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class)); // 1 debit, 1 credit
        verify(transactionRepository).save(any(Transaction.class));
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SETTLED.name());
    }

    // Guards against: Processing the same client request twice (double-charging)
    @Test
    void transfer_shouldReturnExistingTransaction_whenIdempotencyKeyMatches() {
        Transaction existingTx = Transaction.builder()
                .idempotencyKey("idemp-key-123")
                .status(TransactionStatus.SETTLED)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .sourceAccount(sourceAccount)
                .destinationAccount(destAccount)
                .build();
                
        when(transactionRepository.existsByIdempotencyKey("idemp-key-123")).thenReturn(true);
        when(transactionRepository.findByIdempotencyKey("idemp-key-123")).thenReturn(Optional.of(existingTx));

        TransactionResponse.Detail response = ledgerService.transfer(transferRequest, "alice");

        assertThat(response.getIdempotencyKey()).isEqualTo("idemp-key-123");
        verify(accountRepository, never()).findByAccountNumber(anyString());
    }

    // Guards against: Overdrawing an account
    @Test
    void transfer_shouldThrowInsufficientFundsException_whenBalanceTooLowToCoverAmountAndFee() {
        // Setup a transfer that costs 1000 + 15 fee = 1015, but balance is only 1000
        transferRequest.setAmount(new BigDecimal("1000.00"));
        
        when(transactionRepository.existsByIdempotencyKey("idemp-key-123")).thenReturn(false);
        when(accountRepository.findByAccountNumber("ACC-001")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumber("ACC-002")).thenReturn(Optional.of(destAccount));
        
        when(accountRepository.findByIdForUpdate(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdForUpdate(destAccount.getId())).thenReturn(Optional.of(destAccount));
        
        when(feeEngine.calculateFee(any(BigDecimal.class), anyString())).thenReturn(new BigDecimal("15.00"));

        assertThatThrownBy(() -> ledgerService.transfer(transferRequest, "alice"))
                .isInstanceOf(InsufficientFundsException.class);
                
        verify(transactionRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    // Guards against: Mixing incompatible currencies leading to ledger corruption
    @Test
    void transfer_shouldThrowCurrencyMismatchException_whenAccountsHaveDifferentCurrencies() {
        destAccount.setCurrency("EUR"); // Mismatch
        
        when(transactionRepository.existsByIdempotencyKey("idemp-key-123")).thenReturn(false);
        when(accountRepository.findByAccountNumber("ACC-001")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumber("ACC-002")).thenReturn(Optional.of(destAccount));
        
        when(accountRepository.findByIdForUpdate(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdForUpdate(destAccount.getId())).thenReturn(Optional.of(destAccount));

        assertThatThrownBy(() -> ledgerService.transfer(transferRequest, "alice"))
                .isInstanceOf(CurrencyMismatchException.class);
    }
    
    // Guards against: Operations on non-existent accounts
    @Test
    void transfer_shouldThrowResourceNotFoundException_whenAccountDoesNotExist() {
        when(transactionRepository.existsByIdempotencyKey("idemp-key-123")).thenReturn(false);
        when(accountRepository.findByAccountNumber("ACC-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.transfer(transferRequest, "alice"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
