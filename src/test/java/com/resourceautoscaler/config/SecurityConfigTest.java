package com.resourceautoscaler.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies JWT authentication and role-based authorization on the API. */
@SpringBootTest(properties = {
        "app.security.enabled=true",
        "app.security.jwt.secret=" + SecurityConfigTest.SECRET
})
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class SecurityConfigTest {

    static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long!!";

    @Autowired
    private MockMvc mockMvc;

    /** Verifies anonymous requests to protected endpoints are rejected. */
    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/metrics"))
                .andExpect(status().isUnauthorized());
    }

    /** Verifies an invalid signature is rejected. */
    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/metrics").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    /** Verifies a viewer role can read but cannot generate code. */
    @Test
    void viewerCanReadMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/metrics").header("Authorization", "Bearer " + token("VIEWER")))
                .andExpect(status().isOk());
    }

    /** Verifies a viewer role is forbidden from write endpoints. */
    @Test
    void viewerCannotGenerateCode() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header("Authorization", "Bearer " + token("VIEWER"))
                        .contentType("application/json")
                        .content("{\"resourceId\":\"autoscaler-busy\"}"))
                .andExpect(status().isForbidden());
    }

    /** Verifies an operator role can reach write endpoints. */
    @Test
    void operatorCanGenerateCode() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/generate")
                        .header("Authorization", "Bearer " + token("OPERATOR"))
                        .contentType("application/json")
                        .content("{\"resourceId\":\"autoscaler-busy\",\"currentMonthlyCostUsd\":0}"))
                .andExpect(status().is2xxSuccessful());
    }

    /** Verifies a read scope grants access without an explicit role. */
    @Test
    void readScopeCanReadMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/metrics").header("Authorization", "Bearer " + scopedToken("read")))
                .andExpect(status().isOk());
    }

    /** Verifies the health endpoint is public. */
    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private String token(String... roles) throws Exception {
        return sign(new JWTClaimsSet.Builder()
                .subject("test-user")
                .claim("roles", List.of(roles)));
    }

    private String scopedToken(String scope) throws Exception {
        return sign(new JWTClaimsSet.Builder()
                .subject("test-user")
                .claim("scope", scope));
    }

    private String sign(JWTClaimsSet.Builder builder) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                builder.expirationTime(Date.from(Instant.now().plusSeconds(300))).build());
        jwt.sign(new MACSigner(SECRET));
        return jwt.serialize();
    }
}
