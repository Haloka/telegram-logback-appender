package io.github.haloka.telegram.logback;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

class HttpClient {

    private final java.net.http.HttpClient client;
    private Duration readTimeout;

    HttpClient(Duration connectTimeout, Duration readTimeout, boolean followRedirects) {
        java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_2)
            .connectTimeout(connectTimeout)
            .followRedirects(followRedirects ?
                java.net.http.HttpClient.Redirect.NORMAL : java.net.http.HttpClient.Redirect.NEVER);

        this.readTimeout = readTimeout;

        this.client = builder.build();
    }

    static HttpClient of(Duration connectTimeout, Duration readTimeout, boolean followRedirects) {
        return new HttpClient(connectTimeout, readTimeout, followRedirects);
    }

    String get(String url, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(readTimeout)
                .GET();

            if (headers != null) {
                headers.forEach(builder::header);
            }

            HttpResponse<String> response = client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
            );

            return handleResponse(response);
        } catch (Exception e) {
            throw new RequestException("GET request failed", e);
        }
    }

    String post(String url, String body, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(readTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body));

            if (headers != null) {
                headers.forEach(builder::header);
            }

            HttpResponse<String> response = client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
            );

            return handleResponse(response);
        } catch (Exception e) {
            throw new RequestException("POST request failed", e);
        }
    }

    private String handleResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return response.body();
        }

        throw new RequestException("Request failed with status code: " + statusCode);
    }

    static class RequestException extends RuntimeException {
        public RequestException(String message) {
            super(message);
        }

        public RequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static void main(String[] args) {
        HttpClient httpClient = HttpClient.of(Duration.ofSeconds(10), Duration.ofSeconds(10), true);

        Map<String, String> headers = Map.of(
            "Content-Type", "application/json",
            "Authorization", "Bearer token123"
        );

        try {
            String data = httpClient.get("http://127.0.0.1:8080/test?tz=-540", headers);
            System.out.println("GET Response: " + data);

            String jsonBody = "{\"aaa\":\"222\",\"baa\":3}";
            String postResponse = httpClient.post("http://127.0.0.1:8080/test", jsonBody, headers);
            System.out.println("POST Response: " + postResponse);

            String postError = httpClient.post("http://127.0.0.1:8080/testA", jsonBody, headers);
            System.out.println("POST Response: " + postError);
         } catch (RequestException e) {
            System.err.println("Request failed: " + e.getMessage());
        }
    }
}