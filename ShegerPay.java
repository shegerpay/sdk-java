package com.shegerpay.sdk;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * ShegerPay Java SDK
 * Official Java SDK for ShegerPay Payment Verification Gateway
 * 
 * Usage:
 *   ShegerPay client = new ShegerPay("sk_test_xxx");
 *   VerificationResult result = client.verify("FT123456", 100, "cbe", "My Shop");
 * 
 * @version 1.0.0
 */
public class ShegerPay {
    
    private static final String VERSION = "1.0.0";
    private static final String DEFAULT_BASE_URL = "https://api.shegerpay.com";
    
    private final String apiKey;
    private final String baseUrl;
    private final String mode;
    private final int timeout;
    
    /**
     * Create a new ShegerPay client
     * @param apiKey Your secret API key (sk_test_xxx or sk_live_xxx)
     */
    public ShegerPay(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, 30000);
    }
    
    /**
     * Create a new ShegerPay client with custom settings
     */
    public ShegerPay(String apiKey, String baseUrl, int timeout) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API key is required");
        }
        if (!apiKey.startsWith("sk_test_") && !apiKey.startsWith("sk_live_")) {
            throw new IllegalArgumentException("Invalid API key format");
        }
        
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.timeout = timeout;
        this.mode = apiKey.startsWith("sk_test_") ? "test" : "live";
    }
    
    /**
     * Verify a payment transaction
     */
    public VerificationResult verify(String transactionId, double amount) throws ShegerPayException {
        return verify(transactionId, amount, null, null, null);
    }
    
    /**
     * Verify a payment transaction with provider
     */
    public VerificationResult verify(String transactionId, double amount, String provider, String merchantName) 
            throws ShegerPayException {
        return verify(transactionId, amount, provider, merchantName, null);
    }

    public VerificationResult verify(String transactionId, double amount, String provider, String merchantName, String senderAccount)
            throws ShegerPayException {
        
        if (transactionId == null || transactionId.isEmpty()) {
            throw new ShegerPayException("Transaction ID is required");
        }
        
        if (provider == null || provider.isEmpty()) {
            provider = transactionId.toLowerCase().contains("cs.bankofabyssinia.com/slip/?trx=") ? "boa" : null;
        }
        if (provider == null || provider.isEmpty()) {
            throw new ShegerPayException("provider is required for ambiguous transaction references. Pass provider explicitly or use quickVerify().");
        }
        
        if (merchantName == null) {
            merchantName = "ShegerPay Verification";
        }
        
        Map<String, String> data = new HashMap<>();
        data.put("provider", provider);
        data.put("transaction_id", transactionId);
        data.put("amount", String.valueOf(amount));
        data.put("merchant_name", merchantName);
        if (senderAccount != null && !senderAccount.isEmpty()) {
            data.put("sender_account", senderAccount);
        }
        
        Map<String, Object> response = doRequest("POST", "/api/v1/verify", data);
        return new VerificationResult(response);
    }
    
    /**
     * Quick verification with auto-detected provider
     */
    public VerificationResult quickVerify(String transactionId, double amount) throws ShegerPayException {
        return quickVerify(transactionId, amount, null, null);
    }

    public VerificationResult quickVerify(String transactionId, double amount, String expectedProvider, String senderAccount) throws ShegerPayException {
        Map<String, String> data = new HashMap<>();
        data.put("transaction_id", transactionId);
        data.put("amount", String.valueOf(amount));
        if (expectedProvider != null && !expectedProvider.isEmpty()) {
            data.put("expected_provider", expectedProvider);
        }
        if (senderAccount != null && !senderAccount.isEmpty()) {
            data.put("sender_account", senderAccount);
        }
        
        Map<String, Object> response = doRequest("POST", "/api/v1/quick-verify", data);
        return new VerificationResult(response);
    }
    
    /**
     * Make HTTP request
     */
    private Map<String, Object> doRequest(String method, String path, Map<String, String> data) 
            throws ShegerPayException {
        try {
            URL url = new URL(baseUrl + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestProperty("X-API-Key", apiKey);
            conn.setRequestProperty("User-Agent", "ShegerPay-Java-SDK/" + VERSION);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            
            if ("POST".equals(method) && data != null) {
                conn.setDoOutput(true);
                StringBuilder postData = new StringBuilder();
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    if (postData.length() > 0) postData.append("&");
                    postData.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                    postData.append("=");
                    postData.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                }
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            
            int status = conn.getResponseCode();
            
            BufferedReader reader;
            if (status >= 400) {
                reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            }
            
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            if (status == 401) {
                throw new ShegerPayException("Invalid API key");
            }
            if (status == 400) {
                throw new ShegerPayException("Validation error: " + response.toString());
            }
            
            // Simple JSON parsing (in production, use a proper JSON library)
            return parseJson(response.toString());
            
        } catch (Exception e) {
            if (e instanceof ShegerPayException) throw (ShegerPayException) e;
            throw new ShegerPayException("Request failed: " + e.getMessage());
        }
    }
    
    /**
     * Simple JSON parser (for demo - use Jackson or Gson in production)
     */
    private Map<String, Object> parseJson(String json) {
        Map<String, Object> result = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
            String[] pairs = json.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("\"", "");
                    String value = kv[1].trim().replaceAll("\"", "");
                    
                    if ("true".equals(value)) {
                        result.put(key, true);
                    } else if ("false".equals(value)) {
                        result.put(key, false);
                    } else if (value.matches("-?\\d+(\\.\\d+)?")) {
                        result.put(key, Double.parseDouble(value));
                    } else if (!"null".equals(value)) {
                        result.put(key, value);
                    }
                }
            }
        }
        return result;
    }
    
    /**
     * Verify webhook signature
     */
    public static boolean verifyWebhookSignature(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String expected = "sha256=" + hexString.toString();
            return expected.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
    
    // --- Inner Classes ---
    
    public static class VerificationResult {
        public final boolean valid;
        public final String status;
        public final String provider;
        public final String transactionId;
        public final Double amount;
        public final String reason;
        public final String mode;
        
        public VerificationResult(Map<String, Object> data) {
            this.valid = (Boolean) data.getOrDefault("valid", false);
            this.status = (String) data.get("status");
            this.provider = (String) data.get("provider");
            this.transactionId = (String) data.get("transaction_id");
            this.amount = (Double) data.get("amount");
            this.reason = (String) data.get("reason");
            this.mode = (String) data.get("mode");
        }
        
        public boolean isValid() {
            return valid;
        }
    }
    
    public static class ShegerPayException extends Exception {
        public ShegerPayException(String message) {
            super(message);
        }
    }
}
