# Module Specification: Finance

**Priority:** P4 — Operations
**Placement:** New backend module `backend/src/main/java/com/schoolsphere/finance/` (`finance.api`, `finance.internal`, `finance.web`)
**Flutter:** `app/lib/features/finance/`
**Status:** Not started (subscriptions/ module handles plan catalogue only; no fee/invoice/payment reconciliation yet)

---

## 1. Module Overview

Finance manages school financial operations and provides authorized financial reporting. It handles fee structures, invoices, payments, scholarships, supplier expenditure, and budget tracking.

**Critical control:** A payment is not marked confirmed simply because a user returns from a payment page. Confirmation requires explicit reconciliation against a payment provider notification or bank import, with an audit trail.

---

## 2. Package Structure (backend)

```
finance/
├── api/
│   ├── FeeStructureApi.java
│   ├── InvoiceApi.java
│   ├── PaymentApi.java
│   ├── ScholarshipApi.java
│   └── TransactionApi.java
├── internal/
│   ├── entity/
│   │   ├── FeeStructure.java
│   │   ├── FeeComponent.java
│   │   ├── Invoice.java
│   │   ├── InvoiceLine.java
│   │   ├── Payment.java
│   │   ├── Transaction.java
│   │   ├── Scholarship.java
│   │   ├── Waiver.java
│   │   └── SupplierInvoice.java
│   ├── repository/
│   │   ├── FeeStructureRepository.java
│   │   ├── InvoiceRepository.java
│   │   ├── PaymentRepository.java
│   │   └── TransactionRepository.java
│   ├── service/
│   │   ├── FeeService.java
│   │   ├── InvoiceService.java
│   │   ├── PaymentService.java
│   │   └── ReconciliationService.java
│   ├── payment/
│   │   ├── PaymentProvider.java      (SPI, mock in dev)
│   │   └── MockPaymentProvider.java
│   └── event/
│       ├── InvoiceIssuedEvent.java
│       └── PaymentConfirmedEvent.java
└── web/
    ├── FeeStructureController.java
    ├── InvoiceController.java
    ├── PaymentController.java
    ├── ScholarshipController.java
    └── FinanceReportsController.java
```

---

## 3. Database Schema

Flyway migration: `V25__finance.sql`

### Table: fee_structure

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Fee structure id |
| school_id | UUID | FK, NOT NULL, RLS | Owning school |
| name | varchar(200) | NOT NULL | Structure name (e.g. "Grade 7 Term 1") |
| grade_id | UUID | FK | academics.grade (nullable = all grades) |
| term_id | UUID | FK | academics.term |
| academic_year_id | UUID | FK, NOT NULL | Academic year |
| boarding_included | boolean | NOT NULL DEFAULT false | Includes boarding fees |
| transport_included | boolean | NOT NULL DEFAULT false | Includes transport fees |
| effective_from | date | NOT NULL | Start date |
| effective_to | date | | End date |
| is_active | boolean | NOT NULL DEFAULT true | Currently active |
| created_at / updated_at | | soft delete | TenantEntity columns |

### Table: fee_component

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Component id |
| fee_structure_id | UUID | FK, NOT NULL | Parent fee structure |
| name | varchar(200) | NOT NULL | Component name (tuition, exam fee, activity fee) |
| amount | numeric(12,2) | NOT NULL | Component amount |
| is_mandatory | boolean | NOT NULL DEFAULT true | Mandatory vs optional |
| created_at | timestamp | NOT NULL | TenantEntity column |

### Table: invoice

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Invoice id |
| school_id | UUID | FK, NOT NULL, RLS | Owning school |
| invoice_number | varchar(50) | NOT NULL, unique per school | Human-readable invoice number |
| student_id | UUID | FK, NOT NULL | people.student |
| fee_structure_id | UUID | FK | Reference to applied fee structure |
| total_amount | numeric(12,2) | NOT NULL | Invoice total |
| amount_paid | numeric(12,2) | NOT NULL DEFAULT 0.00 | Amount confirmed paid |
| status | varchar(20) | NOT NULL DEFAULT 'draft' | draft | issued | overdue | paid | partially_paid | cancelled |
| due_date | date | NOT NULL | Payment due date |
| issued_at | timestamp | | When issued |
| issued_by | UUID | FK | Staff who issued |
| version | int | NOT NULL DEFAULT 0 | JPA optimistic lock |
| created_at / updated_at | | soft delete | TenantEntity columns |

