package com.erp.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full-stack API tests over real HTTP: Spring Security filter chain,
 * Flyway-migrated schema (H2/PostgreSQL mode) and the seeded demo users.
 *
 * The same scenarios also run against real PostgreSQL by
 * {@link PostgresMigrationTest} on machines/CI with Docker (Testcontainers).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErpApiIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void useApacheHttpClient() {
        // The JDK HttpURLConnection engine cannot handle 401 responses to
        // streamed POSTs ("cannot retry due to server authentication").
        rest.getRestTemplate()
                .setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    // ------------------------------------------------------------------ auth

    private String login(String email, String password) {
        ResponseEntity<String> res = rest.postForEntity("/api/auth/login",
                jsonBody(Map.of("email", email, "password", password)), String.class);
        assertEquals(HttpStatus.OK, res.getStatusCode(), "login failed: " + res.getBody());
        return read(res).get("accessToken").asText();
    }

    private HttpHeaders authHeader(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpEntity<Map<String, Object>> jsonBody(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(new java.util.LinkedHashMap<>(body), headers);
    }

    private JsonNode read(ResponseEntity<String> res) {
        try {
            return json.readTree(res.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Bad JSON: " + res.getBody(), e);
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    void login_rejectsWrongPassword_with401() {
        ResponseEntity<String> res = rest.postForEntity("/api/auth/login",
                jsonBody(Map.of("email", "admin@erp.com", "password", "nope")), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    @Test
    void register_createsEmployee_andRejectsDuplicates() {
        String email = "it-user-" + System.nanoTime() + "@erp.com";
        ResponseEntity<String> created = rest.postForEntity("/api/auth/register",
                jsonBody(Map.of("fullName", "IT User", "email", email, "password", "secret1")),
                String.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        ResponseEntity<String> duplicate = rest.postForEntity("/api/auth/register",
                jsonBody(Map.of("fullName", "IT User", "email", email, "password", "secret1")),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, duplicate.getStatusCode());
    }

    @Test
    void anonymous_getsBlocked_fromProtectedEndpoints() {
        ResponseEntity<String> res = rest.getForEntity("/api/products", String.class);
        assertTrue(res.getStatusCode() == HttpStatus.FORBIDDEN
                || res.getStatusCode() == HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rbac_employeeReadsButCannotWrite_andCannotSeeUsers() {
        String employee = login("employee@erp.com", "employee123");
        String admin = login("admin@erp.com", "admin123");

        // GET: allowed for every role
        assertEquals(HttpStatus.OK, rest.exchange("/api/products", HttpMethod.GET,
                new HttpEntity<>(authHeader(employee)), String.class).getStatusCode());

        // POST product: forbidden for EMPLOYEE
        Map<String, Object> product = Map.of(
                "sku", "RBAC-" + System.nanoTime(), "name", "X",
                "unitPrice", 1, "quantityInStock", 1, "reorderLevel", 1);
        assertEquals(HttpStatus.FORBIDDEN, rest.exchange("/api/products", HttpMethod.POST,
                new HttpEntity<>(product, authHeader(employee)), String.class).getStatusCode());

        // Same payload as MANAGER: allowed
        assertEquals(HttpStatus.CREATED, rest.exchange("/api/products", HttpMethod.POST,
                new HttpEntity<>(product, authHeader(login("manager@erp.com", "manager123"))),
                String.class).getStatusCode());

        // User directory: ADMIN only
        assertEquals(HttpStatus.FORBIDDEN, rest.exchange("/api/users", HttpMethod.GET,
                new HttpEntity<>(authHeader(employee)), String.class).getStatusCode());
        assertEquals(HttpStatus.OK, rest.exchange("/api/users", HttpMethod.GET,
                new HttpEntity<>(authHeader(admin)), String.class).getStatusCode());
    }

    @Test
    void salesOrderFlow_create_complete_deductsStock() {
        String manager = login("manager@erp.com", "manager123");
        String sku = "FLOW-" + System.nanoTime();

        // create product with 100 units
        ResponseEntity<String> productRes = rest.exchange("/api/products", HttpMethod.POST,
                new HttpEntity<>(Map.of("sku", sku, "name", "Flow product", "unitPrice", 5,
                        "quantityInStock", 100, "reorderLevel", 10),
                        authHeader(manager)), String.class);
        assertEquals(HttpStatus.CREATED, productRes.getStatusCode());
        long productId = read(productRes).get("id").asLong();
        long customerId = 1L;

        // create a 3-unit sales order (DRAFT)
        ResponseEntity<String> soRes = rest.exchange("/api/sales-orders", HttpMethod.POST,
                new HttpEntity<>(Map.of("partyId", customerId,
                                "items", new Object[]{Map.of("productId", productId, "quantity", 3)}),
                        authHeader(manager)), String.class);
        assertEquals(HttpStatus.CREATED, soRes.getStatusCode(), soRes.getBody());
        long soId = read(soRes).get("id").asLong();
        assertEquals("DRAFT", read(soRes).get("status").asText());

        // complete it -> stock 100 - 3 = 97
        ResponseEntity<String> completeRes = rest.exchange(
                "/api/sales-orders/" + soId + "/complete", HttpMethod.POST,
                new HttpEntity<>(authHeader(manager)), String.class);
        assertEquals(HttpStatus.OK, completeRes.getStatusCode(), completeRes.getBody());

        ResponseEntity<String> after = rest.exchange("/api/products/" + productId,
                HttpMethod.GET, new HttpEntity<>(authHeader(manager)), String.class);
        assertEquals(97, read(after).get("quantityInStock").asInt());

        // completed order is immutable
        assertEquals(HttpStatus.BAD_REQUEST, rest.exchange(
                "/api/sales-orders/" + soId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeader(manager)), String.class).getStatusCode());
    }

    @Test
    void salesOrder_complete_rejectsOversell_with400() {
        String manager = login("manager@erp.com", "manager123");

        ResponseEntity<String> productRes = rest.exchange("/api/products", HttpMethod.POST,
                new HttpEntity<>(Map.of("sku", "OVER-" + System.nanoTime(), "name", "Tiny stock",
                                "unitPrice", 2, "quantityInStock", 1, "reorderLevel", 0),
                        authHeader(manager)), String.class);
        long productId = read(productRes).get("id").asLong();

        ResponseEntity<String> soRes = rest.exchange("/api/sales-orders", HttpMethod.POST,
                new HttpEntity<>(Map.of("partyId", 1L,
                                "items", new Object[]{Map.of("productId", productId, "quantity", 999)}),
                        authHeader(manager)), String.class);
        long soId = read(soRes).get("id").asLong();

        ResponseEntity<String> res = rest.exchange("/api/sales-orders/" + soId + "/complete",
                HttpMethod.POST, new HttpEntity<>(authHeader(manager)), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(read(res).get("message").asText().contains("Insufficient stock"));
    }

    @Test
    void purchaseOrder_receive_addsStock() {
        String manager = login("manager@erp.com", "manager123");

        ResponseEntity<String> productRes = rest.exchange("/api/products", HttpMethod.POST,
                new HttpEntity<>(Map.of("sku", "PO-" + System.nanoTime(), "name", "Restock",
                                "unitPrice", 3, "quantityInStock", 5, "reorderLevel", 10),
                        authHeader(manager)), String.class);
        long productId = read(productRes).get("id").asLong();

        ResponseEntity<String> poRes = rest.exchange("/api/purchase-orders", HttpMethod.POST,
                new HttpEntity<>(Map.of("partyId", 1L,
                                "items", new Object[]{Map.of("productId", productId, "quantity", 30)}),
                        authHeader(manager)), String.class);
        assertEquals(HttpStatus.CREATED, poRes.getStatusCode(), poRes.getBody());
        long poId = read(poRes).get("id").asLong();

        rest.exchange("/api/purchase-orders/" + poId + "/receive", HttpMethod.POST,
                new HttpEntity<>(authHeader(manager)), String.class);

        ResponseEntity<String> after = rest.exchange("/api/products/" + productId,
                HttpMethod.GET, new HttpEntity<>(authHeader(manager)), String.class);
        assertEquals(35, read(after).get("quantityInStock").asInt()); // 5 + 30
    }

    @Test
    void refreshTokens_rotate_revokeOnLogout_andDetectReuse() {
        // login -> pair
        ResponseEntity<String> loginRes = rest.postForEntity("/api/auth/login",
                jsonBody(Map.of("email", "admin@erp.com", "password", "admin123")), String.class);
        JsonNode pair = read(loginRes);
        String access = pair.get("accessToken").asText();
        String refresh1 = pair.get("refreshToken").asText();

        // rotate -> new refresh, old one revoked
        ResponseEntity<String> rotateRes = rest.postForEntity("/api/auth/refresh",
                jsonBody(Map.of("refreshToken", refresh1)), String.class);
        assertEquals(HttpStatus.OK, rotateRes.getStatusCode());
        String refresh2 = read(rotateRes).get("refreshToken").asText();
        assertNotEquals(refresh1, refresh2);

        // reuse of the rotated token = theft -> whole family revoked
        assertEquals(HttpStatus.BAD_REQUEST, rest.postForEntity("/api/auth/refresh",
                jsonBody(Map.of("refreshToken", refresh1)), String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, rest.postForEntity("/api/auth/refresh",
                jsonBody(Map.of("refreshToken", refresh2)), String.class).getStatusCode());

        // logout makes the token unusable
        ResponseEntity<String> login2Res = rest.postForEntity("/api/auth/login",
                jsonBody(Map.of("email", "employee@erp.com", "password", "employee123")),
                String.class);
        String refresh3 = read(login2Res).get("refreshToken").asText();
        assertEquals(HttpStatus.NO_CONTENT, rest.postForEntity("/api/auth/logout",
                jsonBody(Map.of("refreshToken", refresh3)), String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, rest.postForEntity("/api/auth/refresh",
                jsonBody(Map.of("refreshToken", refresh3)), String.class).getStatusCode());

        // access token still validates for API calls until it expires on its own
        assertEquals(HttpStatus.OK, rest.exchange("/api/products", HttpMethod.GET,
                new HttpEntity<>(authHeader(access)), String.class).getStatusCode());
    }

    @Test
    void dataIntegrityViolation_returnsStructured409Conflict() {
        String admin = login("admin@erp.com", "admin123");
        // Customer 1 is seeded and has associated sales orders in demo data
        ResponseEntity<String> res = rest.exchange("/api/customers/1", HttpMethod.DELETE,
                new HttpEntity<>(authHeader(admin)), String.class);

        assertEquals(HttpStatus.CONFLICT, res.getStatusCode());
        JsonNode body = read(res);
        assertEquals(409, body.get("status").asInt());
        assertTrue(body.get("message").asText().contains("data integrity constraints"));
        assertFalse(body.toString().contains("org.postgresql"), "Should not expose SQL driver or schema internals");
        assertFalse(body.toString().contains("StackTrace"), "Should not expose stack trace");
    }

    @Test
    void lastAdminProtection_preventsDeleteAndDemoteViaApi() {
        String admin = login("admin@erp.com", "admin123");

        // Find admin user ID (alice admin has id 1)
        ResponseEntity<String> usersRes = rest.exchange("/api/users", HttpMethod.GET,
                new HttpEntity<>(authHeader(admin)), String.class);
        assertEquals(HttpStatus.OK, usersRes.getStatusCode());
        long adminId = 1L;

        // Attempt to delete the admin account -> should fail
        ResponseEntity<String> deleteRes = rest.exchange("/api/users/" + adminId, HttpMethod.DELETE,
                new HttpEntity<>(authHeader(admin)), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, deleteRes.getStatusCode());
        assertTrue(read(deleteRes).get("message").asText().contains("Cannot delete the last remaining ADMIN"));

        // Attempt to demote the admin account to EMPLOYEE -> should fail
        ResponseEntity<String> demoteRes = rest.exchange("/api/users/" + adminId + "/role", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("role", "EMPLOYEE"), authHeader(admin)), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, demoteRes.getStatusCode());
        assertTrue(read(demoteRes).get("message").asText().contains("Cannot demote the last remaining ADMIN"));
    }

    @Test
    void notifications_returnCorrectSummaryAndDraftOrders() {
        String manager = login("manager@erp.com", "manager123");
        ResponseEntity<String> res = rest.exchange("/api/notifications", HttpMethod.GET,
                new HttpEntity<>(authHeader(manager)), String.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        JsonNode body = read(res);
        assertTrue(body.has("totalAlerts"));
        assertTrue(body.has("lowStockCount"));
        assertTrue(body.has("pendingPurchasesCount"));
        assertTrue(body.has("pendingSalesCount"));
        assertTrue(body.has("items"));
    }

    @Test
    void auditLogs_recordOperationsAcrossEntities_andRestrictsEmployee() {
        String employee = login("employee@erp.com", "employee123");
        String admin = login("admin@erp.com", "admin123");

        // Employee cannot view audit logs (PreAuthorize hasAnyRole('ADMIN', 'MANAGER'))
        ResponseEntity<String> forbidden = rest.exchange("/api/audit-logs", HttpMethod.GET,
                new HttpEntity<>(authHeader(employee)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        // Admin can view audit logs
        ResponseEntity<String> ok = rest.exchange("/api/audit-logs", HttpMethod.GET,
                new HttpEntity<>(authHeader(admin)), String.class);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        JsonNode logs = read(ok).get("content");
        assertTrue(logs.isArray() && logs.size() > 0);
    }

    @Test
    void concurrentSalesOrders_preventNegativeStock() throws Exception {
        String manager = login("manager@erp.com", "manager123");

        // Create product with exactly 10 units
        String sku = "CONCUR-" + System.nanoTime();
        ResponseEntity<String> prodRes = rest.exchange("/api/products", HttpMethod.POST,
                new HttpEntity<>(Map.of("sku", sku, "name", "Concurrent Product", "unitPrice", 10,
                        "quantityInStock", 10, "reorderLevel", 2),
                        authHeader(manager)), String.class);
        assertEquals(HttpStatus.CREATED, prodRes.getStatusCode());
        long productId = read(prodRes).get("id").asLong();

        // Create two orders, each for 10 units
        ResponseEntity<String> o1Res = rest.exchange("/api/sales-orders", HttpMethod.POST,
                new HttpEntity<>(Map.of("partyId", 1L,
                        "items", new Object[]{Map.of("productId", productId, "quantity", 10)}),
                        authHeader(manager)), String.class);
        long o1Id = read(o1Res).get("id").asLong();

        ResponseEntity<String> o2Res = rest.exchange("/api/sales-orders", HttpMethod.POST,
                new HttpEntity<>(Map.of("partyId", 1L,
                        "items", new Object[]{Map.of("productId", productId, "quantity", 10)}),
                        authHeader(manager)), String.class);
        long o2Id = read(o2Res).get("id").asLong();

        // Run both completions concurrently
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<Integer> f1 = pool.submit(() -> {
            latch.await();
            return rest.exchange("/api/sales-orders/" + o1Id + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeader(manager)), String.class).getStatusCode().value();
        });

        Future<Integer> f2 = pool.submit(() -> {
            latch.await();
            return rest.exchange("/api/sales-orders/" + o2Id + "/complete", HttpMethod.POST,
                    new HttpEntity<>(authHeader(manager)), String.class).getStatusCode().value();
        });

        latch.countDown();
        int s1 = f1.get(15, TimeUnit.SECONDS);
        int s2 = f2.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        // Exactly one should succeed (200), the other must fail (409 Conflict or 400 Bad Request)
        boolean oneSucceeded = (s1 == 200 && s2 != 200) || (s2 == 200 && s1 != 200);
        assertTrue(oneSucceeded, "Exactly one concurrent order should succeed; got " + s1 + " and " + s2);

        // Verify remaining stock is 0, NEVER negative
        ResponseEntity<String> finalProd = rest.exchange("/api/products/" + productId, HttpMethod.GET,
                new HttpEntity<>(authHeader(manager)), String.class);
        int finalStock = read(finalProd).get("quantityInStock").asInt();
        assertEquals(0, finalStock, "Stock must be 0 and never drop below zero");
    }
}
