package vn.techflow.manager.youtube;

import jakarta.persistence.*;
import vn.techflow.manager.auth.AppUser;

import java.time.LocalDateTime;

@Entity
@Table(name = "youtube_accounts")
public class YouTubeAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private AppUser owner;
    @Column(nullable = false, length = 160) private String channelId = "";
    @Column(nullable = false, length = 160) private String channelTitle = "";
    @Column(nullable = false, columnDefinition = "TEXT") private String encryptedAccessToken;
    @Column(nullable = false, columnDefinition = "TEXT") private String encryptedRefreshToken;
    @Column(nullable = false, length = 1000) private String scopes = "";
    @Column(nullable = false) private LocalDateTime accessTokenExpiresAt;
    @Column(nullable = false, updatable = false) private LocalDateTime connectedAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    @PrePersist void create() { connectedAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }

    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public String getChannelTitle() { return channelTitle; }
    public void setChannelTitle(String channelTitle) { this.channelTitle = channelTitle; }
    public String getEncryptedAccessToken() { return encryptedAccessToken; }
    public void setEncryptedAccessToken(String encryptedAccessToken) { this.encryptedAccessToken = encryptedAccessToken; }
    public String getEncryptedRefreshToken() { return encryptedRefreshToken; }
    public void setEncryptedRefreshToken(String encryptedRefreshToken) { this.encryptedRefreshToken = encryptedRefreshToken; }
    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }
    public LocalDateTime getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public void setAccessTokenExpiresAt(LocalDateTime accessTokenExpiresAt) { this.accessTokenExpiresAt = accessTokenExpiresAt; }
    public LocalDateTime getConnectedAt() { return connectedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
