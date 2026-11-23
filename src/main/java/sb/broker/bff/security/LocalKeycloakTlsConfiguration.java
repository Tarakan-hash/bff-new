package sb.broker.bff.security;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.LoggerFactory;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.JwkSetUriJwtDecoderBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@Profile("local-insecure-keycloak")
public class LocalKeycloakTlsConfiguration {

    @Bean
    JwkSetUriJwtDecoderBuilderCustomizer localKeycloakTlsCustomizer()
            throws GeneralSecurityException {

        var context = SSLContext.getInstance("TLS");

        context.init(null, new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(
                            X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(
                            X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        }, null);

        var factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(
                    HttpURLConnection connection, String method)
                    throws IOException {

                super.prepareConnection(connection, method);

                if (connection instanceof HttpsURLConnection https) {
                    https.setSSLSocketFactory(context.getSocketFactory());
                }
            }
        };

        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);

        LoggerFactory.getLogger(LocalKeycloakTlsConfiguration.class)
                .warn("LOCAL ONLY: TLS certificate trust validation "
                        + "is disabled for Keycloak JWKS requests");

        var restTemplate = new RestTemplate(factory);
        return builder -> builder.restOperations(restTemplate);
    }
}
