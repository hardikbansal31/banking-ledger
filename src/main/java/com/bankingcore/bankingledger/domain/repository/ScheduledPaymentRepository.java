package com.bankingcore.bankingledger.domain.repository;

import com.bankingcore.bankingledger.domain.entity.Account;
import com.bankingcore.bankingledger.domain.entity.ScheduledPayment;
import com.bankingcore.bankingledger.domain.enums.ScheduledPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduledPaymentRepository extends JpaRepository<ScheduledPayment, UUID> {

    List<ScheduledPayment> findBySourceAccountAndStatus(
            Account sourceAccount, ScheduledPaymentStatus status);

    List<ScheduledPayment> findBySourceAccount(Account sourceAccount);

    Optional<ScheduledPayment> findByJobKey(String jobKey);
}