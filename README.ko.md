# Kotlin ORM 비교 프로젝트

이 프로젝트는 Kotlin에서 사용 가능한 여러 ORM 기술(JOOQ, JPA, Exposed, QueryDSL)을 비교하기 위한 멀티 모듈 프로젝트입니다.

> [English Version](README.md)

## 프로젝트 구조

```
kotlin-orm-diff/
├── domain/           # 순수 Kotlin 도메인 모듈
├── jooq/            # JOOQ 구현체 모듈
├── jpa/             # JPA/Hibernate 구현체 모듈
├── exposed/         # Exposed 구현체 모듈
└── querydsl/        # QueryDSL 구현체 모듈
```

## 기술 스택

- **Kotlin**: 2.0.21
- **JVM**: Java 17
- **Database**: H2 (테스트용)
- **Build Tool**: Gradle (Kotlin DSL)

### ORM 라이브러리

- **JOOQ**: 3.19.1
- **Hibernate**: 6.4.1.Final
- **Exposed**: 0.46.0
- **QueryDSL**: 5.1.0

## 모듈 설명

### 1. Domain 모듈

순수 Kotlin으로 작성된 도메인 모듈로, 외부 라이브러리 의존성이 없습니다.

**구조 (DDD)**:
- `entity/`: 도메인 엔티티 (Customer, Product, Order, OrderItem, Payment)
- `valueobject/`: 값 객체 (Money, *Id, Enum 클래스)
- `repository/`: Repository 인터페이스

**엔티티 관계**:
- Customer (1) ↔ (N) Order
- Order (1) ↔ (N) OrderItem
- Order (1) ↔ (1) Payment
- Product (1) ↔ (N) OrderItem

### 2. JOOQ 모듈

Type-safe SQL DSL을 사용한 구현체입니다.

**특징**:
- DSLContext를 활용한 타입 안전 쿼리
- SQL에 가까운 직관적인 API
- 복잡한 조인 및 집계 쿼리 지원

### 3. JPA 모듈

표준 JPA와 Hibernate를 사용한 구현체입니다.

**특징**:
- EntityManager와 JPQL 사용
- 객체 지향적인 쿼리 작성
- 자동 DDL 생성

### 4. Exposed 모듈

JetBrains의 Kotlin 전용 SQL 라이브러리입니다.

**특징**:
- Kotlin DSL 스타일의 쿼리 작성
- 타입 안전성과 간결한 문법
- 트랜잭션 관리 DSL

### 5. QueryDSL 모듈

JPA 기반의 타입 안전 쿼리 빌더입니다.

**특징**:
- 컴파일 타임 타입 체크
- 복잡한 동적 쿼리 작성에 유리
- Fluent API

## Repository 기능

각 Repository는 다음 기능을 구현합니다:

### CRUD 작업
- `save()`: 엔티티 저장
- `findById()`: ID로 조회
- `findAll()`: 전체 조회
- `update()`: 수정
- `delete()`: 삭제

### 복잡한 쿼리
- `findCustomersWithHighValueOrders()`: 고액 주문 고객 조회 (조인, 그룹핑, having)
- `findOrdersWithPaymentAndItems()`: 주문-결제-상품 정보 조인 조회
- `findProductsLowStockByCategory()`: 카테고리별 재고 부족 상품 조회
- `calculateCustomerOrderStatistics()`: 고객별 주문 통계 (집계 함수)
- `findUnpaidOrdersWithDetails()`: 미결제 주문 상세 정보 조회
- 기타 통계 및 분석 쿼리

## 빌드 및 테스트

### 전체 빌드
```bash
./gradlew build
```

### 개별 모듈 테스트
```bash
# JOOQ 테스트
./gradlew :jooq:test

# JPA 테스트
./gradlew :jpa:test

# Exposed 테스트
./gradlew :exposed:test

# QueryDSL 테스트
./gradlew :querydsl:test
```

### SQL 로깅

각 모듈의 테스트 실행 시 실제 수행된 SQL 쿼리를 콘솔에서 확인할 수 있습니다.

- **JOOQ**: logback-test.xml에서 JOOQ 로거 설정
- **JPA/QueryDSL**: hibernate.show_sql 활성화
- **Exposed**: addLogger() 설정

## 주요 설계 원칙

1. **순수성**: domain 모듈은 외부 라이브러리 의존성 없음
2. **의존성 역전**: 구현 모듈이 domain 모듈에 의존
3. **타입 안전성**: Value Object를 활용한 타입 안전한 ID 관리
4. **관심사 분리**: 각 ORM 기술은 독립적인 모듈로 분리
5. **테스트 검증**: H2 DB를 이용한 통합 테스트로 실제 동작 검증

## 비교 포인트

이 프로젝트를 통해 다음을 비교할 수 있습니다:

- **쿼리 작성 방식**: DSL vs JPQL vs 네이티브 SQL
- **타입 안전성**: 컴파일 타임 vs 런타임 체크
- **학습 곡선**: API 복잡도 및 문서화 수준
- **성능**: 쿼리 최적화 및 실행 계획
- **유지보수성**: 코드 가독성 및 리팩토링 용이성

## 라이선스

이 프로젝트는 학습 및 비교 목적으로 작성되었습니다.

