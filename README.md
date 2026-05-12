<p align="center"><img src="logo.png" alt="ShegerPay" width="200" /></p>

# ShegerPay Java SDK

Official Java SDK for [ShegerPay](https://shegerpay.com) — Ethiopian payment verification.

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>com.shegerpay</groupId>
  <artifactId>sdk</artifactId>
  <version>2.2.0</version>
</dependency>
```

Or with Gradle:

```groovy
implementation 'com.shegerpay:sdk:2.2.0'
```

## Quick Start

```java
import com.shegerpay.ShegerPay;
import com.shegerpay.VerifyResponse;

public class Main {
    public static void main(String[] args) {
        ShegerPay client = new ShegerPay("sk_live_...");

        VerifyResponse response = client.verify("txn_abc123");

        if (response.isSuccess()) {
            System.out.println("Payment verified: " + response.getTransactionId());
            System.out.println("Amount: " + response.getAmount());
        } else {
            System.out.println("Verification failed: " + response.getMessage());
        }
    }
}
```

## API Reference

### `new ShegerPay(apiKey)`

Creates a new ShegerPay client.

| Parameter | Type   | Description        |
|-----------|--------|--------------------|
| `apiKey`  | String | Your secret API key |

### `client.verify(transactionId)`

Verifies a payment transaction.

| Parameter       | Type   | Description              |
|-----------------|--------|--------------------------|
| `transactionId` | String | The transaction ID to verify |

Returns a `VerifyResponse` with:
- `isSuccess()` — whether the payment was successful
- `getTransactionId()` — the transaction ID
- `getAmount()` — the verified amount
- `getMessage()` — status message

## Requirements

- Java 8+
- Maven or Gradle

## License

MIT — see [LICENSE](LICENSE)