### Table: invoice_line

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Line id |
| invoice_id | UUID | FK, NOT NULL | Parent invoice |
| description | varchar(200) | NOT NULL | Line description |
| amount | numeric(12,2) | NOT NULL | Line amount |
| fee_component_id | UUID | FK | Reference to fee component |

### Table: payment

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Payment id |
| school_id | UUID | FK, NOT NULL, RLS | Owning school |
| invoice_id | UUID | FK, NOT NULL | Related invoice |
| student_id | UUID | FK, NOT NULL | Student |
| amount | numeric(12,2) | NOT NULL | Payment amount |
| payment_method | varchar(40) | NOT NULL | cash | bank_transfer | mobile_money | card | cheque |
| reference_number | varchar(100) | | Provider/bank reference |
| status | varchar(20) | NOT NULL DEFAULT 'pending' | pending | confirmed | failed | refunded |
| confirmed_at | timestamp | | When confirmed (not when initiated) |
| confirmed_by | UUID | FK | Staff who confirmed |
| reconciliation_notes | text | | Reconciliation notes |
| created_at | timestamp | NOT NULL | TenantEntity column |
| payment_date | date | NOT NULL | Date payment was made |

### Table: transaction

General ledger-style transaction log for audit.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Transaction id |
| school_id | UUID | FK, NOT NULL, RLS | Owning school |
| reference_type | varchar(40) | NOT NULL | invoice | payment | scholarship | waiver | supplier |
| reference_id | UUID | NOT NULL | Related record id |
| amount | numeric(12,2) | NOT NULL | Transaction amount |
| type | varchar(20) | NOT NULL | debit | credit |
| description | varchar(500) | NOT NULL | Description |
| transaction_date | date | NOT NULL | Transaction date |
| created_at | timestamp | NOT NULL | TenantEntity column |

### Table: scholarship

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Scholarship id |
| school_id | UUID | FK, NOT NULL, RLS | Owning school |
| student_id | UUID | FK, NOT NULL | Student |
| name | varchar(200) | NOT NULL | Scholarship name |
| amount | numeric(12,2) | NOT NULL | Award amount |
| type | varchar(40) | NOT NULL | merit | need | sports | other |
| academic_year_id | UUID | FK | Academic year |
| approved_by | UUID | FK | Approving staff |
| approved_at | timestamp | NOT NULL | Approval timestamp |
| created_at | timestamp | NOT NULL | TenantEntity column |

### Table: waiver

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Waiver id |
| school_id | UUID | FK, NOT NULL, RLS | Owning school |
| student_id | UUID | FK, NOT NULL | Student |
| invoice_id | UUID | FK | Related invoice |
| amount | numeric(12,2) | NOT NULL | Waived amount |
| reason | varchar(500) | NOT NULL | Waiver reason |
| approved_by | UUID | FK | Approving staff |
| approved_at | timestamp | NOT NULL | Approval timestamp |
| created_at | timestamp | NOT NULL | TenantEntity column |

### Table: supplier_invoice

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Supplier invoice id |
| school_id | UUID | FK, NOT NULL, RLS | Owning school |
| supplier_name | varchar(200) | NOT NULL | Supplier name |
| invoice_number | varchar(100) | NOT NULL | Supplier's invoice number |
| amount | numeric(12,2) | NOT NULL | Amount due |
| status | varchar(20) | NOT NULL DEFAULT 'unpaid' | unpaid | paid | disputed |
| due_date | date | NOT NULL | Payment due date |
| category | varchar(100) | | Expenditure category |
| created_at | timestamp | NOT NULL | TenantEntity column |

---

## 4. Permissions

Add to `PermissionCatalog`:

| Permission | Description | Roles |
|------------|-------------|-------|
| `finance.view` | View invoices, payments, financial summaries | owner, principal, admin, bursar, it_admin |
| `finance.manage` | Create/edit invoices, fee structures, record payments | owner, principal, admin, bursar |
| `finance.reconcile` | Confirm payments, reconcile against provider notifications | owner, bursar |
| `finance.refund` | Process refunds | owner, bursar |
| `scholarship.manage` | Create/manage scholarships and waivers | owner, principal, admin, bursar |

---

## 5. API Endpoints

Base path: `/api/v1/finance`

