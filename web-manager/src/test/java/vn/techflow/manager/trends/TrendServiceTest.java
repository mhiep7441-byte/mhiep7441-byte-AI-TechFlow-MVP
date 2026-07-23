package vn.techflow.manager.trends;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TrendServiceTest {
    @Test
    void rejectsNonHttpsAndUnallowlistedHosts() {
        Set<String> allowed = TrendService.parseDomains("example.com,docs.python.org");
        assertTrue(TrendService.isAllowed(URI.create("https://docs.python.org/feed.xml"), allowed));
        assertTrue(TrendService.isAllowed(URI.create("https://sub.docs.python.org/feed.xml"), allowed));
        assertFalse(TrendService.isAllowed(URI.create("http://docs.python.org/feed.xml"), allowed));
        assertFalse(TrendService.isAllowed(URI.create("https://example.net/feed.xml"), allowed));
        assertFalse(TrendService.isAllowed(URI.create("https://user:pass@example.com/feed.xml"), allowed));
    }

    @Test
    void parsesRssAndAtomItemsWithBounds() throws Exception {
        String xml = """
                <?xml version="1.0"?>
                <rss><channel>
                  <item><title>AI release</title><link>https://example.com/a</link><pubDate>today</pubDate></item>
                  <item><title>Second item</title><link>https://example.com/b</link></item>
                </channel></rss>
                """;
        List<TrendItem> items = TrendService.parseXml(xml.getBytes(StandardCharsets.UTF_8),
                URI.create("https://example.com/feed.xml"), 1);
        assertEquals(1, items.size());
        assertEquals("AI release", items.get(0).title());
        assertEquals("https://example.com/a", items.get(0).url());
        assertEquals("today", items.get(0).publishedAt());
    }

    @Test
    void rejectsXmlWithDoctype() {
        String xml = "<!DOCTYPE rss [ <!ENTITY xxe SYSTEM 'file:///etc/passwd'> ]><rss/>";
        assertThrows(Exception.class, () -> TrendService.parseXml(xml.getBytes(StandardCharsets.UTF_8),
                URI.create("https://example.com/feed.xml"), 20));
    }

    @Test
    void usesDeterministicFallbackWithoutConfiguredFeeds() {
        TrendService service = new TrendService(HttpClient.newHttpClient(), List.of(), Set.of("example.com"));
        TrendsResponse response = service.getTrends();
        assertTrue(response.fallback());
        assertEquals(3, response.items().size());
        assertFalse(response.warnings().isEmpty());
        assertSame(response, service.getTrends());
    }

    @Test
    void limitsConfiguredFeedsAndDomains() {
        assertEquals(TrendService.MAX_FEEDS,
                TrendService.parseUris("https://a.test/1,https://b.test/2,https://c.test/3,https://d.test/4,https://e.test/5,https://f.test/6").size());
        assertEquals(Set.of("example.com", "python.org"),
                TrendService.parseDomains("example.com, .python.org,EXAMPLE.COM"));
    }
}
