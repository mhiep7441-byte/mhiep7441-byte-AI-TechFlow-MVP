package vn.techflow.manager.tiktok;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TikTokConfiguration {
    private final String clientKey;
    private final String clientSecret;
    private final String redirectUri;

    public TikTokConfiguration(
            @Value("${techflow.tiktok.client-key:}") String clientKey,
            @Value("${techflow.tiktok.client-secret:}") String clientSecret,
            @Value("${techflow.tiktok.redirect-uri:http://localhost:8080/oauth/tiktok/callback}") String redirectUri) {
        this.clientKey = clientKey.trim();
        this.clientSecret = clientSecret.trim();
        this.redirectUri = redirectUri.trim();
    }

    public boolean configured() {
        return !clientKey.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    public String clientKey() { return clientKey; }
    public String clientSecret() { return clientSecret; }
    public String redirectUri() { return redirectUri; }
}
