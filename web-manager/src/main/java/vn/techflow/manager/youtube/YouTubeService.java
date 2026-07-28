package vn.techflow.manager.youtube;

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
import vn.techflow.manager.publication.PublicationResponse;
import vn.techflow.manager.publication.PublicationService;
import vn.techflow.manager.task.TaskService;
import vn.techflow.manager.task.TaskStatus;
import vn.techflow.manager.task.WorkTask;
import vn.techflow.manager.tiktok.TokenCipher;

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
import java.util.Base64;
import java.util.Map;
import java.util.Set;

@Service
public class YouTubeService {
    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String UPLOAD_URL =
            "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status";
    private static final String SCOPE = "https://www.googleapis.com/auth/youtube.upload";
    private static final long MAX_VIDEO_BYTES = 256L * 1024 * 1024;
    private static final Set<String> PRIVACY = Set.of("private", "unlisted", "public");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final YouTubeConfiguration configuration;
    private final YouTubeAccountRepository accounts;
    private final TokenCipher cipher;
    private final AuthService authService;
    private final TaskService taskService;
    private final PublicationService publicationService;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public YouTubeService(YouTubeConfiguration configuration, YouTubeAccountRepository accounts,
                          TokenCipher cipher, AuthService authService, TaskService taskService,
                          PublicationService publicationService, ObjectMapper json) {
        this.configuration = configuration;
        this.accounts = accounts;
        this.cipher = cipher;
        this.authService = authService;
        this.taskService = taskService;
        this.publicationService = publicationService;
        this.json = json;
    }

    public URI authorizationUri(Authentication authentication, HttpSession session) {
        requireConfigured();
        AppUser owner = authService.current(authentication);
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        session.setAttribute("youtube.oauth.state", state);
        session.setAttribute("youtube.oauth.owner", owner.getId());
        String query = "client_id=" + encode(configuration.clientId())
                + "&redirect_uri=" + encode(configuration.redirectUri())
                + "&response_type=code&scope=" + encode(SCOPE)
                + "&access_type=offline&include_granted_scopes=true&prompt=consent"
                + "&state=" + encode(state);
        return URI.create(AUTHORIZE_URL + "?" + query);
    }

