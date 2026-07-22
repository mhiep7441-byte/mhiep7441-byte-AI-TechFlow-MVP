package vn.techflow.manager.tiktok;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.techflow.manager.auth.AppUser;
import vn.techflow.manager.auth.AuthService;
import vn.techflow.manager.publication.Platform;
import vn.techflow.manager.publication.Publication;
import vn.techflow.manager.publication.PublicationResponse;
import vn.techflow.manager.publication.PublicationService;
import vn.techflow.manager.publication.PublicationStatus;
import vn.techflow.manager.task.TaskService;
import vn.techflow.manager.task.TaskStatus;
import vn.techflow.manager.task.WorkTask;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class TikTokService {
    private static final String AUTHORIZE_URL = "https://www.tiktok.com/v2/auth/authorize/";
    private static final String TOKEN_URL = "https://open.tiktokapis.com/v2/oauth/token/";
    private static final String CREATOR_INFO_URL = "https://open.tiktokapis.com/v2/post/publish/creator_info/query/";
    private static final String DIRECT_POST_URL = "https://open.tiktokapis.com/v2/post/publish/video/init/";
    private static final String PUBLISH_STATUS_URL = "https://open.tiktokapis.com/v2/post/publish/status/fetch/";
    private static final long MAX_VIDEO_BYTES = 128L * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TikTokConfiguration configuration;
    private final TokenCipher cipher;
    private final TikTokAccountRepository accounts;
    private final AuthService authService;
    private final TaskService taskService;
    private final PublicationService publicationService;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public TikTokService(
            TikTokConfiguration configuration,
            TokenCipher cipher,
            TikTokAccountRepository accounts,
            AuthService authService,
            TaskService taskService,
            PublicationService publicationService) {
        this.configuration = configuration;
        this.cipher = cipher;
        this.accounts = accounts;
        this.authService = authService;
        this.taskService = taskService;
        this.publicationService = publicationService;
    }

    public URI authorizationUri(Authentication authentication, HttpSession session) {
        requireConfigured();
        AppUser owner = authService.current(authentication);
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        session.setAttribute("tiktok.oauth.state", state);
        session.setAttribute("tiktok.oauth.owner", owner.getId());
        String query = "client_key=" + encode(configuration.clientKey())
                + "&scope=" + encode("user.info.basic,video.publish")
                + "&response_type=code&redirect_uri=" + encode(configuration.redirectUri())
                + "&state=" + encode(state);
        return URI.create(AUTHORIZE_URL + "?" + query);
    }

    @Transactional
    public TikTokConnectionStatus completeAuthorization(
            String code, String state, Authentication authentication, HttpSession session) {
        requireConfigured();
        Object expectedState = session.getAttribute("tiktok.oauth.state");
        Object expectedOwner = session.getAttribute("tiktok.oauth.owner");
        session.removeAttribute("tiktok.oauth.state");
        session.removeAttribute("tiktok.oauth.owner");
        AppUser owner = authService.current(authentication);
        if (!(expectedState instanceof String value)
                || !MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))
                || !(expectedOwner instanceof Long ownerId)
                || !owner.getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiên kết nối TikTok không hợp lệ hoặc đã hết hạn");
        }

        JsonNode token = sendForm(TOKEN_URL, Map.of(
                "client_key", configuration.clientKey(),
                "client_secret", configuration.clientSecret(),
                "code", code,
                "grant_type", "authorization_code",
                "redirect_uri", configuration.redirectUri()
        ));
        String accessToken = required(token, "access_token");
        String refreshToken = required(token, "refresh_token");
        String openId = required(token, "open_id");
        TikTokAccount account = accounts.findByOwnerId(owner.getId()).orElseGet(TikTokAccount::new);
        if (account.getOwner() == null) account.setOwner(owner);
        account.setOpenId(openId);
        account.setDisplayName(fetchDisplayName(accessToken, openId));
        account.setEncryptedAccessToken(cipher.encrypt(accessToken));
        account.setEncryptedRefreshToken(cipher.encrypt(refreshToken));
        account.setScopes(token.path("scope").asText(""));
        account.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(token.path("expires_in").asLong(86_400)));
        account.setRefreshTokenExpiresAt(LocalDateTime.now().plusSeconds(token.path("refresh_expires_in").asLong(31_536_000)));
        accounts.save(account);
        return status(authentication);
    }

    @Transactional(readOnly = true)
    public TikTokConnectionStatus status(Authentication authentication) {
        if (!configuration.configured() || !cipher.configured()) {
            return new TikTokConnectionStatus(false, false, "", "", List.of(), false,
                    "TikTok chưa được cấu hình đầy đủ trên server");
        }
        AppUser owner = authService.current(authentication);
        return accounts.findByOwnerId(owner.getId())
                .map(account -> {
                    List<String> scopes = List.of(account.getScopes().split(","));
                    boolean canPublish = scopes.stream().map(String::trim).anyMatch("video.publish"::equals);
                    return new TikTokConnectionStatus(true, true, account.getDisplayName(), account.getOpenId(),
                            scopes, canPublish, canPublish ? "Sẵn sàng gửi video đã duyệt" : "Thiếu scope video.publish");
                })
                .orElseGet(() -> new TikTokConnectionStatus(true, false, "", "", List.of(), false,
                        "Hãy kết nối tài khoản TikTok"));
    }

    public TikTokPublishResponse publish(Long taskId, TikTokPublishRequest request, Authentication authentication) {
        requireConfigured();
        if (!request.consent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần xác nhận đồng ý gửi video TikTok");
        }
        WorkTask task = taskService.getAccessible(taskId, authentication);
        if (task.getStatus() != TaskStatus.DRAFT_REQUIRES_REVIEW && task.getStatus() != TaskStatus.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể gửi video đã dựng và đang chờ duyệt");
        }
        if (task.getOutputPath() == null || task.getOutputPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Video chưa có file đầu ra");
        }
        AppUser owner = authService.current(authentication);
        TikTokAccount account = accounts.findByOwnerId(owner.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Bạn chưa kết nối TikTok"));
        if (!List.of(account.getScopes().split(",")).stream().map(String::trim).toList().contains("video.publish")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản chưa cấp scope video.publish");
        }
        String accessToken = currentAccessToken(account);
        TikTokCreatorInfo creator = creatorInfo(accessToken);
        List<String> privacyOptions = creator.privacyLevelOptions();
        if (!privacyOptions.contains(request.privacyLevel())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Quyền riêng tư không được tài khoản TikTok cho phép. Các giá trị hợp lệ: " + privacyOptions);
        }

        Path video = null;
        try {
            video = downloadVideo(task.getOutputPath());
            long size = Files.size(video);
            String title = request.title() == null || request.title().isBlank()
                    ? (task.getCaption() + " " + task.getHashtags()).trim()
                    : request.title().trim();
            ObjectNode payload = JSON.createObjectNode();
            ObjectNode postInfo = payload.putObject("post_info");
            postInfo.put("title", limitUtf16(title, 2200));
            postInfo.put("privacy_level", request.privacyLevel());
            postInfo.put("disable_comment", request.disableComment());
            postInfo.put("disable_duet", request.disableDuet());
            postInfo.put("disable_stitch", request.disableStitch());
            postInfo.put("video_cover_timestamp_ms", 1000);
            ObjectNode sourceInfo = payload.putObject("source_info");
            sourceInfo.put("source", "FILE_UPLOAD");
            sourceInfo.put("video_size", size);
            sourceInfo.put("chunk_size", size);
            sourceInfo.put("total_chunk_count", 1);
            JsonNode init = sendJson(DIRECT_POST_URL, payload, accessToken);
            String publishId = required(init.path("data"), "publish_id");
            String uploadUrl = required(init.path("data"), "upload_url");
            uploadVideo(uploadUrl, video, size);
            PublicationResponse publication = publicationService.recordTikTokSubmission(task, publishId);
            return new TikTokPublishResponse(publishId, publication.id(), "PROCESSING",
                    "TikTok đã nhận video và đang xử lý");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không thể truyền video tới TikTok", exception);
        } finally {
            if (video != null) {
                try { Files.deleteIfExists(video); } catch (IOException ignored) { /* temporary file only */ }
            }
        }
    }

    public TikTokCreatorInfo creatorInfo(Authentication authentication) {
        requireConfigured();
        AppUser owner = authService.current(authentication);
        TikTokAccount account = accounts.findByOwnerId(owner.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Bạn chưa kết nối TikTok"));
        return creatorInfo(currentAccessToken(account));
    }

    public TikTokPublishStatusResponse refreshPublishStatus(Long publicationId, Authentication authentication) {
        requireConfigured();
        Publication publication = publicationService.getAccessibleEntity(publicationId, authentication);
        if (publication.getPlatform() != Platform.TIKTOK
                || publication.getExternalId() == null
                || publication.getExternalId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lượt xuất bản không phải TikTok Direct Post");
        }
        AppUser viewer = authService.current(authentication);
        TikTokAccount account = accounts.findByOwnerId(viewer.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Bạn chưa kết nối TikTok"));
        ObjectNode payload = JSON.createObjectNode().put("publish_id", publication.getExternalId());
        JsonNode data = sendJson(PUBLISH_STATUS_URL, payload, currentAccessToken(account)).path("data");
        String tiktokStatus = data.path("status").asText("UNKNOWN");
        String failReason = data.path("fail_reason").asText("").trim();
        PublicationStatus mapped = mapPublicationStatus(tiktokStatus);
        String message = switch (mapped) {
            case PUBLISHED -> "TikTok đã xuất bản video";
            case FAILED -> failReason.isBlank() ? "TikTok xử lý video thất bại" : "TikTok: " + failReason;
            default -> "TikTok đang xử lý video: " + tiktokStatus;
        };
        PublicationResponse updated = publicationService.updateTikTokSubmission(
                publication.getExternalId(), mapped, message);
        return new TikTokPublishStatusResponse(publication.getExternalId(), updated.id(), tiktokStatus,
                updated.status().name(), message);
    }

    static PublicationStatus mapPublicationStatus(String status) {
        if ("PUBLISH_COMPLETE".equals(status) || "SEND_TO_USER_INBOX".equals(status)) {
            return PublicationStatus.PUBLISHED;
        }
        if ("FAILED".equals(status)) return PublicationStatus.FAILED;
        return PublicationStatus.PROCESSING;
    }

    private TikTokCreatorInfo creatorInfo(String accessToken) {
        JsonNode data = sendJson(CREATOR_INFO_URL, JSON.createObjectNode(), accessToken).path("data");
        List<String> privacyOptions = new ArrayList<>();
        data.path("privacy_level_options").forEach(value -> privacyOptions.add(value.asText()));
        return new TikTokCreatorInfo(
                data.path("creator_username").asText(""),
                data.path("creator_nickname").asText(""),
                privacyOptions,
                data.path("comment_disabled").asBoolean(false),
                data.path("duet_disabled").asBoolean(false),
                data.path("stitch_disabled").asBoolean(false),
                data.path("max_video_post_duration_sec").asInt(60)
        );
    }

    private String currentAccessToken(TikTokAccount account) {
        if (account.getAccessTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return cipher.decrypt(account.getEncryptedAccessToken());
        }
        String refreshToken = cipher.decrypt(account.getEncryptedRefreshToken());
        JsonNode refreshed = sendForm(TOKEN_URL, Map.of(
                "client_key", configuration.clientKey(),
                "client_secret", configuration.clientSecret(),
                "grant_type", "refresh_token",
                "refresh_token", refreshToken
        ));
        String accessToken = required(refreshed, "access_token");
        String nextRefresh = refreshed.path("refresh_token").asText(refreshToken);
        account.setEncryptedAccessToken(cipher.encrypt(accessToken));
        account.setEncryptedRefreshToken(cipher.encrypt(nextRefresh));
        account.setScopes(refreshed.path("scope").asText(account.getScopes()));
        account.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(refreshed.path("expires_in").asLong(86_400)));
        accounts.save(account);
        return accessToken;
    }

    private String fetchDisplayName(String accessToken, String fallback) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET().build();
            JsonNode body = send(request);
            return body.path("data").path("user").path("display_name").asText(fallback);
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private Path downloadVideo(String value) throws IOException {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ chấp nhận video HTTPS từ Cloudinary đã cấu hình");
        }
        Path target = Files.createTempFile("techflow-tiktok-", ".mp4");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2)).GET().build();
        try {
            HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Cloudinary trả HTTP " + response.statusCode());
            }
            long size = Files.size(target);
            if (size <= 0 || size > MAX_VIDEO_BYTES) throw new IOException("Kích thước video không hợp lệ: " + size);
            return target;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Tải video bị gián đoạn", exception);
        } catch (IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    private void uploadVideo(String uploadUrl, Path video, long size) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "video/mp4")
                .header("Content-Range", "bytes 0-" + (size - 1) + "/" + size)
                .PUT(HttpRequest.BodyPublishers.ofFile(video))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("TikTok upload trả HTTP " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Upload TikTok bị gián đoạn", exception);
        }
    }

    private JsonNode sendJson(String url, JsonNode payload, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        return send(request);
    }

    private JsonNode sendForm(String url, Map<String, String> fields) {
        String body = fields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body() == null || response.body().isBlank()
                    ? JSON.createObjectNode() : JSON.readTree(response.body());
            String errorCode = body.path("error").path("code").asText("ok");
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !"ok".equals(errorCode)) {
                String message = body.path("error").path("message").asText("TikTok API từ chối yêu cầu");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "TikTok: " + message + ("ok".equals(errorCode) ? "" : " (" + errorCode + ")"));
            }
            return body;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Kết nối TikTok bị gián đoạn", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không đọc được phản hồi TikTok", exception);
        }
    }

    private void requireConfigured() {
        if (!configuration.configured() || !cipher.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TikTok chưa được cấu hình đầy đủ trên server");
        }
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "TikTok không trả về trường " + field);
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String limitUtf16(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