| Method | Path | Permission | Description |
|--------|------|-----------|-------------|
| GET | `/fee-structures` | `finance.view` | List fee structures |
| POST | `/fee-structures` | `finance.manage` | Create fee structure |
| GET | `/fee-structures/{id}` | `finance.view` | Get fee structure with components |
| PATCH | `/fee-structures/{id}` | `finance.manage` | Update fee structure |
| GET | `/invoices` | `finance.view` | List invoices (filter by student, status, date) |
| POST | `/invoices` | `finance.manage` | Generate invoice from fee structure |
| GET | `/invoices/{id}` | `finance.view` | Get invoice with lines |
| PATCH | `/invoices/{id}` | `finance.manage` | Update invoice (draft only) |
| POST | `/invoices/{id}/issue` | `finance.manage` | Issue invoice (sets status=issued, issued_at) |
| POST | `/invoices/{id}/cancel` | `finance.manage` | Cancel invoice (draft/issued, not paid) |
| GET | `/payments` | `finance.view` | List payments |
| POST | `/payments` | `finance.manage` | Record a payment (status=pending) |
| POST | `/payments/{id}/confirm` | `finance.reconcile` | Confirm payment after reconciliation |
| POST | `/payments/{id}/fail` | `finance.reconcile` | Mark payment failed |
| POST | `/payments/{id}/refund` | `finance.refund` | Refund a confirmed payment |
| GET | `/students/{studentId}/statement` | `finance.view` | Student statement (invoices + payments + balance) |
| GET | `/scholarships` | `finance.view` | List scholarships |
| POST | `/scholarships` | `scholarship.manage` | Create scholarship |
| GET | `/waivers` | `finance.view` | List waivers |
| POST | `/waivers` | `scholarship.manage` | Create waiver |
| GET | `/supplier-invoices` | `finance.view` | List supplier invoices |
| POST | `/supplier-invoices` | `finance.manage` | Record supplier invoice |
| GET | `/reports/collection-summary` | `finance.view` | Fee collection summary by grade/term |
| GET | `/reports/overdue-balances` | `finance.view` | Overdue balances report |
| GET | `/reports/expenditure` | `finance.view` | Supplier expenditure report |

---

## 6. Backend Services

### FeeService
- `generateInvoice(studentId, feeStructureId, actorId)` — calculates total from fee components, applies scholarships/waivers, creates invoice in draft status
- `applyScholarships(invoiceId, actorId)` — recalculates invoice with active scholarships

### InvoiceService
- `issueInvoice(invoiceId, actorId)` — validates due date in future, sets status=issued, emits InvoiceIssuedEvent (notifications to guardian)
- `cancelInvoice(invoiceId, actorId)` — only if no confirmed payments

### PaymentService
- `recordPayment(paymentDTO, actorId)` — creates payment with status=pending; does NOT confirm
- `confirmPayment(paymentId, confirmationRef, actorId)` — sets status=confirmed, confirmed_at, confirmed_by; creates credit transaction; emits PaymentConfirmedEvent
- `failPayment(paymentId, reason, actorId)` — sets status=failed

### ReconciliationService
- `reconcileProviderNotification(providerNotification, actorId)` — matches external payment notification (mobile money/bank/card) to pending payment by reference_number; confirms or fails
- `importBankStatement(file, actorId)` — parses bank/CSV import, matches payments by reference

### PaymentProvider (SPI)
```java
public interface PaymentProvider {
    String initiatePayment(PaymentInitiationRequest request);
    PaymentNotification parseNotification(Map<String, String> rawNotification);
    boolean verifySignature(Map<String, String> notification);
}
```
- `MockPaymentProvider` in dev — simulates success/failure
- Production implementations injected per school (mobile money, card gateway, bank)

### Events
- `InvoiceIssuedEvent` — notifications module alerts guardians/parents of new invoice
- `PaymentConfirmedEvent` — updates invoice status, notifications of payment received

---

## 7. Flutter Frontend

### Screens (`app/lib/features/finance/`)

**FinanceDashboardScreen** (`finance_dashboard_screen.dart`)
- Role-aware: bursar/finance staff see school-wide collections; parents see own children's statements
- Cards: total collected, outstanding invoices, overdue count, pending reconciliations
- Quick actions: issue invoice, record payment, reconcile

**InvoiceListScreen** (`invoice_list_screen.dart`)
- List invoices with status chips (draft/issued/paid/overdue/cancelled)
- Filter by student, status, date range, grade
- Bulk issue action

**InvoiceDetailScreen** (`invoice_detail_screen.dart`)
- Invoice lines, fee components applied, scholarships/waivers
- Payment history
- "Record Payment" / "Issue" / "Cancel" actions (permission-gated)
- Download receipt/statement (PDF)

