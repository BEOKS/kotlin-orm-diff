# Kotlin ORM Comparison Project

This is a multi-module project for comparing various ORM technologies (JOOQ, JPA, Exposed, QueryDSL) available in Kotlin.

> [한국어 버전](README.ko.md)

## Project Structure

```
kotlin-orm-diff/
├── domain/           # Pure Kotlin domain module
├── jooq/            # JOOQ implementation module
├── jpa/             # JPA/Hibernate implementation module
├── exposed/         # Exposed implementation module
└── querydsl/        # QueryDSL implementation module
```

## Technology Stack

- **Kotlin**: 2.0.21
- **JVM**: Java 17
- **Database**: H2 (for testing)
- **Build Tool**: Gradle (Kotlin DSL)

### ORM Libraries

- **JOOQ**: 3.19.1
- **Hibernate**: 6.4.1.Final
- **Exposed**: 0.46.0
- **QueryDSL**: 5.1.0

## Module Descriptions

### 1. Domain Module

A pure Kotlin domain module with no external library dependencies.

**Structure (DDD)**:
- `entity/`: Domain entities (Customer, Product, Order, OrderItem, Payment)
- `valueobject/`: Value objects (Money, *Id, Enum classes)
- `repository/`: Repository interfaces

**Entity Relationships**:
- Customer (1) ↔ (N) Order
- Order (1) ↔ (N) OrderItem
- Order (1) ↔ (1) Payment
- Product (1) ↔ (N) OrderItem

### 2. JOOQ Module

Implementation using type-safe SQL DSL.

**Features**:
- Type-safe queries using DSLContext
- Intuitive API close to SQL
- Support for complex joins and aggregate queries

### 3. JPA Module

Implementation using standard JPA and Hibernate.

**Features**:
- Using EntityManager and JPQL
- Object-oriented query writing
- Automatic DDL generation

### 4. Exposed Module

JetBrains' Kotlin-specific SQL library.

**Features**:
- Kotlin DSL style query writing
- Type safety and concise syntax
- Transaction management DSL

### 5. QueryDSL Module

Type-safe query builder based on JPA.

**Features**:
- Compile-time type checking
- Advantageous for writing complex dynamic queries
- Fluent API

## Repository Features

Each Repository implements the following features:

### CRUD Operations
- `save()`: Save entity
- `findById()`: Find by ID
- `findAll()`: Find all
- `update()`: Update
- `delete()`: Delete

### Complex Queries
- `findCustomersWithHighValueOrders()`: Find customers with high-value orders (join, grouping, having)
- `findOrdersWithPaymentAndItems()`: Join query for order-payment-product information
- `findProductsLowStockByCategory()`: Find low-stock products by category
- `calculateCustomerOrderStatistics()`: Customer order statistics (aggregate functions)
- `findUnpaidOrdersWithDetails()`: Find unpaid order details
- Other statistical and analytical queries

## Build and Test

### Full Build
```bash
./gradlew build
```

### Individual Module Tests
```bash
# JOOQ test
./gradlew :jooq:test

# JPA test
./gradlew :jpa:test

# Exposed test
./gradlew :exposed:test

# QueryDSL test
./gradlew :querydsl:test
```

### SQL Logging

When running tests for each module, you can view the actual SQL queries executed in the console.

- **JOOQ**: JOOQ logger settings in logback-test.xml
- **JPA/QueryDSL**: hibernate.show_sql enabled
- **Exposed**: addLogger() configuration

## Key Design Principles

1. **Purity**: The domain module has no external library dependencies
2. **Dependency Inversion**: Implementation modules depend on the domain module
3. **Type Safety**: Type-safe ID management using Value Objects
4. **Separation of Concerns**: Each ORM technology is separated into independent modules
5. **Test Validation**: Integration tests with H2 DB to verify actual behavior

## Comparison Points

This project allows you to compare:

- **Query Writing Style**: DSL vs JPQL vs Native SQL
- **Type Safety**: Compile-time vs Runtime checks
- **Learning Curve**: API complexity and documentation level
- **Performance**: Query optimization and execution plans
- **Maintainability**: Code readability and refactoring ease

## License

This project was created for learning and comparison purposes.