    @Transactional
    public YouTubeConnectionStatus completeAuthorization(String code, String state,
                                                         Authentication authentication, HttpSession session) {
        requireConfigured();
        Object expectedState = session.getAttribute("youtube.oauth.state");
        Object expectedOwner = session.getAttribute("youtube.oauth.owner");
        session.removeAttribute("youtube.oauth.state");
        session.removeAttribute("youtube.oauth.owner");
        AppUser owner = authService.current(authentication);
        if (!(expectedState instanceof String value)
                || !MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))
                || !(expectedOwner instanceof Long ownerId)
                || !owner.getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiên kết nối YouTube không hợp lệ");
        }
        JsonNode token = sendForm(Map.of(
                "client_id", configuration.clientId(),
                "client_secret", configuration.clientSecret(),
                "code", code,
                "grant_type", "authorization_code",
                "redirect_uri", configuration.redirectUri()
        ));
        String accessToken = required(token, "access_token");
        String refreshToken = required(token, "refresh_token");
        YouTubeAccount account = accounts.findByOwnerId(owner.getId()).orElseGet(YouTubeAccount::new);
        if (account.getOwner() == null) account.setOwner(owner);
        account.setChannelTitle("Kênh YouTube đã kết nối");
        account.setEncryptedAccessToken(cipher.encrypt(accessToken));
        account.setEncryptedRefreshToken(cipher.encrypt(refreshToken));
        account.setScopes(token.path("scope").asText(SCOPE));
        account.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(token.path("expires_in").asLong(3600)));
        accounts.save(account);
        return status(authentication);
    }

    @Transactional(readOnly = true)
    public YouTubeConnectionStatus status(Authentication authentication) {
        if (!configuration.configured() || !cipher.configured()) {
            return new YouTubeConnectionStatus(false, false, "", "", false,
                    "YouTube chưa được cấu hình trên server");
        }
        AppUser owner = authService.current(authentication);
        return accounts.findByOwnerId(owner.getId())
                .map(account -> new YouTubeConnectionStatus(true, true, account.getChannelId(),
                        account.getChannelTitle(), account.getScopes().contains(SCOPE),
                        "Sẵn sàng upload video đã duyệt"))
                .orElseGet(() -> new YouTubeConnectionStatus(true, false, "", "", false,
                        "Hãy kết nối kênh YouTube"));
    }

    @Transactional
    public void disconnect(Authentication authentication) {
        accounts.deleteByOwnerId(authService.current(authentication).getId());
    }

    public YouTubePublishResponse publish(Long taskId, YouTubePublishRequest request,
                                          Authentication authentication) {
        requireConfigured();
        if (!request.consent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần xác nhận đã xem và duyệt video");
        }
        if (!PRIVACY.contains(request.privacyStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "YouTube privacyStatus không hợp lệ");
        }
        WorkTask task = taskService.getAccessible(taskId, authentication);
        if (task.getStatus() != TaskStatus.DRAFT_REQUIRES_REVIEW && task.getStatus() != TaskStatus.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ upload video đã dựng và đang chờ duyệt");
        }
        if (task.getOutputPath() == null || task.getOutputPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Video chưa có file đầu ra");
        }
        AppUser owner = authService.current(authentication);
        YouTubeAccount account = accounts.findByOwnerId(owner.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Bạn chưa kết nối YouTube"));

        Path video = null;
        try {
            video = downloadVideo(task.getOutputPath());
            long size = Files.size(video);
            ObjectNode metadata = json.createObjectNode();
            ObjectNode snippet = metadata.putObject("snippet");
            snippet.put("title", request.title().trim());
            snippet.put("description", request.description() == null ? "" : request.description().trim());
            snippet.put("categoryId", "28");
            ObjectNode status = metadata.putObject("status");
            status.put("privacyStatus", request.privacyStatus());
            status.put("selfDeclaredMadeForKids", request.madeForKids());

            String accessToken = currentAccessToken(account);
            HttpRequest init = HttpRequest.newBuilder(URI.create(UPLOAD_URL))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("X-Upload-Content-Length", String.valueOf(size))
                    .header("X-Upload-Content-Type", "video/mp4")
                    .POST(HttpRequest.BodyPublishers.ofString(metadata.toString()))
                    .build();
            HttpResponse<String> initResponse = sendRaw(init);
            String location = initResponse.headers().firstValue("Location").orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.BAD_GATEWAY, "YouTube không trả về upload session"));
            HttpRequest upload = HttpRequest.newBuilder(URI.create(location))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "video/mp4")
                    .header("Content-Length", String.valueOf(size))
                    .PUT(HttpRequest.BodyPublishers.ofFile(video))
                    .build();
            JsonNode response = parseSuccess(sendRaw(upload));
            String videoId = required(response, "id");
            PublicationResponse publication = publicationService.recordYouTubeSubmission(task, videoId);
            return new YouTubePublishResponse(videoId, publication.id(), request.privacyStatus(),
                    "YouTube đã nhận video; video đang được xử lý");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không thể upload video lên YouTube", exception);
        } finally {
            if (video != null) {
                try { Files.deleteIfExists(video); } catch (IOException ignored) { /* temporary media */ }
            }
        }
    }

    private String currentAccessToken(YouTubeAccount account) {
        if (account.getAccessTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return cipher.decrypt(account.getEncryptedAccessToken());
        }
        JsonNode token = sendForm(Map.of(
                "client_id", configuration.clientId(),
                "client_secret", configuration.clientSecret(),
                "refresh_token", cipher.decrypt(account.getEncryptedRefreshToken()),
                "grant_type", "refresh_token"
        ));
        String accessToken = required(token, "access_token");
        account.setEncryptedAccessToken(cipher.encrypt(accessToken));
        account.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(token.path("expires_in").asLong(3600)));
        accounts.save(account);
        return accessToken;
    }

    private Path downloadVideo(String value) throws IOException {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chỉ chấp nhận video HTTPS từ Cloudinary đã cấu hình");
        }
        Path target = Files.createTempFile("techflow-youtube-", ".mp4");
        try {
            HttpResponse<Path> response = http.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Cloudinary returned HTTP " + response.statusCode());
            }
            long size = Files.size(target);
            if (size <= 0 || size > MAX_VIDEO_BYTES) throw new IOException("Invalid video size: " + size);
            return target;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(target);
            throw new IOException("Video download interrupted", exception);
        } catch (IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    private JsonNode sendForm(Map<String, String> fields) {
        String body = fields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return parseSuccess(sendRaw(request));
    }

    private HttpResponse<String> sendRaw(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = "HTTP " + response.statusCode();
                try {
                    JsonNode body = json.readTree(response.body());
                    message = body.path("error").path("message").asText(message);
                } catch (Exception ignored) { /* preserve status-only message */ }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "YouTube API: " + message);
            }
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Kết nối YouTube bị gián đoạn", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không đọc được phản hồi YouTube", exception);
        }
    }

    private JsonNode parseSuccess(HttpResponse<String> response) {
        try {
            return response.body() == null || response.body().isBlank()
                    ? json.createObjectNode() : json.readTree(response.body());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "YouTube trả về JSON không hợp lệ", exception);
        }
    }

    private void requireConfigured() {
        if (!configuration.configured() || !cipher.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "YouTube hoặc khóa mã hóa token chưa được cấu hình");
        }
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "YouTube không trả về " + field);
        }
        return value;
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
