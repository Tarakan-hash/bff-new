package sb.broker.bff.web;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sb.broker.bff.support.ApiTestSupport;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.workflow.api.access.AccessOutcome;
import sb.broker.bff.workflow.api.access.CheckLkAccessInput;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Настоящая проверка JWT (без {@code jwt()} из spring-security-test): токен того вида, что выдаёт Keycloak
 * realm {@code lk} клиенту {@code lk-auth-backend}, подписан ключом из JWKS.
 *
 * <p>JWKS отдаётся по внутреннему адресу, а {@code iss} в токене — публичный: так bff настроен в кластере,
 * где Keycloak доступен по другому имени, чем браузеру.
 */
class KeycloakTokenTest extends ApiTestSupport {

    private static final String ISSUER = "https://keycloak.example/realms/lk";

    private static final RSAKey SIGNING_KEY = rsaKey(KeyUse.SIGNATURE, JWSAlgorithm.RS256);
    private static final HttpServer JWKS = jwksServer(new JWKSet(List.of(
            SIGNING_KEY.toPublicJWK(),
            // Keycloak публикует в certs и ключ шифрования: он не должен мешать выбору ключа подписи
            rsaKey(KeyUse.ENCRYPTION, JWEAlgorithm.RSA_OAEP).toPublicJWK())));

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/realms/lk/protocol/openid-connect/certs");
    }

    @AfterAll
    static void stopJwks() {
        JWKS.stop(0);
    }

    @BeforeEach
    void granted() {
        accessIs(AccessOutcome.GRANTED);
    }

    @Test
    void keycloakAccessTokenIsAccepted() throws Exception {
        mvc.perform(post("/api/v1/policies").header("Authorization", "Bearer " + token(SIGNING_KEY, claims -> { })))
                .andExpect(status().is2xxSuccessful());

        verify(workflows).execute(eq(LkWorkflows.CHECK_LK_ACCESS), eq(new CheckLkAccessInput(EMAIL, true)));
    }

    @Test
    void audienceListWithAccountIsAccepted() throws Exception {
        String token = token(SIGNING_KEY, claims -> claims.audience(List.of("broker-api", "account")));

        mvc.perform(post("/api/v1/policies").header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void otherIssuerIs401() throws Exception {
        String token = token(SIGNING_KEY, claims -> claims.issuer("http://keycloak.internal/realms/lk"));

        mvc.perform(post("/api/v1/policies").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutAccountAudienceIs401() throws Exception {
        String token = token(SIGNING_KEY, claims -> claims.audience("broker-api"));

        mvc.perform(post("/api/v1/policies").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIs401() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        String token = token(SIGNING_KEY, claims -> claims
                .issueTime(Date.from(past.minusSeconds(300)))
                .expirationTime(Date.from(past)));

        mvc.perform(post("/api/v1/policies").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedByUnknownKeyIs401() throws Exception {
        String token = token(rsaKey(KeyUse.SIGNATURE, JWSAlgorithm.RS256), claims -> { });

        mvc.perform(post("/api/v1/policies").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Access token Keycloak: заголовок {@code typ: JWT} с {@code kid}, {@code aud} строкой (единственная аудитория),
     * {@code typ: Bearer}, {@code azp}, роли и профиль пользователя (scope {@code openid profile email}).
     */
    private static String token(RSAKey key, Consumer<JWTClaimsSet.Builder> customizer) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .expirationTime(Date.from(now.plusSeconds(300)))
                .issueTime(Date.from(now))
                .claim("auth_time", now.getEpochSecond())
                .jwtID(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience("account")
                .subject(UUID.randomUUID().toString())
                .claim("typ", "Bearer")
                .claim("azp", "lk-auth-backend")
                .claim("nonce", UUID.randomUUID().toString())
                .claim("sid", UUID.randomUUID().toString())
                .claim("acr", "1")
                .claim("allowed-origins", List.of("https://lk.dev.broker-sb.io"))
                .claim("realm_access", Map.of("roles", List.of("offline_access", "uma_authorization", "default-roles-lk")))
                .claim("resource_access", Map.of("account", Map.of("roles", List.of("manage-account", "view-profile"))))
                .claim("scope", "openid profile email")
                .claim("email_verified", true)
                .claim("firstName", "Иван Иванов")
                .claim("preferred_username", EMAIL)
                .claim("given_name", "Иван")
                .claim("family_name", "Иванов")
                .claim("email", EMAIL);
        customizer.accept(claims);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey rsaKey(KeyUse use, com.nimbusds.jose.Algorithm algorithm) {
        try {
            return new RSAKeyGenerator(2048).keyUse(use).algorithm(algorithm).keyID(UUID.randomUUID().toString()).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer jwksServer(JWKSet jwks) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] body = jwks.toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/realms/lk/protocol/openid-connect/certs", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
