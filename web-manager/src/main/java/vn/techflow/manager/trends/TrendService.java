package vn.techflow.manager.trends;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads a small, configured set of RSS/Atom feeds for topic discovery.
 *
 * The service intentionally has no URL parameter. Feeds come only from an
 * environment/property value and are checked against an HTTPS host allowlist
 * before a request is made. Feed failures are warnings, never fatal errors.
 */
@Service
public class TrendService {
    static final int MAX_FEEDS = 5;
    static final int MAX_ITEMS = 20;
    static final int MAX_XML_BYTES = 256 * 1024;
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private static final List<TrendItem> FALLBACK_ITEMS = List.of(
            new TrendItem("Gợi ý: cập nhật AI và công cụ lập trình chính thức",
                    "https://developers.openai.com/", "", "TechFlow fallback"),
            new TrendItem("Gợi ý: mẹo Python từ tài liệu chuẩn",
                    "https://docs.python.org/3/", "", "TechFlow fallback"),
            new TrendItem("Gợi ý: kiểm tra API và bảo mật trước khi tích hợp",
                    "https://developers.tiktok.com/", "", "TechFlow fallback")
    );

    private final HttpClient httpClient;
    private final List<URI> feeds;
    private final Set<String> allowedDomains;
    private volatile TrendsResponse cached;
    private volatile Instant cachedAt;

