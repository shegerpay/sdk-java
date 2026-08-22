package com.shegerpay.sdk;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * @version 2.2.1
 */
public class ShegerPay {

    private static final String VERSION = "2.2.1";
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
     * Verify a payment from a receipt image/screenshot (or PDF).
     *
     * Works for ANY supported bank — the backend reads the receipt's QR code
     * (CBE, Telebirr, BOA…) or OCRs the reference and auto-detects the provider.
     * Just pass the image bytes; no need to know the bank or pre-extract the ref.
     */
    public VerificationResult verifyImage(byte[] screenshot, String provider, Double amount) throws ShegerPayException {
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        if (provider != null) fields.put("provider", provider);
        if (amount != null) fields.put("amount", String.valueOf(amount));
        Map<String, Object> response = doMultipartRequest("/api/v1/verify-image", fields, "screenshot", "receipt.png", screenshot);
        return new VerificationResult(response);
    }

    public VerificationResult verifyImage(byte[] screenshot) throws ShegerPayException {
        return verifyImage(screenshot, null, null);
    }

    /**
     * Create a shareable payment link
     */
    public Map<String, Object> createPaymentLink(String title, double amount, String currency) throws ShegerPayException {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("title", title);
        params.put("amount", String.valueOf(amount));
        params.put("currency", currency != null ? currency : "ETB");
        return requestMap("POST", "/api/v1/payment-links", params);
    }

    public Map<String, Object> createPaymentLink(String title, double amount) throws ShegerPayException {
        return createPaymentLink(title, amount, "ETB");
    }

    /**
     * List all payment links
     */
    public List<Map<String, Object>> listPaymentLinks() throws ShegerPayException {
        return requestList("GET", "/api/v1/payment-links", null);
    }

    /**
     * Delete a payment link
     */
    public void deletePaymentLink(String linkId) throws ShegerPayException {
        requestMap("DELETE", "/api/v1/payment-links/" + linkId, null);
    }

