## Project Structure (what we'll build)
```
banking-ledger/
├── docker-compose.yml
├── .env.example
├── pom.xml
└── src/main/
    ├── java/com/bankingcore/ledger/
    │   ├── BankingLedgerApplication.java
    │   ├── config/
    │   │   ├── SecurityConfig.java
    │   │   ├── RedissonConfig.java
    │   │   ├── WebClientConfig.java
    │   │   └── QuartzConfig.java
    │   ├── security/
    │   │   ├── JwtService.java
    │   │   ├── JwtAuthFilter.java
    │   │   └── UserDetailsServiceImpl.java
    │   ├── domain/
    │   │   ├── entity/
    │   │   │   ├── User.java
    │   │   │   ├── Account.java
    │   │   │   ├── Transaction.java
    │   │   │   ├── LedgerEntry.java
    │   │   │   └── ScheduledPayment.java
    │   │   └── enums/
    │   │       ├── TransactionStatus.java
    │   │       ├── EntryType.java (DEBIT/CREDIT)
    │   │       └── Currency.java
    │   ├── repository/
    │   │   ├── UserRepository.java
    │   │   ├── AccountRepository.java
    │   │   ├── TransactionRepository.java
    │   │   └── LedgerEntryRepository.java
    │   ├── service/
    │   │   ├── AccountService.java
    │   │   ├── TransactionService.java
    │   │   ├── LedgerService.java
    │   │   ├── FeeEngineService.java
    │   │   ├── ExchangeRateService.java
    │   │   ├── StateMachineService.java
    │   │   └── ScheduledPaymentService.java
    │   ├── controller/
    │   │   ├── AuthController.java
    │   │   ├── AccountController.java
    │   │   └── TransactionController.java
    │   ├── dto/
    │   │   ├── request/ ...
    │   │   └── response/ ...
    │   └── exception/
    │       ├── GlobalExceptionHandler.java
    │       └── (domain exceptions)
    └── resources/
        ├── application.yml
        └── application-dev.yml