    @Autowired
    public TrendService(
            @Value("${techflow.trend-feeds:}") String configuredFeeds,
            @Value("${techflow.trend-allowed-domains:developers.openai.com,docs.python.org,developers.tiktok.com}") String configuredDomains) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build(),
                parseUris(configuredFeeds), parseDomains(configuredDomains));
    }

    TrendService(HttpClient httpClient, List<URI> feeds, Set<String> allowedDomains) {
        this.httpClient = httpClient;
        this.feeds = List.copyOf(feeds);
        this.allowedDomains = Set.copyOf(allowedDomains);
    }

    public synchronized TrendsResponse getTrends() {
        if (cached != null && cachedAt != null && Instant.now().isBefore(cachedAt.plus(CACHE_TTL))) {
            return cached;
        }
        TrendsResponse response = loadTrends();
        cached = response;
        cachedAt = Instant.now();
        return response;
    }

    private TrendsResponse loadTrends() {
        if (feeds.isEmpty()) {
            return fallback(List.of("Chưa cấu hình TREND_FEEDS; đang hiển thị gợi ý cố định."));
        }
        List<TrendItem> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (URI feed : feeds) {
            if (items.size() >= MAX_ITEMS) {
                break;
            }
            if (!isAllowed(feed, allowedDomains)) {
                warnings.add("Bỏ qua feed ngoài allowlist: " + feed.getHost());
                continue;
            }
            try {
                items.addAll(fetch(feed, MAX_ITEMS - items.size()));
            } catch (Exception exception) {
                warnings.add("Không đọc được feed " + feed + ": " + shortMessage(exception));
            }
        }
        if (items.isEmpty()) {
            warnings.add("Không có feed hợp lệ; nội dung bên dưới chỉ là gợi ý, không phải tin mới.");
            return fallback(warnings);
        }
        return new TrendsResponse(List.copyOf(items), false, List.copyOf(warnings));
    }

    private List<TrendItem> fetch(URI feed, int remaining)
            throws IOException, InterruptedException, SAXException, ParserConfigurationException {
        HttpRequest request = HttpRequest.newBuilder(feed)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .header("User-Agent", "AI-TechFlow-Trends/1.0")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < HttpURLConnection.HTTP_OK || response.statusCode() >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new IOException("HTTP " + response.statusCode());
            }
            byte[] xml = readLimited(body, MAX_XML_BYTES);
            return parseXml(xml, feed, remaining);
        }
    }

    /** Package-private for deterministic unit tests and no-network parsing QA. */
    static List<TrendItem> parseXml(byte[] xml, URI source, int maxItems)
            throws IOException, SAXException, ParserConfigurationException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        FeedHandler handler = new FeedHandler(source, Math.min(Math.max(maxItems, 0), MAX_ITEMS));
        factory.newSAXParser().parse(new InputSource(new ByteArrayInputStream(xml)), handler);
        return handler.items;
    }

    private TrendsResponse fallback(List<String> warnings) {
        return new TrendsResponse(FALLBACK_ITEMS, true, List.copyOf(warnings));
    }

    static boolean isAllowed(URI uri, Set<String> domains) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        return domains.stream().anyMatch(domain -> host.equals(domain)
                || host.endsWith("." + domain));
    }

    static List<URI> parseUris(String configured) {
        if (configured == null || configured.isBlank()) {
            return List.of();
        }
        List<URI> values = new ArrayList<>();
        for (String raw : configured.split(",")) {
            if (values.size() == MAX_FEEDS) {
                break;
            }
            try {
                URI uri = URI.create(raw.trim());
                if (!uri.toString().isBlank()) {
                    values.add(uri);
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid configuration is surfaced by the deterministic fallback warning.
            }
        }
        return List.copyOf(values);
    }

    static Set<String> parseDomains(String configured) {
        if (configured == null || configured.isBlank()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String raw : configured.split(",")) {
            String domain = raw.trim().toLowerCase(Locale.ROOT);
            if (!domain.isBlank() && domain.matches("[a-z0-9.-]+")) {
                values.add(domain.startsWith(".") ? domain.substring(1) : domain);
            }
        }
        return Set.copyOf(values);
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 16 * 1024));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new IOException("Feed vượt quá giới hạn " + limit + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String shortMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message.substring(0, Math.min(160, message.length()));
    }

    private static final class FeedHandler extends DefaultHandler {
        private final URI source;
        private final int maxItems;
        private final List<TrendItem> items = new ArrayList<>();
        private String container;
        private String field;
        private String title;
        private String link;
        private String publishedAt;
        private StringBuilder text;

        private FeedHandler(URI source, int maxItems) {
            this.source = source;
            this.maxItems = maxItems;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String name = elementName(localName, qName);
            if (("item".equalsIgnoreCase(name) || "entry".equalsIgnoreCase(name)) && container == null) {
                container = name.toLowerCase(Locale.ROOT);
                title = link = publishedAt = null;
                return;
            }
            if (container == null) {
                return;
            }
            if ("link".equalsIgnoreCase(name) && attributes.getValue("href") != null) {
                link = attributes.getValue("href");
            }
            if (Set.of("title", "link", "pubdate", "published", "updated").contains(name.toLowerCase(Locale.ROOT))) {
                field = name.toLowerCase(Locale.ROOT);
                text = new StringBuilder();
            }
        }

        @Override
        public void characters(char[] chars, int start, int length) {
            if (text != null && text.length() < 2_000) {
                text.append(chars, start, Math.min(length, 2_000 - text.length()));
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String name = elementName(localName, qName).toLowerCase(Locale.ROOT);
            if (container == null) {
                return;
            }
            if (field != null && field.equals(name) && text != null) {
                String value = text.toString().replaceAll("\\s+", " ").trim();
                if ("title".equals(field)) title = value;
                if ("link".equals(field) && (link == null || link.isBlank())) link = value;
                if (Set.of("pubdate", "published", "updated").contains(field)) publishedAt = value;
                field = null;
                text = null;
            }
            if (container.equals(name)) {
                if (title != null && !title.isBlank() && items.size() < maxItems) {
                    items.add(new TrendItem(title.substring(0, Math.min(160, title.length())),
                            link == null ? "" : link.substring(0, Math.min(500, link.length())),
                            publishedAt == null ? "" : publishedAt.substring(0, Math.min(80, publishedAt.length())),
                            source.getHost()));
                }
                container = null;
                field = null;
                text = null;
            }
        }

        private static String elementName(String localName, String qName) {
            return localName == null || localName.isBlank() ? qName : localName;
        }
    }
}
