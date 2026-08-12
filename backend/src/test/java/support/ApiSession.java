package support;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.Map;

/**
 * One independent logged-in identity against a running @SpringBootTest instance -- the REST
 * equivalent of the socket-protocol test suite's TestConnection. Session-based auth means the
 * server tracks identity via a cookie rather than a per-connection socket, so this class's job is
 * simply: capture the Set-Cookie from POST /api/auth/login, then attach it to every subsequent
 * request. Independent ApiSession instances sharing one TestRestTemplate give independent
 * identities, the way separate TestConnections did.
 */
public class ApiSession {

    private final TestRestTemplate restTemplate;
    private String sessionCookie;

    public ApiSession(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<Map<String, Object>> login(String username, String password) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", username, "password", password)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
        captureCookie(response);
        return response;
    }

    public void logout() {
        exchange(HttpMethod.POST, "/api/auth/logout", null, Void.class);
        sessionCookie = null;
    }

    public <T> ResponseEntity<T> get(String path, Class<T> responseType) {
        return exchange(HttpMethod.GET, path, null, responseType);
    }

    public <T> ResponseEntity<T> get(String path, ParameterizedTypeReference<T> responseType) {
        return exchange(HttpMethod.GET, path, null, responseType);
    }

    public <T> ResponseEntity<T> post(String path, Object body, Class<T> responseType) {
        return exchange(HttpMethod.POST, path, body, responseType);
    }

    public <T> ResponseEntity<T> put(String path, Object body, Class<T> responseType) {
        return exchange(HttpMethod.PUT, path, body, responseType);
    }

    public <T> ResponseEntity<T> delete(String path, Class<T> responseType) {
        return exchange(HttpMethod.DELETE, path, null, responseType);
    }

    public <T> ResponseEntity<T> exchange(HttpMethod method, String path, Object body, Class<T> responseType) {
        ResponseEntity<T> response = restTemplate.exchange(path, method, entityWithCookie(body), responseType);
        captureCookie(response);
        return response;
    }

    public <T> ResponseEntity<T> exchange(HttpMethod method, String path, Object body,
                                           ParameterizedTypeReference<T> responseType) {
        ResponseEntity<T> response = restTemplate.exchange(path, method, entityWithCookie(body), responseType);
        captureCookie(response);
        return response;
    }

    public String sessionCookie() {
        return sessionCookie;
    }

    private HttpEntity<?> entityWithCookie(Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        return body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);
    }

    private void captureCookie(ResponseEntity<?> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies != null && !setCookies.isEmpty()) {
            this.sessionCookie = setCookies.get(0).split(";")[0];
        }
    }
}
