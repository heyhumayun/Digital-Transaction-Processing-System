# How to Run the Banking Transaction System 🚀

This document provides step-by-step instructions on how to compile, test, run, and interact with the Mini Banking Backend System.

---

## 📋 Prerequisites
- **Java**: Ensure you have **Java 21** or later installed on your system. You can verify your version by running:
  ```bash
  java -version
  ```
- **Port 8080**: Make sure port `8080` is free, as the application runs on this port by default.

---

## ⚡ Quick Start: Running with H2 In-Memory Database (No Setup Required)

By default, the application runs in a `dev` configuration, which spins up a fast, local, in-memory H2 database. You do not need to install or configure MySQL or PostgreSQL to use this mode.

1. Open your terminal (PowerShell, Command Prompt, or Bash) in the project root directory.
2. Run the start command:
   - **On Windows (PowerShell or CMD):**
     ```powershell
     .\mvnw.cmd spring-boot:run
     ```
   - **On macOS/Linux:**
     ```bash
     ./mvnw spring-boot:run
     ```
3. Wait for the terminal to print:
   ```
   [main] com.banking.system.BankingSystemApplication  : Started BankingSystemApplication in X.XXX seconds
   ```
   The backend server is now running at `http://localhost:8080`.

---

## 🛢️ Accessing the H2 Web Console

While the application is running, you can connect to the in-memory database to view and query tables:

1. Open your web browser and go to: **`http://localhost:8080/h2-console`**
2. Configure the login details:
   - **Driver Class**: `org.h2.Driver`
   - **JDBC URL**: `jdbc:h2:mem:bankingdb`
   - **User Name**: `sa`
   - **Password**: `password`
3. Click **Connect**. You can now view tables (`USERS`, `ACCOUNTS`, `TRANSACTIONS`) and execute SQL queries directly.

---

## 🧪 Running Automated Unit & Concurrency Tests

To run the complete test suite (validating accounts, deposits, withdrawals, fund transfers, transactional rollbacks, and concurrent transaction locking safety):

- **On Windows:**
  ```powershell
  .\mvnw.cmd clean test
  ```
- **On macOS/Linux:**
  ```bash
  ./mvnw clean test
  ```

---

## 🔌 Running with MySQL or PostgreSQL Profiles

To use a persistent MySQL or PostgreSQL instance:

1. Open the profile configuration file of your choice:
   - MySQL configuration: [`src/main/resources/application-mysql.properties`](file:///c:/Users/jaive/Desktop/Banking%20Transaction%20System/src/main/resources/application-mysql.properties)
   - PostgreSQL configuration: [`src/main/resources/application-postgres.properties`](file:///c:/Users/jaive/Desktop/Banking%20Transaction%20System/src/main/resources/application-postgres.properties)
2. Update the credentials (`spring.datasource.username`, `spring.datasource.password`, and the connection string URL database name) to match your local setup.
3. Start the application with the chosen profile activated:
   - **To run MySQL:**
     - **Windows (PowerShell/CMD):**
       ```powershell
       .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql"
       ```
     - **macOS/Linux:**
       ```bash
       ./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
       ```
   - **To run PostgreSQL:**
     - **Windows (PowerShell/CMD):**
       ```powershell
       .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgres"
       ```
     - **macOS/Linux:**
       ```bash
       ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
       ```

---

## 🧪 Quick PowerShell Verification Script
You can test the APIs manually. Open a separate PowerShell window while the server is running and run this script to test the core features:

```powershell
# 1. Register a customer
$userReq = @{ username = "clark_kent"; email = "clark@dailyplanet.com"; fullName = "Clark Kent" } | ConvertTo-Json
$user = Invoke-RestMethod -Uri "http://localhost:8080/api/users" -Method Post -Body $userReq -ContentType "application/json"
Write-Host "Created User ID: $($user.id)"

# 2. Open an account with 1000.00 initial balance
$accReq = @{ userId = $user.id; initialBalance = 1000.00 } | ConvertTo-Json
$acc = Invoke-RestMethod -Uri "http://localhost:8080/api/accounts" -Method Post -Body $accReq -ContentType "application/json"
Write-Host "Opened Account Number: $($acc.accountNumber) with balance $($acc.balance)"

# 3. Deposit 500.00
$depositReq = @{ accountNumber = $acc.accountNumber; amount = 500.00 } | ConvertTo-Json
$dep = Invoke-RestMethod -Uri "http://localhost:8080/api/transactions/deposit" -Method Post -Body $depositReq -ContentType "application/json"
Write-Host "New Balance after Deposit: 1500.00"

# 4. Withdraw 200.00
$withdrawReq = @{ accountNumber = $acc.accountNumber; amount = 200.00 } | ConvertTo-Json
$wit = Invoke-RestMethod -Uri "http://localhost:8080/api/transactions/withdraw" -Method Post -Body $withdrawReq -ContentType "application/json"
Write-Host "New Balance after Withdrawal: 1300.00"

# 5. Get Transaction History
$hist = Invoke-RestMethod -Uri "http://localhost:8080/api/transactions/history/$($acc.accountNumber)" -Method Get
$hist.content | Format-Table id, type, amount, description, timestamp
```