    private Map<String, Object> requestMap(String method, String path, Map<String, String> data) throws ShegerPayException {
        return doRequest(method, path, data);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> requestList(String method, String path, Map<String, String> data) throws ShegerPayException {
        // List endpoints return {"links": [...], "total": n}; unwrap the "links" key.
        try {
            Map<String, Object> response = doRequest(method, path, data);
            Object items = response.get("links");
            if (items instanceof List) {
                return (List<Map<String, Object>>) items;
            }
        } catch (Exception e) {
            if (e instanceof ShegerPayException) throw (ShegerPayException) e;
        }
        return new ArrayList<>();
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
            
            if (("POST".equals(method) || "PUT".equals(method)) && data != null) {
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
     * POST multipart/form-data with a single file part plus extra form fields.
     */
    private Map<String, Object> doMultipartRequest(String path, Map<String, String> fields,
            String fileField, String fileName, byte[] fileData) throws ShegerPayException {
        try {
            String boundary = "ShegerPayBoundary" + System.currentTimeMillis();
            String crlf = "\r\n";
            String dash = "--";
            URL url = new URL(baseUrl + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestProperty("X-API-Key", apiKey);
            conn.setRequestProperty("User-Agent", "ShegerPay-Java-SDK/" + VERSION);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                for (Map.Entry<String, String> entry : fields.entrySet()) {
                    os.write((dash + boundary + crlf).getBytes(StandardCharsets.UTF_8));
                    os.write(("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"" + crlf + crlf)
                            .getBytes(StandardCharsets.UTF_8));
                    os.write((entry.getValue() + crlf).getBytes(StandardCharsets.UTF_8));
                }
                os.write((dash + boundary + crlf).getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\"" + fileName + "\"" + crlf)
                        .getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Type: application/octet-stream" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
                os.write(fileData);
                os.write(crlf.getBytes(StandardCharsets.UTF_8));
                os.write((dash + boundary + dash + crlf).getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    status >= 400 ? conn.getErrorStream() : conn.getInputStream()));
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
            return parseJson(response.toString());

        } catch (Exception e) {
            if (e instanceof ShegerPayException) throw (ShegerPayException) e;
            throw new ShegerPayException("Request failed: " + e.getMessage());
        }
    }

    /**
     * Parse a JSON response body into the Map/List shapes the SDK works with.
     * Empty bodies yield an empty map; a non-object top-level value is exposed
     * under the "data" key.
     */
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        Object value = JsonParser.parse(json);
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return map;
        }
        Map<String, Object> wrapper = new HashMap<>();
        if (value != null) {
            wrapper.put("data", value);
        }
        return wrapper;
    }

    /**
     * Minimal recursive-descent JSON parser (package-private, no external deps).
     * Supports objects, arrays, strings with escapes (including \\uXXXX),
     * numbers, true/false/null. Objects parse to Map&lt;String,Object&gt;, arrays
     * to List&lt;Object&gt;, numbers to Long (integral) or Double (decimal/exponent).
     */
    static final class JsonParser {
        private final String src;
        private int pos;

        private JsonParser(String src) {
            this.src = src;
            this.pos = 0;
        }

        static Object parse(String json) {
            JsonParser parser = new JsonParser(json);
            parser.skipWhitespace();
            Object value = parser.parseValue();
            parser.skipWhitespace();
            if (parser.pos < parser.src.length()) {
                throw parser.error("Unexpected trailing characters");
            }
            return value;
        }

        private Object parseValue() {
            char c = peek();
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expectLiteral("true"); return Boolean.TRUE;
                case 'f': expectLiteral("false"); return Boolean.FALSE;
                case 'n': expectLiteral("null"); return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw error("Unexpected character '" + c + "'");
            }
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new HashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("Expected string key");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    return map;
                }
                throw error("Expected ',' or '}' in object");
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    return list;
                }
                throw error("Expected ',' or ']' in array");
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= src.length()) {
                    throw error("Unterminated string");
                }
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= src.length()) {
                        throw error("Unterminated escape sequence");
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > src.length()) {
                                throw error("Invalid unicode escape");
                            }
                            String hex = src.substring(pos, pos + 4);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw error("Invalid unicode escape \\u" + hex);
                            }
                            pos += 4;
                            break;
                        default:
                            throw error("Invalid escape character '\\" + esc + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Object parseNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (pos < src.length() && isDigit(src.charAt(pos))) {
                pos++;
            }
            boolean isDecimal = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                isDecimal = true;
                pos++;
                while (pos < src.length() && isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isDecimal = true;
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < src.length() && isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            String number = src.substring(start, pos);
            try {
                if (isDecimal) {
                    return Double.parseDouble(number);
                }
                return Long.parseLong(number);
            } catch (NumberFormatException e) {
                try {
                    return Double.parseDouble(number);
                } catch (NumberFormatException e2) {
                    throw error("Invalid number '" + number + "'");
                }
            }
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private char peek() {
            if (pos >= src.length()) {
                throw error("Unexpected end of input");
            }
            return src.charAt(pos);
        }

        private void expect(char c) {
            if (pos >= src.length() || src.charAt(pos) != c) {
                throw error("Expected '" + c + "'");
            }
            pos++;
        }

        private void expectLiteral(String literal) {
            if (!src.startsWith(literal, pos)) {
                throw error("Invalid literal");
            }
            pos += literal.length();
        }

        private void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(
                    "JSON parse error at position " + pos + ": " + message);
        }
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
            byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
            byte[] signatureBytes =
                    (signature == null ? "" : signature).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expectedBytes, signatureBytes);
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
            Object amountValue = data.get("amount");
            Double parsedAmount = null;
            if (amountValue instanceof Number) {
                parsedAmount = ((Number) amountValue).doubleValue();
            } else if (amountValue instanceof String) {
                try {
                    parsedAmount = Double.parseDouble((String) amountValue);
                } catch (NumberFormatException e) {
                    parsedAmount = null;
                }
            }
            this.amount = parsedAmount;
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
