<p align="center"><img src="logo.png" alt="ShegerPay" width="200" /></p>

# ShegerPay Java SDK

[![Version](https://img.shields.io/badge/version-2.2.0-blue)](https://search.maven.org/artifact/com.shegerpay/sdk)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

Official Java SDK for ShegerPay — verify Ethiopian bank payments (CBE, Telebirr, BOA, Awash).

## Install

**Maven:**
```xml
<dependency>
    <groupId>com.shegerpay</groupId>
    <artifactId>sdk</artifactId>
    <version>2.2.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'com.shegerpay:sdk:2.2.0'
```

## Quick Start

```java
import com.shegerpay.ShegerPay;
import java.util.*;

ShegerPay client = new ShegerPay("sk_live_YOUR_API_KEY");

// Verify a payment
Map<String, Object> result = client.verify("FT26062K7WMY", "cbe", 1000.0, null);
System.out.println(result.get("verified")); // true/false

// Verify without amount (lookup only)
Map<String, Object> result2 = client.verify("FT26062K7WMY", "telebirr", null, null);
System.out.println(result2.get("status"));

// Verify from receipt screenshot (base64)
String image = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get("receipt.png")));
Map<String, Object> imgResult = client.verifyImage(image, "cbe", null);
System.out.println(imgResult.get("verified"));

// Create payment link
Map<String, Object> params = new HashMap<>();
params.put("title", "Order #1234");
params.put("amount", 1500);
params.put("currency", "ETB");
Map<String, Object> link = client.createPaymentLink(params);
System.out.println(link.get("url"));
```

## Supported Providers
`cbe` · `telebirr` · `boa` · `awash` · `ebirr_kaafi` · `ebirr_coop`

## Requirements
- Java 8+


## Support
- 📚 Docs: https://shegerpay.com/docs
- 💬 Telegram: [@shegerpay_0](https://t.me/shegerpay_0)
- 📧 Email: support@shegerpay.com
