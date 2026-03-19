# Intelligent Test Data Generator

> A Spring Boot application that generates realistic test data with business rule validation, edge case generation, and QA metadata tracking.

## 📋 Table of Contents
- [Quick Start](#quick-start)
- [How It Works](#how-it-works)
- [Features](#features)
- [Capabilities](#capabilities)
- [Limitations](#limitations)
- [API Usage](#api-usage)
- [Schema Definition](#schema-definition)
- [Docker Deployment](#docker-deployment)

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+

### Build & Run Locally
```bash
mvn clean package
java -jar target/intelligent-test-data-generator-1.0.0.jar
```
Then open **http://localhost:8080** in your browser

### Docker
```bash
docker-compose up --build
```
Access at **http://localhost:8080**

---

## 🗺️ How It Works

There are **two ways** to generate test data. Choose the path that fits your situation:

---

### Path A — Import from an OpenAPI / Swagger Spec
*Best if you already have an API spec file.*

| Step | Action | Notes |
|------|--------|-------|
| **1** | Open the app → click **"OpenAPI Import"** tab | Located next to the Generator tab |
| **2** | **Paste** your OpenAPI spec (YAML or JSON) **or click "Upload File"** | Supports OpenAPI 3.0 and 3.1 |
| **3** | Click **"Parse Spec"** | All `$ref` references are resolved automatically. A dropdown of operations appears. |
| **4** | **Select the operation** from the dropdown | e.g. `POST /productOrder – createProductOrder` |
| **5** | Choose **direction**: Request Body or Response Body | Then set the number of records to generate |
| **6** | Click **"Generate Schema"** | The OpenAPI schema is converted to generator format and the Generator tab opens automatically |
| **7** | Click **"🚀 Generate Data"** | Realistic test data is created matching your API's schema |
| **8** | **Copy or Download** the JSON output | Ready for Postman, Cucumber, JMeter, or any test tool |

**Example — TMF Product Ordering API:**
```
Spec pasted → Parse → Select "retrieveProductOrder" → Response → Count: 5 → Generate Schema → Generate Data
```
Output will include: UUIDs, datetime fields, enum values (state, action), nested arrays (relatedParty, productOrderItem), deeply nested objects (productSpecification).

---

### Path B — Build or Derive a Schema Manually
*Best if you have no spec file, or want full control over the schema.*

| Step | Action | Notes |
|------|--------|-------|
| **1** | Open the app → use the **"Generator"** tab | This is the default tab |
| **2** | **Load a template** (Simple Order, Complex Order, Users, Products) | Or clear and write your own schema from scratch |
| **3** | *(Optional)* **Paste a sample API response** in the "Sample Response" box | Any real JSON response from your system works |
| **4** | Click **"Sample → Schema"** | Field types are inferred from values and field names automatically |
| **5** | **Edit the schema** as needed | Add `computedFields`, `qaMode`, `seed` — see Schema Definition section |
| **6** | Click **"🚀 Generate Data"** | Output appears in the right panel |
| **7** | **Copy or Download** the JSON output | Use "Output → Schema" to refine the schema from the generated results |

**Example — User Batch:**
```json
{
  "count": 20,
  "schema": {
    "id": "uuid",
    "name": "fullName",
    "email": "email",
    "age": {"type": "number", "min": 18, "max": 65}
  }
}
```

---

### Both Paths — Optional Enhancements

Once you have a schema in the Generator tab, you can enrich it:

- **Add computed fields** (business rules):
  ```json
  "computedFields": {
    "tax": "price * 0.08",
    "shipping": "if(total > 100, 0, 15)",
    "order_total": "sum(items[].price * items[].quantity)"
  }
  ```

- **Enable edge case generation** (QA mode):
  ```json
  "qaMode": {
    "edgeCases": true,
    "edgeCaseRatio": 0.2,
    "include": ["minimum", "maximum", "boundary_minus", "boundary_plus"]
  }
  ```

- **Reproducible data** (seed):
  ```json
  "seed": 12345
  ```

---

## ✨ Features
- **Business Rule Validation**: sum(), if/else, arithmetic expressions
- **Edge Case Generation**: Automatic boundary, min, max value testing
- **50+ Data Types**: Names, emails, phones, UUIDs, products, companies via JavaFaker
- **Reproducible Data**: Seed support for consistent test data generation
- **QA Metadata**: Each record tagged with type (normal/edge_case) and validation details
- **Nested Arrays**: Support for complex array structures with min/max cardinality
- **Decimal Precision**: Configurable decimal places for financial data

## 🎯 Capabilities

### What You Can Do
1. **Generate realistic test data** - Names, emails, phone numbers, UUIDs, company names
2. **Create complex schemas** - Nested objects and arrays with constraints
3. **Apply business rules** - Calculate derived fields using:
   - Arithmetic: `subtotal * 0.08`
   - Conditionals: `if(subtotal > 100, 0, 15)`
   - Array aggregation: `sum(items[].price * items[].quantity)`
4. **Generate edge cases** - Automatically test boundary conditions (min, max values)
5. **Ensure data consistency** - QA metadata attached to every generated record
6. **Reproducible datasets** - Use seeds to regenerate identical data
7. **Batch processing** - Generate 1 to 100,000 records at once

## ⚠️ Limitations

### What You CANNOT Do
1. **No custom Faker hooks** - Cannot define custom data generation logic beyond built-in types
2. **Limited expression language** - Only supports basic arithmetic, conditionals, and simple sum operations
3. **No relational constraints** - Cannot enforce foreign key-like relationships between generated datasets
4. **No regex patterns** - Cannot generate data matching complex regex patterns
5. **No database integration** - Output is JSON only; no direct DB write capability
6. **No async/streaming** - All data generated synchronously and returned at once
7. **No scheduling** - Cannot schedule recurring generation jobs
8. **Single locale** - Currently hardcoded to `en_US`; no locale switching
9. **No validation rules** - Business rules only compute; they don't fail invalid inputs
10. **Edge cases limited** - Only 4 predefined edge case types (minimum, maximum, boundary_minus, boundary_plus)

## 🔌 API Usage

### POST /api/generate
Generate test data based on schema

**Request:**
```json
{
  "count": 10,
  "schema": {
    "order_id": "uuid",
    "customer_name": "fullName",
    "email": "email",
    "items": {
      "type": "array",
      "minItems": 1,
      "maxItems": 3,
      "schema": {
        "product": "productName",
        "price": {"type": "decimal", "min": 10, "max": 200, "decimals": 2},
        "quantity": {"type": "number", "min": 1, "max": 5}
      }
    }
  },
  "computedFields": {
    "subtotal": "sum(items[].price * items[].quantity)",
    "tax": "subtotal * 0.08",
    "shipping": "if(subtotal > 100, 0, 15)",
    "total": "subtotal + tax + shipping"
  },
  "qaMode": {
    "edgeCases": true,
    "edgeCaseRatio": 0.2,
    "include": ["minimum", "maximum"]
  },
  "seed": 12345
}
```

**Response:**
```json
[
  {
    "order_id": "550e8400-e29b-41d4-a716-446655440000",
    "customer_name": "John Doe",
    "email": "john.doe@example.com",
    "items": [...],
    "subtotal": 150.00,
    "tax": 12.00,
    "shipping": 0,
    "total": 162.00,
    "_qa_metadata": {
      "type": "normal",
      "business_rules_validated": true
    }
  },
  ...
]
```

### GET /api/health
Health check endpoint
```bash
curl http://localhost:8080/api/health
# Response: {"status":"UP","version":"1.0.0"}
```

## 📝 Schema Definition

### Simple Types (String)
```json
{
  "firstName": "firstname",
  "lastName": "lastname",
  "fullName": "fullName",
  "email": "email",
  "phone": "phone",
  "uuid": "uuid",
  "productName": "productname",
  "companyName": "companyname"
}
```

### Complex Types (Object)

**Number:**
```json
{
  "age": {"type": "number", "min": 18, "max": 65}
}
```

**Decimal:**
```json
{
  "price": {"type": "decimal", "min": 10.5, "max": 99.99, "decimals": 2}
}
```

**Array:**
```json
{
  "tags": {
    "type": "array",
    "minItems": 1,
    "maxItems": 5,
    "schema": {"tag": "productname"}
  }
}
```

### Computed Fields

**Arithmetic:**
```json
{
  "double_price": "price * 2",
  "discounted": "price * 0.9"
}
```

**Conditional:**
```json
{
  "shipping": "if(total > 100, 0, 10)"
}
```

**Sum Aggregation:**
```json
{
  "order_total": "sum(items[].price * items[].quantity)"
}
```

## 🐳 Docker Deployment

Build and run:
```bash
docker-compose up --build
```

Stop:
```bash
docker-compose down
```

The app runs on `http://localhost:8080` inside and outside the container.

## 📊 Use Cases
- **QA Testing**: Generate realistic test datasets with business rule validation
- **Load Testing**: Generate large volumes of consistent data
- **API Testing**: Pre-populate databases with test data before running tests
- **Data Pipeline Development**: Create diverse datasets for ETL testing
- **Documentation**: Generate example payloads for API documentation
- **Performance Testing**: Create reproducible large datasets (up to 100K records)

## 🛠️ Technology Stack
- Spring Boot 3.2.0
- Java 17
- JavaFaker 1.0.2
- Jakarta Validation
- Maven 3.9+

## 📄 License
MIT