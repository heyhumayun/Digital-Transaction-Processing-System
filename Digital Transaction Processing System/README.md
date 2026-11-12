# Digital Transaction Processing System (DTPS)

A Java-based financial transaction management backend built using **Java 21, Spring Boot, Spring Data JPA and SQL databases**. The system provides RESTful APIs for customer registration, account management, deposits, withdrawals and inter-account fund transfers.

The application focuses on financial data integrity, ACID transaction management, concurrency control and reliable transaction auditing.

## Key Features

- **Customer Management:** Register customers with unique usernames and email addresses.
- **Account Management:** Create and manage bank accounts with automatically generated account numbers.
- **Fund Deposits & Withdrawals:** Process financial transactions with input validation and balance checks.
- **Inter-Account Transfers:** Transfer funds between accounts with atomic transaction processing.
- **Transaction History:** Maintain and retrieve paginated financial transaction records.
- **Transaction Auditing:** Record transaction types, amounts, source and destination accounts, descriptions and timestamps.
- **Concurrency Control:** Implement pessimistic database locking to prevent race conditions and inconsistent account balances.
- **Deadlock Prevention:** Apply consistent resource-locking order when processing simultaneous transfers.
- **Financial Precision:** Use Java `BigDecimal` for monetary calculations to avoid floating-point precision errors.
- **Exception Handling:** Provide structured error responses for invalid transactions, insufficient balances and missing resources.
- **Multi-Database Support:** Configure H2, MySQL and PostgreSQL through Spring profiles.

## Technology Stack

| Category | Technologies |
|---|---|
| Language | Java 21 |
| Backend Framework | Spring Boot 3.4.1 |
| API Architecture | REST |
| Database | H2, MySQL, PostgreSQL |
| ORM | Spring Data JPA, Hibernate |
| Build Tool | Maven |
| Testing | JUnit 5, Spring Boot Test |
| Version Control | Git, GitHub |

## System Architecture

The application follows a layered architecture separating API handling, business logic and database operations.

```text
                  Client Applications
                          |
                          v
                   REST Controllers
                          |
                          v
                    Service Layer
                 (Banking Operations)
                          |
                          v
                  Repository Layer
                  (Spring Data JPA)
                          |
                          v
                    SQL Database
              (H2 / MySQL / PostgreSQL)
```

### Project Structure

```text
src/
├── main/
│   ├── java/com/banking/system/
│   │   ├── controller/
│   │   │   └── BankingController.java
│   │   ├── dto/
│   │   ├── exception/
│   │   ├── model/
│   │   ├── repository/
│   │   ├── service/
│   │   └── BankingSystemApplication.java
│   │
│   └── resources/
│       ├── application.properties
│       ├── application-mysql.properties
│       └── application-postgres.properties
│
└── test/
    └── java/com/banking/system/
        ├── BankingSystemApplicationTests.java
        └── BankingServiceTests.java
```

## Financial Transaction Integrity

A key design consideration is maintaining consistent account balances during concurrent financial operations.

### 1. ACID Transaction Management

Fund transfers use Spring's `@Transactional` mechanism to ensure atomic execution.

A transfer involves:
1. Validating the source and destination accounts.
2. Checking the available source balance.
3. Debiting the source account.
4. Crediting the destination account.
5. Recording the transaction.

If an operation fails, the database transaction is rolled back to prevent partial transfers.

### 2. Pessimistic Locking

The application uses database-level pessimistic write locks to prevent concurrent modifications from producing incorrect account balances.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
Optional<Account> findByAccountNumberForUpdate(
    String accountNumber
);
```

This ensures that competing transactions attempting to modify the same account wait for the existing transaction to complete.

### 3. Deadlock Prevention

To reduce circular-wait deadlocks during simultaneous transfers between two accounts, account locks are acquired in a consistent lexicographical order.

This ensures that concurrent transactions follow the same resource acquisition sequence.

### 4. Monetary Precision

Financial amounts are represented using Java's `BigDecimal` rather than binary floating-point types, avoiding common precision errors in monetary calculations.

## REST API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/users` | Register a customer |
| POST | `/api/accounts` | Create a bank account |
| GET | `/api/accounts/{accountNumber}` | Retrieve account details |
| POST | `/api/transactions/deposit` | Deposit funds |
| POST | `/api/transactions/withdraw` | Withdraw funds |
| POST | `/api/transactions/transfer` | Transfer funds |
| GET | `/api/transactions/history/{accountNumber}` | Retrieve transaction history |

### Example: Fund Transfer

**Request**

`POST /api/transactions/transfer`

```json
{
  "sourceAccountNumber": "5437761369",
  "destinationAccountNumber": "2870520490",
  "amount": 300.00,
  "description": "Account transfer"
}
```

**Response**

```json
{
  "id": 5,
  "type": "TRANSFER",
  "amount": 300.00,
  "sourceAccountNumber": "5437761369",
  "destinationAccountNumber": "2870520490",
  "description": "Account transfer",
  "timestamp": "2026-06-19T14:35:57"
}
```

## Getting Started

### Prerequisites

- Java 21 or later
- Git
- Maven (optional, as the project includes Maven Wrapper)

### 1. Clone the Repository

```bash
git clone https://github.com/heyhumayun/Digital-Transaction-Processing-System.git
cd Digital-Transaction-Processing-System
```

### 2. Run Automated Tests

```bash
./mvnw clean test
```

On Windows:

```powershell
.\mvnw.cmd clean test
```

### 3. Start the Application

```bash
./mvnw spring-boot:run
```

The application starts at:

`http://localhost:8080`

### 4. Access the H2 Database Console

Open:

`http://localhost:8080/h2-console`

Connection details:

| Property | Value |
|---|---|
| JDBC URL | `jdbc:h2:mem:bankingdb` |
| Username | `sa` |
| Password | `password` |
| Driver | `org.h2.Driver` |

### 5. Configure MySQL or PostgreSQL

The application supports persistent database configurations through Spring profiles.

Update the corresponding database credentials in:

- `application-mysql.properties`
- `application-postgres.properties`

Run with MySQL:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

Run with PostgreSQL:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Testing

The project includes automated tests using JUnit 5 and Spring Boot Test.

Testing covers core banking operations and service-level behavior, including:

- Customer and account operations.
- Deposit and withdrawal processing.
- Inter-account transfers.
- Transaction validation and failure scenarios.
- Transaction rollback behavior.
- Concurrent transaction processing and locking.

Run the test suite:

```bash
./mvnw clean test
```

## Design Objectives

The project demonstrates practical backend engineering concepts within a financial services context:

- RESTful application development.
- Layered software architecture.
- Relational database integration.
- ACID transaction management.
- Concurrent access control.
- Deadlock prevention.
- Financial data precision.
- Automated testing and exception handling.

---

**Disclaimer:** This project is an educational banking transaction application. It is not intended for processing real financial transactions or managing actual customer funds.
