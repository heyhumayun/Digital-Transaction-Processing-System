package com.banking.system.service;

import com.banking.system.dto.*;
import org.springframework.data.domain.Page;

public interface BankingService {
    UserResponse registerUser(UserRegistrationRequest request);
    AccountResponse createAccount(AccountCreationRequest request);
    AccountResponse getAccount(String accountNumber);
    TransactionResponse deposit(TransactionRequest request);
    TransactionResponse withdraw(TransactionRequest request);
    TransactionResponse transfer(TransferRequest request);
    Page<TransactionResponse> getTransactionHistory(String accountNumber, int page, int size);
}