**PaymentScreen** (`payment_screen.dart`)
- Form: invoice, amount, payment method, reference number, payment date
- Provider integration: initiate online payment (mock in dev)
- Confirmation status display (pending → confirmed after reconciliation)

**StudentStatementScreen** (`student_statement_screen.dart`)
- Parent/guardian view: invoices, payments, balance due for their children
- Fee due dates, payment confirmations
- Download statement PDF

**ScholarshipWaiverScreen** (`scholarship_waiver_screen.dart`)
- Manage scholarships and waivers per student
- Approval workflow

**SupplierInvoiceScreen** (`supplier_invoice_screen.dart`)
- Record and track supplier invoices/expenditure
- Mark as paid/disputed

### Providers (`finance_providers.dart`)
- `invoicesProvider` — FutureProvider with filters
- `paymentsProvider` — StreamProvider for payment status updates
- `feeStructuresProvider` — FutureProvider
- `studentStatementProvider` — FutureProvider per student (parent-scoped)
- `reconciliationQueueProvider` — Notifier for pending payments awaiting confirmation

### Widgets
- `InvoiceStatusChip` — color-coded status
- `BalanceDueBadge` — outstanding amount with overdue warning
- `PaymentMethodSelector` — cash/bank/mobile/card/cheque
- `ReconciliationIndicator` — pending vs confirmed visual state

---

## 8. User Journeys

### Journey 1: Bursar issues term fees
1. Bursar opens FinanceDashboard, clicks "Generate Invoices"
2. Selects grade, term, fee structure; system previews invoices for all students
3. Applies active scholarships/waivers automatically
4. Clicks "Issue All" — invoices created with status=issued, InvoiceIssuedEvent emitted
5. Guardians receive notifications; invoices visible in their StudentStatementScreen

### Journey 2: Parent pays school fees
1. Parent opens StudentStatementScreen, sees overdue invoice
2. Taps "Pay", selects payment method (mobile money/card)
3. System initiates payment via PaymentProvider (mock in dev)
4. Payment created with status=pending; parent redirected to provider
5. Provider sends notification; ReconciliationService matches reference_number
6. Bursar (or auto) confirms payment → status=confirmed, confirmed_at set
7. Invoice status updated to paid/partially_paid; parent notified

### Journey 3: Bursar reconciles bank statement
1. Bursar uploads bank statement CSV
2. ReconciliationService parses, matches transactions to pending payments by reference
3. Unmatched transactions flagged for manual review
4. Matched payments confirmed in bulk with audit trail

### Journey 4: Scholarship awarded
1. Principal creates scholarship for student (merit-based)
2. System applies scholarship to upcoming invoices automatically
3. Invoice total reduced; scholarship recorded with approver and audit trail

---

## 9. Integration Points

- **People/Students:** student_id references people.student; invoices tied to student enrollment
- **Academics:** fee_structure links to grade, term, academic_year
- **Notifications:** InvoiceIssuedEvent and PaymentConfirmedEvent trigger in-app/email/SMS to guardians
- **Boarding/Canteen/Transport:** approved charges from those modules can be added as fee components or separate invoice lines (finance feeds)
- **Subscriptions:** school-level subscription plan (existing module) is separate from per-student fee invoices
- **Audit Log:** All invoice creation, payment confirmation, refunds, scholarships, waivers audited with actor and before/after amounts
- **Reports:** Finance report endpoints feed Reports & Analytics module

---

## 10. Non-Functional Requirements

| Concern | Requirement |
|---------|-------------|
| Tenant isolation | RLS on all tables via school_id; `@TenantScoped` services |
| Payment confirmation | Payment never auto-confirmed on return from payment page; explicit reconcile step required; confirmed_at != created_at |
| Idempotency | Payment reference_number + invoice_id uniqueness; provider notification idempotency |
| Audit trail | Transaction ledger append-only; no direct balance updates without transaction row |
| Financial precision | All monetary fields numeric(12,2); rounding rules documented; no floating-point for money |
| Offline sync | Payment recording supports offline create (sync entity `payment`); confirmation requires online reconciliation |
| Accessibility | Currency formatting with locale, high-contrast status chips, keyboard-navigable forms |
| Security | `finance.reconcile` and `finance.refund` restricted to bursar/owner; segregation of duties (recorder vs confirmer) |
| Performance | Student statement queries indexed on student_id + date; collection summary aggregated |
