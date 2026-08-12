package support;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * A TestRestTemplate pointed at a random-port @SpringBootTest instance, using the modern
 * java.net.http.HttpClient-backed request factory instead of the default legacy
 * HttpURLConnection-based one. Without this, a POST that gets back a 401 (e.g.
 * AuthControllerTest's wrong-password case) throws HttpRetryException("cannot retry due to
 * server authentication, in streaming mode") instead of just returning the 401 response --
 * HttpURLConnection unconditionally assumes any 401 might need an installed java.net.Authenticator
 * retry and can't rewind a request body it already started streaming. JdkClientHttpRequestFactory
 * doesn't share that legacy behavior.
 */
public final class TestRestTemplates {

    private TestRestTemplates() {
    }

    public static TestRestTemplate create(int port) {
        RestTemplateBuilder builder = new RestTemplateBuilder()
                .rootUri("http://localhost:" + port)
                .requestFactory(() -> new JdkClientHttpRequestFactory(java.net.http.HttpClient.newHttpClient()));
        return new TestRestTemplate(builder);
    }
}
