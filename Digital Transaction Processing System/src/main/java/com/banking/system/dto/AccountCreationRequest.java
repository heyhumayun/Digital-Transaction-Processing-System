package com.banking.system.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class AccountCreationRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @DecimalMin(value = "0.00", message = "Initial balance must be zero or positive")
    private BigDecimal initialBalance;

    public AccountCreationRequest() {
    }

    public AccountCreationRequest(Long userId, BigDecimal initialBalance) {
        this.userId = userId;
        this.initialBalance = initialBalance;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }
}
