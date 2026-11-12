package com.banking.system.service;

import com.banking.system.dto.*;
import com.banking.system.exception.*;
import com.banking.system.model.*;
import com.banking.system.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Optional;

@Service
public class BankingServiceImpl implements BankingService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public BankingServiceImpl(UserRepository userRepository,
                              AccountRepository accountRepository,
                              TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public UserResponse registerUser(UserRegistrationRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email '" + request.getEmail() + "' is already registered");
        }

        User user = new User(request.getUsername(), request.getEmail(), request.getFullName());
        User savedUser = userRepository.save(user);

        return mapToUserResponse(savedUser);
    }

    @Override
    @Transactional
    public AccountResponse createAccount(AccountCreationRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + request.getUserId()));

        String accountNumber = generateUniqueAccountNumber();
        BigDecimal initialBalance = request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO;

        Account account = new Account(accountNumber, user, initialBalance);
        Account savedAccount = accountRepository.save(account);

        // If initial balance is greater than zero, log it as an initial deposit transaction
        if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            Transaction initialDeposit = new Transaction(
                    TransactionType.DEPOSIT,
                    initialBalance,
                    null,
                    savedAccount,
                    "Initial deposit on account creation"
            );
            transactionRepository.save(initialDeposit);
        }

        return mapToAccountResponse(savedAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with account number: " + accountNumber));
        return mapToAccountResponse(account);
    }

    @Override
    @Transactional
    public TransactionResponse deposit(TransactionRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Deposit amount must be greater than zero");
        }

        // Lock the account pessimistically to prevent concurrent write interference
        Account account = accountRepository.findByAccountNumberForUpdate(request.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with account number: " + request.getAccountNumber()));

        account.setBalance(account.getBalance().add(request.getAmount()));
        accountRepository.save(account);

        Transaction transaction = new Transaction(
                TransactionType.DEPOSIT,
                request.getAmount(),
                null,
                account,
                "Cash deposit"
        );
        Transaction savedTransaction = transactionRepository.save(transaction);

        return mapToTransactionResponse(savedTransaction);
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(TransactionRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Withdrawal amount must be greater than zero");
        }

        // Lock the account pessimistically to prevent concurrent write interference
        Account account = accountRepository.findByAccountNumberForUpdate(request.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with account number: " + request.getAccountNumber()));

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance. Current balance is: " + account.getBalance());
        }

        account.setBalance(account.getBalance().subtract(request.getAmount()));
        accountRepository.save(account);

        Transaction transaction = new Transaction(
                TransactionType.WITHDRAWAL,
                request.getAmount(),
                account,
                null,
                "Cash withdrawal"
        );
        Transaction savedTransaction = transactionRepository.save(transaction);

        return mapToTransactionResponse(savedTransaction);
    }

    @Override
    @Transactional
    public TransactionResponse transfer(TransferRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Transfer amount must be greater than zero");
        }

        String sourceAccNum = request.getSourceAccountNumber();
        String destAccNum = request.getDestinationAccountNumber();

        if (sourceAccNum.equals(destAccNum)) {
            throw new InvalidTransactionException("Source and destination accounts must be different");
        }

        // DEADLOCK PREVENTION: Lock accounts in alphabetical order of their account numbers.
        // This ensures that two concurrent transfers between the same two accounts in opposite directions
        // will attempt to lock the resources in the exact same sequence, avoiding circular wait deadlocks.
        Account sourceAccount;
        Account destAccount;

        if (sourceAccNum.compareTo(destAccNum) < 0) {
            sourceAccount = accountRepository.findByAccountNumberForUpdate(sourceAccNum)
                    .orElseThrow(() -> new ResourceNotFoundException("Source account not found: " + sourceAccNum));
            destAccount = accountRepository.findByAccountNumberForUpdate(destAccNum)
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + destAccNum));
        } else {
            destAccount = accountRepository.findByAccountNumberForUpdate(destAccNum)
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + destAccNum));
            sourceAccount = accountRepository.findByAccountNumberForUpdate(sourceAccNum)
                    .orElseThrow(() -> new ResourceNotFoundException("Source account not found: " + sourceAccNum));
        }

        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance for transfer. Current balance is: " + sourceAccount.getBalance());
        }

        sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.getAmount()));
        destAccount.setBalance(destAccount.getBalance().add(request.getAmount()));

        accountRepository.save(sourceAccount);
        accountRepository.save(destAccount);

        String description = request.getDescription() != null ? request.getDescription() : "Fund transfer";
        Transaction transaction = new Transaction(
                TransactionType.TRANSFER,
                request.getAmount(),
                sourceAccount,
                destAccount,
                description
        );
        Transaction savedTransaction = transactionRepository.save(transaction);

        return mapToTransactionResponse(savedTransaction);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(String accountNumber, int page, int size) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with account number: " + accountNumber));

        Pageable pageable = PageRequest.of(page, size);
        Page<Transaction> transactions = transactionRepository.findTransactionsByAccount(account, pageable);

        return transactions.map(this::mapToTransactionResponse);
    }

    // Helper Methods
    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            // Generate a random 10-digit number
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append(random.nextInt(10));
            }
            accountNumber = sb.toString();
        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getCreatedAt()
        );
    }

    private AccountResponse mapToAccountResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getUser().getId(),
                account.getUser().getUsername(),
                account.getCreatedAt()
        );
    }

    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        String source = transaction.getSourceAccount() != null ? transaction.getSourceAccount().getAccountNumber() : null;
        String dest = transaction.getDestinationAccount() != null ? transaction.getDestinationAccount().getAccountNumber() : null;

        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                source,
                dest,
                transaction.getDescription(),
                transaction.getTimestamp()
        );
    }
}
