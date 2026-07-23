package vn.techflow.manager.youtube;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "techflow.youtube")
public record YouTubeConfiguration(String clientId, String clientSecret, String redirectUri) {
    public YouTubeConfiguration {
        clientId = clean(clientId);
        clientSecret = clean(clientSecret);
        redirectUri = clean(redirectUri);
    }

    public boolean configured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
