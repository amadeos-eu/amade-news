package eu.amadeos.apps.news;

import eu.amadeos.apps.news.net.Http;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Serves canned answers by address and records the requests. */
public final class FakeHttp implements Http {
    private final Map<String, Response> answers = new ConcurrentHashMap<>();
    public final List<String> requests = java.util.Collections.synchronizedList(new ArrayList<>());
    public final List<Map<String, String>> requestHeaders = java.util.Collections.synchronizedList(new ArrayList<>());

    public FakeHttp serve(String url, String body, String contentType) {
        return serve(url, 200, body.getBytes(StandardCharsets.UTF_8), Map.of("content-type", contentType));
    }

    public FakeHttp serve(String url, int status, byte[] body, Map<String, String> headers) {
        answers.put(url, new Response(status, URI.create(url), new HashMap<>(headers), body));
        return this;
    }

    public FakeHttp status(String url, int status, Map<String, String> headers) {
        return serve(url, status, new byte[0], headers);
    }

    @Override
    public Response get(URI uri, Map<String, String> headers) throws IOException {
        requests.add(uri.toString());
        requestHeaders.add(Map.copyOf(headers));
        Response r = answers.get(uri.toString());
        if (r == null) throw new ConnectException("no connection to " + uri.getHost());
        return r;
    }
}
