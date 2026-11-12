# Mini Banking Backend System 🏦

A robust, enterprise-grade RESTful API backend for a Mini Banking System built with **Java 21**, **Spring Boot 3.4**, and **Spring Data JPA**.

This application implements core banking features—including user registration, account management, deposits, withdrawals, fund transfers, and transaction auditing—while enforcing strict **ACID transactional consistency** and **concurrency safety** matching financial domain standards.

---

## 🛠️ Features & Highlights

- **User Account Management**: Create customers and associate them with unique 10-digit bank accounts.
- **Deposit & Withdraw**: Standard banking operations validating constraints (e.g. positive values, account existence).
- **Fund Transfer**: Safely move funds between accounts with atomic transactional rollbacks if any step fails.
- **Transaction Audit Log**: Automatically logs all banking operations (type, amount, source/destination, description, and timestamp).
- **Concurrency & Concurrency Safeguards**:
  - **Pessimistic Write Locking (`SELECT ... FOR UPDATE`)** to prevent race conditions during concurrent modifications.
  - **Lexicographical Resource Locking** (alphabetical sorting of account numbers) to prevent circular-wait database deadlocks.
  - **High-Precision Money Handling** using `BigDecimal` to eliminate floating-point rounding errors.
- **Unified Error Handling**: Structured JSON error payloads for field-level validations and custom domain errors.
- **Multi-Database Support**: Profile-based configuration for in-memory **H2** (development), **MySQL**, or **PostgreSQL** (production).

---

## 💻 Tech Stack

- **Core Framework**: Java 21 / 25, Spring Boot 3.4.1 (Web, JPA, Validation)
- **Database / ORM**: Spring Data JPA, Hibernate, H2 Database (Default), MySQL Driver, PostgreSQL Driver
- **Testing**: JUnit 5, AssertJ, Spring Boot Test (Integration & Concurrency)
- **Build Tool**: Maven

---

## 📁 Project Structure

```
Banking Transaction System/
├── pom.xml                               # Maven Dependencies
├── mvnw / mvnw.cmd                       # Maven Wrapper scripts
├── README.md                             # Project Documentation
├── SDLC.md                               # Software Development Life Cycle Document
└── src/
    ├── main/
    │   ├── java/com/banking/system/
    │   │   ├── BankingSystemApplication.java   # App Entrypoint
    │   │   ├── controller/
    │   │   │   └── BankingController.java      # REST Endpoints
    │   │   ├── dto/                            # API Requests/Responses
    │   │   │   ├── AccountCreationRequest.java
    │   │   │   ├── AccountResponse.java
    │   │   │   ├── TransactionRequest.java
    │   │   │   ├── TransactionResponse.java
    │   │   │   ├── TransferRequest.java
    │   │   │   ├── UserRegistrationRequest.java
    │   │   │   └── UserResponse.java
    │   │   ├── exception/                      # Global Exception Handlers
    │   │   │   ├── DuplicateResourceException.java
    │   │   │   ├── ErrorResponse.java
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   ├── InsufficientBalanceException.java
    │   │   │   ├── InvalidTransactionException.java
    │   │   │   └── ResourceNotFoundException.java
    │   │   ├── model/                          # Database Entities
    │   │   │   ├── Account.java
    │   │   │   ├── Transaction.java
    │   │   │   ├── TransactionType.java        # Enum (DEPOSIT, WITHDRAWAL, TRANSFER)
    │   │   │   └── User.java
    │   │   ├── repository/                     # Database Access Interfaces
    │   │   │   ├── AccountRepository.java
    │   │   │   ├── TransactionRepository.java
    │   │   │   └── UserRepository.java
    │   │   └── service/                        # Business Logic Implementation
    │   │       ├── BankingService.java
    │   │       └── BankingServiceImpl.java
    │   └── resources/
    │       ├── application.properties          # Default Properties (H2 config)
    │       ├── application-mysql.properties    # MySQL Profile settings
    │       └── application-postgres.properties # PostgreSQL Profile settings
    └── test/
        └── java/com/banking/system/
            ├── BankingSystemApplicationTests.java # Context Load checks
            └── BankingServiceTests.java           # Integration & Concurrency Tests
```

---

## 🔒 Financial Safety: Concurrency & Lock Design

### 1. The Race Condition Problem (Double Spending)
In high-throughput bank backends, if User A initiates a transfer and a deposit concurrently, two separate server threads will query A's current balance, adjust it, and write it back. If they overlap, one update will overwrite the other, resulting in incorrect balances (Lost Update anomaly).

