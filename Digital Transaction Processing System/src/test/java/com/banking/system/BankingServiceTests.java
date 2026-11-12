package com.banking.system;

import com.banking.system.dto.*;
import com.banking.system.exception.InsufficientBalanceException;
import com.banking.system.exception.InvalidTransactionException;
import com.banking.system.exception.ResourceNotFoundException;
import com.banking.system.service.BankingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class BankingServiceTests {

    @Autowired
    private BankingService bankingService;

    @Test
    public void testUserRegistrationAndAccountCreation() {
        UserRegistrationRequest userReq = new UserRegistrationRequest(
                "john_doe", "john@example.com", "John Doe"
        );
        UserResponse userResp = bankingService.registerUser(userReq);
        assertThat(userResp.getId()).isNotNull();
        assertThat(userResp.getUsername()).isEqualTo("john_doe");

        AccountCreationRequest accReq = new AccountCreationRequest(userResp.getId(), new BigDecimal("500.00"));
        AccountResponse accResp = bankingService.createAccount(accReq);
        assertThat(accResp.getAccountNumber()).hasSize(10);
        assertThat(accResp.getBalance().compareTo(new BigDecimal("500.00"))).isEqualTo(0);
        assertThat(accResp.getUsername()).isEqualTo("john_doe");
    }

    @Test
    public void testDepositAndWithdrawal() {
        UserRegistrationRequest userReq = new UserRegistrationRequest(
                "jane_doe", "jane@example.com", "Jane Doe"
        );
        UserResponse userResp = bankingService.registerUser(userReq);
        AccountCreationRequest accReq = new AccountCreationRequest(userResp.getId(), BigDecimal.ZERO);
        AccountResponse accResp = bankingService.createAccount(accReq);
        String accNum = accResp.getAccountNumber();

        // Test Deposit
        TransactionResponse depositResp = bankingService.deposit(new TransactionRequest(accNum, new BigDecimal("100.00")));
        assertThat(depositResp.getSourceAccountNumber()).isNull();
        assertThat(depositResp.getDestinationAccountNumber()).isEqualTo(accNum);
        assertThat(depositResp.getAmount().compareTo(new BigDecimal("100.00"))).isEqualTo(0);

        AccountResponse checkAcc = bankingService.getAccount(accNum);
        assertThat(checkAcc.getBalance().compareTo(new BigDecimal("100.00"))).isEqualTo(0);

        // Test Withdrawal
        TransactionResponse withdrawResp = bankingService.withdraw(new TransactionRequest(accNum, new BigDecimal("40.00")));
        assertThat(withdrawResp.getSourceAccountNumber()).isEqualTo(accNum);
        assertThat(withdrawResp.getDestinationAccountNumber()).isNull();
        assertThat(withdrawResp.getAmount().compareTo(new BigDecimal("40.00"))).isEqualTo(0);

        checkAcc = bankingService.getAccount(accNum);
        assertThat(checkAcc.getBalance().compareTo(new BigDecimal("60.00"))).isEqualTo(0);
    }

    @Test
    public void testInsufficientFundsWithdrawalThrowsException() {
        UserRegistrationRequest userReq = new UserRegistrationRequest(
                "alice_smith", "alice@example.com", "Alice Smith"
        );
        UserResponse userResp = bankingService.registerUser(userReq);
        AccountCreationRequest accReq = new AccountCreationRequest(userResp.getId(), new BigDecimal("50.00"));
        AccountResponse accResp = bankingService.createAccount(accReq);
        String accNum = accResp.getAccountNumber();

        assertThrows(InsufficientBalanceException.class, () -> {
            bankingService.withdraw(new TransactionRequest(accNum, new BigDecimal("60.00")));
        });

        // Balance should remain unchanged
        AccountResponse checkAcc = bankingService.getAccount(accNum);
        assertThat(checkAcc.getBalance().compareTo(new BigDecimal("50.00"))).isEqualTo(0);
    }

    @Test
    public void testFundTransfer() {
        UserResponse user1 = bankingService.registerUser(new UserRegistrationRequest("user1", "user1@example.com", "User One"));
        UserResponse user2 = bankingService.registerUser(new UserRegistrationRequest("user2", "user2@example.com", "User Two"));

        AccountResponse acc1 = bankingService.createAccount(new AccountCreationRequest(user1.getId(), new BigDecimal("200.00")));
        AccountResponse acc2 = bankingService.createAccount(new AccountCreationRequest(user2.getId(), new BigDecimal("100.00")));

        TransactionResponse transferResp = bankingService.transfer(new TransferRequest(
                acc1.getAccountNumber(), acc2.getAccountNumber(), new BigDecimal("50.00"), "Lunch payment"
        ));

        assertThat(transferResp.getSourceAccountNumber()).isEqualTo(acc1.getAccountNumber());
        assertThat(transferResp.getDestinationAccountNumber()).isEqualTo(acc2.getAccountNumber());
        assertThat(transferResp.getAmount().compareTo(new BigDecimal("50.00"))).isEqualTo(0);

        assertThat(bankingService.getAccount(acc1.getAccountNumber()).getBalance().compareTo(new BigDecimal("150.00"))).isEqualTo(0);
        assertThat(bankingService.getAccount(acc2.getAccountNumber()).getBalance().compareTo(new BigDecimal("150.00"))).isEqualTo(0);

        // Check history
        Page<TransactionResponse> history = bankingService.getTransactionHistory(acc1.getAccountNumber(), 0, 10);
        assertThat(history.getContent()).hasSize(2); // Initial deposit (on creation since balance > 0) + transfer
    }

    @Test
    public void testConcurrentDeposits() throws InterruptedException, ExecutionException {
        UserResponse user = bankingService.registerUser(new UserRegistrationRequest("concurrent_user", "concurrent@example.com", "Concurrent User"));
        AccountResponse acc = bankingService.createAccount(new AccountCreationRequest(user.getId(), BigDecimal.ZERO));
        String accNum = acc.getAccountNumber();

        int numberOfThreads = 10;
        int depositsPerThread = 10;
        BigDecimal depositAmount = new BigDecimal("10.00");

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            tasks.add(() -> {
                for (int j = 0; j < depositsPerThread; j++) {
                    bankingService.deposit(new TransactionRequest(accNum, depositAmount));
                }
                return null;
            });
        }

        List<Future<Void>> futures = executorService.invokeAll(tasks);
        for (Future<Void> future : futures) {
            future.get(); // Throws execution exception if any thread failed
        }
        executorService.shutdown();

        BigDecimal expectedBalance = depositAmount.multiply(new BigDecimal(numberOfThreads * depositsPerThread));
        AccountResponse finalAcc = bankingService.getAccount(accNum);
        assertThat(finalAcc.getBalance().compareTo(expectedBalance)).isEqualTo(0);
    }

    @Test
    public void testConcurrentTransfers() throws InterruptedException, ExecutionException {
        UserResponse userA = bankingService.registerUser(new UserRegistrationRequest("userA", "userA@example.com", "User A"));
        UserResponse userB = bankingService.registerUser(new UserRegistrationRequest("userB", "userB@example.com", "User B"));

        AccountResponse accA = bankingService.createAccount(new AccountCreationRequest(userA.getId(), new BigDecimal("1000.00")));
        AccountResponse accB = bankingService.createAccount(new AccountCreationRequest(userB.getId(), new BigDecimal("1000.00")));

        String numA = accA.getAccountNumber();
        String numB = accB.getAccountNumber();

        int numberOfThreads = 8;
        int transfersPerThread = 10;
        BigDecimal transferAmount = new BigDecimal("5.00");

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        List<Callable<Void>> tasks = new ArrayList<>();

        // 4 threads transferring from A to B, 4 threads transferring from B to A
        for (int i = 0; i < numberOfThreads; i++) {
            final boolean directionAToB = (i % 2 == 0);
            tasks.add(() -> {
                for (int j = 0; j < transfersPerThread; j++) {
                    if (directionAToB) {
                        bankingService.transfer(new TransferRequest(numA, numB, transferAmount, "A to B"));
                    } else {
                        bankingService.transfer(new TransferRequest(numB, numA, transferAmount, "B to A"));
                    }
                }
                return null;
            });
        }

        List<Future<Void>> futures = executorService.invokeAll(tasks);
        for (Future<Void> future : futures) {
            future.get(); // If there's a deadlock or race, this will fail or hang
        }
        executorService.shutdown();

        // Since equal number of threads transferred back and forth equal amounts,
        // final balances must be exactly the initial balance.
        AccountResponse finalA = bankingService.getAccount(numA);
        AccountResponse finalB = bankingService.getAccount(numB);

        assertThat(finalA.getBalance().compareTo(new BigDecimal("1000.00"))).isEqualTo(0);
        assertThat(finalB.getBalance().compareTo(new BigDecimal("1000.00"))).isEqualTo(0);
    }
}
