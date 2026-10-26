package eu.amadeos.apps.news.net;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Fetches documents and pictures. The real one is {@link JdkHttp}; tests serve from memory. */
public interface Http {
    /**
     * GETs the address.
     *
     * @param headers request headers, e.g. {@code If-None-Match}
     */
    Response get(URI uri, Map<String, String> headers) throws IOException, InterruptedException;

    /**
     * The answer.
     *
     * @param status   HTTP status
     * @param finalUri the address after redirects
     * @param headers  response headers, names in lower case
     * @param body     empty for 304
     */
    record Response(int status, URI finalUri, Map<String, String> headers, byte[] body) {
        public Optional<String> header(String name) {
            return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
        }

        /** The charset in Content-Type, if any. */
        public Charset charset() {
            String type = header("content-type").orElse("");
            int at = type.toLowerCase(Locale.ROOT).indexOf("charset=");
            if (at < 0) return null;
            String name = type.substring(at + 8).split("[;\\s]")[0].replace("\"", "");
            try {
                return Charset.forName(name);
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                return null;
            }
        }

        public boolean isHtml() {
            return header("content-type").orElse("").toLowerCase(Locale.ROOT).contains("html");
        }
    }
}
