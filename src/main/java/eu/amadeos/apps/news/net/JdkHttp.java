package eu.amadeos.apps.news.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * The JDK's HTTP client with the JVM's proxy selector, so News follows the amadeOS proxy setting once amade-commons
 * installs it for every program; this is the one place to switch to the shared client of amade-commons then.
 * Bodies are limited to {@value #MAX_BYTES} bytes.
 */
public final class JdkHttp implements Http {
    static final int MAX_BYTES = 20 * 1024 * 1024;

    private final HttpClient client;
    private final String userAgent;

    public JdkHttp(String userAgent) {
        this.userAgent = userAgent;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(ProxySelector.getDefault())
                .build();
    }

    @Override
    public Response get(URI uri, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", userAgent)
                .header("Accept-Encoding", "gzip")
                .GET();
        headers.forEach(request::header);
        HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        Map<String, String> h = new HashMap<>();
        response.headers().map().forEach((k, v) -> h.put(k.toLowerCase(Locale.ROOT), String.join(", ", v)));
        boolean gzip = response.headers().firstValue("content-encoding").orElse("").toLowerCase(Locale.ROOT).contains("gzip");
        try (InputStream raw = response.body(); InputStream in = gzip ? new GZIPInputStream(raw) : raw) {
            byte[] body = in.readNBytes(MAX_BYTES + 1);
            if (body.length > MAX_BYTES) throw new IOException("larger than " + MAX_BYTES / 1024 / 1024 + " MB");
            return new Response(response.statusCode(), response.uri(), h, body);
        }
    }
}
