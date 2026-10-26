package eu.amadeos.apps.news.net;

import eu.amadeos.commons.net.Network;

import java.io.IOException;
import java.io.InputStream;
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
 * The JDK's HTTP client from amade-commons, so News follows the proxy of Settings &gt; Network, its password
 * included, and a change of it while the program runs. Bodies are limited to {@value #MAX_BYTES} bytes.
 */
public final class JdkHttp implements Http {
    static final int MAX_BYTES = 20 * 1024 * 1024;

    private final HttpClient client;
    private final String userAgent;

    // --8<-- [start:client]
    public JdkHttp(String userAgent) {
        this.userAgent = userAgent;
        this.client = Network.httpClient().build();
    }
    // --8<-- [end:client]

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