### 2. The Solution: Pessimistic Write Locking
We declare a database-level write lock when retrieving an account for updates:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
Optional<Account> findByAccountNumberForUpdate(String accountNumber);
```
This forces Hibernate to execute `SELECT ... FOR UPDATE` in SQL. Any secondary thread trying to read A's balance for modification will block until the first thread's transaction commits.

### 3. The Deadlock Problem (Circular Locks)
If User A transfers money to User B, and User B transfers to User A concurrently:
- Thread 1 locks Account A, waiting to lock Account B.
- Thread 2 locks Account B, waiting to lock Account A.
Neither thread can proceed, causing a database deadlock.

### 4. The Solution: Alphabetical Sorting Lock Order
In `BankingServiceImpl.java`, we always lock accounts in alphabetical order of their account numbers:
```java
if (sourceAccNum.compareTo(destAccNum) < 0) {
    sourceAccount = accountRepository.findByAccountNumberForUpdate(sourceAccNum);
    destAccount = accountRepository.findByAccountNumberForUpdate(destAccNum);
} else {
    destAccount = accountRepository.findByAccountNumberForUpdate(destAccNum);
    sourceAccount = accountRepository.findByAccountNumberForUpdate(sourceAccNum);
}
```
Because both threads will now lock the lexicographically lower account number first, one thread will acquire the lock while the second thread blocks *before* locking its first resource, preventing deadlocks entirely.

---

## 🔌 API Documentation & Examples

### 1. Register a User
- **URL**: `POST /api/users`
- **Payload**:
  ```json
  {
    "username": "peter_parker",
    "email": "peter@dailybugle.com",
    "fullName": "Peter Parker"
  }
  ```
- **Response** (`201 Created`):
  ```json
  {
    "id": 1,
    "username": "peter_parker",
    "email": "peter@dailybugle.com",
    "fullName": "Peter Parker",
    "createdAt": "2026-06-19T14:35:57"
  }
  ```

### 2. Create a Bank Account
- **URL**: `POST /api/accounts`
- **Payload**:
  ```json
  {
    "userId": 1,
    "initialBalance": 1500.00
  }
  ```
- **Response** (`201 Created`):
  ```json
  {
    "id": 1,
    "accountNumber": "5437761369",
    "balance": 1500.00,
    "userId": 1,
    "username": "peter_parker",
    "createdAt": "2026-06-19T14:35:57"
  }
  ```

### 3. Check Account Balance
- **URL**: `GET /api/accounts/{accountNumber}`
- **Response** (`200 OK`):
  ```json
  {
    "id": 1,
    "accountNumber": "5437761369",
    "balance": 1500.00,
    "userId": 1,
    "username": "peter_parker",
    "createdAt": "2026-06-19T14:35:57"
  }
  ```

### 4. Deposit Funds
- **URL**: `POST /api/transactions/deposit`
- **Payload**:
  ```json
  {
    "accountNumber": "5437761369",
    "amount": 250.00
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "id": 2,
    "type": "DEPOSIT",
    "amount": 250.00,
    "sourceAccountNumber": null,
    "destinationAccountNumber": "5437761369",
    "description": "Cash deposit",
    "timestamp": "2026-06-19T14:35:57"
  }
  ```

### 5. Withdraw Funds
- **URL**: `POST /api/transactions/withdraw`
- **Payload**:
  ```json
  {
    "accountNumber": "5437761369",
    "amount": 100.00
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "id": 3,
    "type": "WITHDRAWAL",
    "amount": 100.00,
    "sourceAccountNumber": "5437761369",
    "destinationAccountNumber": null,
    "description": "Cash withdrawal",
    "timestamp": "2026-06-19T14:35:57"
  }
  ```

### 6. Transfer Funds Between Accounts
- **URL**: `POST /api/transactions/transfer`
- **Payload**:
  ```json
  {
    "sourceAccountNumber": "5437761369",
    "destinationAccountNumber": "2870520490",
    "amount": 300.00,
    "description": "Rent contribution"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "id": 5,
    "type": "TRANSFER",
    "amount": 300.00,
    "sourceAccountNumber": "5437761369",
    "destinationAccountNumber": "2870520490",
    "description": "Rent contribution",
    "timestamp": "2026-06-19T14:35:57"
  }
  ```

### 7. Retrieve Transaction History (Paginated)
- **URL**: `GET /api/transactions/history/{accountNumber}?page=0&size=10`
- **Response** (`200 OK`):
  ```json
  {
    "content": [
      {
        "id": 5,
        "type": "TRANSFER",
        "amount": 300.00,
        "sourceAccountNumber": "5437761369",
        "destinationAccountNumber": "2870520490",
        "description": "Rent contribution",
        "timestamp": "2026-06-19T14:35:57"
      },
      {
        "id": 3,
        "type": "WITHDRAWAL",
        "amount": 100.00,
        "sourceAccountNumber": "5437761369",
        "destinationAccountNumber": null,
        "description": "Cash withdrawal",
        "timestamp": "2026-06-19T14:35:57"
      }
    ],
    "pageable": { ... },
    "totalPages": 1,
    "totalElements": 4,
    "size": 10,
    "number": 0
  }
  ```

---

## 🚀 How to Run & Verify

### 1. Prerequisites
- **Java**: Java 21 or later installed.
- Ensure your environment configuration allows launching network applications on port `8080`.

### 2. Build & Run Tests
To verify all integrations, input validations, error mappings, and multi-threaded concurrency locking, execute:
```bash
./mvnw clean test
```

### 3. Run the Server
By default, the application runs on port `8080` with an in-memory H2 database:
```bash
./mvnw spring-boot:run
```
- Access the H2 Database Console at: `http://localhost:8080/h2-console`
  - **JDBC URL**: `jdbc:h2:mem:bankingdb`
  - **Username**: `sa`
  - **Password**: `password`

### 4. Swapping Database Profiles
To run the server with a persistent database, configure the connection settings in `application-mysql.properties` or `application-postgres.properties`, and run with:
```bash
# For MySQL
./mvnw spring-boot:run -Dspring.boot.run.arguments="--spring.profiles.active=mysql"

# For PostgreSQL
./mvnw spring-boot:run -Dspring.boot.run.arguments="--spring.profiles.active=postgres"
```
