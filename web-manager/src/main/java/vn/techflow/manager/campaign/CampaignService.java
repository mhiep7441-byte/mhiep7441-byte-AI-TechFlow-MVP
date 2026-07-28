package vn.techflow.manager.campaign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.techflow.manager.auth.AppUser;
import vn.techflow.manager.auth.AuthService;
import vn.techflow.manager.auth.UserRole;
import vn.techflow.manager.task.Priority;
import vn.techflow.manager.task.TaskRepository;
import vn.techflow.manager.task.TaskStatus;
import vn.techflow.manager.task.WorkTask;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CampaignService {
    private static final List<String> ANGLES = List.of(
            "Bối cảnh và vấn đề người xem thường gặp",
            "Khái niệm cốt lõi cần hiểu đúng",
            "Quy trình thực hành từng bước",
            "Sai lầm phổ biến và cách tránh",
            "Ví dụ ứng dụng thực tế",
            "Checklist đánh giá kết quả",
            "Góc nhìn chuyên sâu và giới hạn",
            "Tổng kết hành trình và bước tiếp theo"
    );

    private final CampaignRepository campaigns;
    private final TaskRepository tasks;
    private final AuthService authService;
    private final SeriesPlannerService planner;
    private final ObjectMapper json;
    private final Path projectDirectory;
    private final String pythonCommand;
    private final String workerScript;
    private final RestClient restClient;

    public CampaignService(CampaignRepository campaigns, TaskRepository tasks, AuthService authService,
                           SeriesPlannerService planner, ObjectMapper json,
                           @Value("${techflow.project-dir:..}") String projectDirectory,
                           @Value("${techflow.python-command:python}") String pythonCommand,
                           @Value("${techflow.worker-script:video_worker.py}") String workerScript,
                           RestClient.Builder restClientBuilder) {
        this.campaigns = campaigns;
        this.tasks = tasks;
        this.authService = authService;
        this.planner = planner;
        this.json = json;
        this.projectDirectory = Path.of(projectDirectory).toAbsolutePath().normalize();
        this.pythonCommand = pythonCommand;
        this.workerScript = workerScript;
        this.restClient = restClientBuilder.build();
    }

    @Transactional(readOnly = true)
    public Page<Campaign> search(String query, int page, int size, Authentication authentication) {
        AppUser viewer = authService.current(authentication);
        Long ownerId = viewer.getRole() == UserRole.ADMIN ? null : viewer.getId();
        return campaigns.search(ownerId, clean(query),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                        Sort.by(Sort.Direction.DESC, "updatedAt")));
    }

    @Transactional
    public Campaign create(CampaignRequest request, Authentication authentication) {
        Campaign campaign = new Campaign();
        campaign.setOwner(authService.current(authentication));
        apply(campaign, request);
        return campaigns.save(campaign);
    }

    @Transactional
    public Campaign update(Long id, CampaignRequest request, Authentication authentication) {
        Campaign campaign = accessible(id, authentication);
        apply(campaign, request);
        return campaigns.save(campaign);
    }

    @Transactional(readOnly = true)
    public Campaign get(Long id, Authentication authentication) {
        return accessible(id, authentication);
    }

    @Transactional
    public Campaign generateCharacter(Long id, CharacterGenerationRequest request, Authentication authentication) {
        Campaign campaign = accessible(id, authentication);
        String prompt = clean(request.description()).isBlank()
                ? clean(campaign.getCharacterDescription()) : clean(request.description());
        if (prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần mô tả nhân vật để tạo reference sheet");
        }
        try {
            List<String> command = new ArrayList<>(List.of(
                    pythonCommand, workerScript, "--topic", campaign.getTheme(),
                    "--visual-style", clean(campaign.getVisualStyle()),
                    "--character", prompt, "--generate-character"
            ));
            Process process = new ProcessBuilder(command)
                    .directory(projectDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) throw new IOException("Worker lỗi " + exitCode + ": " + tail(output, 2500));
            String url = parseCharacterUrl(output).orElseThrow(() ->
                    new IOException("Worker không trả về CHARACTER_IMAGE_URL"));
            campaign.setCharacterDescription(prompt);
            campaign.setCharacterReferencePrompt(prompt);
            campaign.setCharacterImageUrl(url);
            syncOpenEpisodes(campaign);
            return campaigns.save(campaign);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Tạo nhân vật bị gián đoạn", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, tail(exception.getMessage(), 3500), exception);
        }
    }

    @Transactional
    public Campaign uploadCharacter(Long id, MultipartFile file, String description, Authentication authentication) {
        Campaign campaign = accessible(id, authentication);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần chọn ảnh nhân vật");
        }
        if (!String.valueOf(file.getContentType()).startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File upload phải là ảnh");
        }
        String cloudinaryUrl = System.getenv("CLOUDINARY_URL");
        if (cloudinaryUrl == null || cloudinaryUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình CLOUDINARY_URL");
        }
        try {
            URI uri = URI.create(cloudinaryUrl);
            String[] credentials = Optional.ofNullable(uri.getUserInfo()).orElse(":").split(":", 2);
            if (credentials.length < 2 || credentials[0].isBlank() || credentials[1].isBlank() || uri.getHost() == null) {
                throw new IOException("CLOUDINARY_URL không hợp lệ");
            }
            String cloudName = uri.getHost();
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String folder = "techflow/characters";
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("file", new NamedByteArrayResource(file.getBytes(), file.getOriginalFilename()))
                    .contentType(MediaType.parseMediaType(
                            Optional.ofNullable(file.getContentType()).orElse(MediaType.IMAGE_PNG_VALUE)));
            body.part("api_key", credentials[0]);
            body.part("timestamp", timestamp);
            body.part("folder", folder);
            body.part("signature", sha1("folder=" + folder + "&timestamp=" + timestamp + credentials[1]));
            MultiValueMap<String, org.springframework.http.HttpEntity<?>> multipart = body.build();
            JsonNode response = restClient.post()
                    .uri("https://api.cloudinary.com/v1_1/{cloud}/image/upload", cloudName)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart)
                    .retrieve()
                    .body(JsonNode.class);
            String url = response == null ? "" : response.path("secure_url").asText("");
            if (url.isBlank()) throw new IOException("Cloudinary không trả về secure_url");
            String prompt = clean(description).isBlank() ? campaign.getCharacterDescription() : clean(description);
            campaign.setCharacterDescription(prompt);
            campaign.setCharacterReferencePrompt(prompt);
            campaign.setCharacterImageUrl(url);
            syncOpenEpisodes(campaign);
            return campaigns.save(campaign);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upload Cloudinary thất bại: " + tail(exception.getMessage(), 2500), exception);
        }
    }

    @Transactional
    public Campaign planSeries(Long id, Authentication authentication) {
        Campaign campaign = accessible(id, authentication);
        JsonNode plan = planner.plan(campaign);
        campaign.setSeriesPlanJson(plan.toString());
        return campaigns.save(campaign);
    }

    @Transactional
    public List<WorkTask> planAndCreateEpisodes(Long id, Authentication authentication) {
        Campaign campaign = accessible(id, authentication);
        JsonNode plan = planner.plan(campaign);
        campaign.setSeriesPlanJson(plan.toString());
        campaigns.save(campaign);
        return createOrRefreshEpisodes(campaign);
    }

    @Transactional
    public List<WorkTask> createEpisodes(Long id, Authentication authentication) {
        return createEpisodes(accessible(id, authentication));
    }

    @Transactional
    public WorkTask prepareNext(Long id, Authentication authentication) {
        return claimNext(accessible(id, authentication), LocalDateTime.now());
    }

    @Transactional
    public List<Long> claimDueBatch(LocalDateTime now, int limit) {
        List<Long> taskIds = new ArrayList<>();
        PageRequest batch = PageRequest.of(0, Math.min(Math.max(limit, 1), 10));
        for (Campaign campaign : campaigns.findDue(now, batch)) {
            try {
                taskIds.add(claimNext(campaign, now).getId());
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() != HttpStatus.CONFLICT) throw exception;
            }
        }
        return taskIds;
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        campaigns.delete(accessible(id, authentication));
    }

    private List<WorkTask> createEpisodes(Campaign campaign) {
        return createOrRefreshEpisodes(campaign);
    }

    private List<WorkTask> createOrRefreshEpisodes(Campaign campaign) {
        Long id = campaign.getId();
        List<WorkTask> existing = tasks.findByCampaignIdOrderByEpisodeNumberAsc(id);
        if (!existing.isEmpty()) {
            syncPlannedEpisodes(campaign, existing);
            return tasks.findByCampaignIdOrderByEpisodeNumberAsc(id);
        }

        JsonNode plannedEpisodes = readPlan(campaign).path("episodes");
        List<WorkTask> episodes = new ArrayList<>();
        for (int number = 1; number <= campaign.getEpisodeCount(); number++) {
            JsonNode planned = plannedEpisodes.isArray() && plannedEpisodes.size() >= number
                    ? plannedEpisodes.get(number - 1) : json.createObjectNode();
            WorkTask task = new WorkTask();
            task.setOwner(campaign.getOwner());
            task.setCampaignId(id);
            task.setEpisodeNumber(number);
            task.setTargetDurationSeconds(campaign.getTargetDurationSeconds());
            task.setVisualStyle(campaign.getVisualStyle());
            task.setCharacterDescription(campaign.getCharacterDescription());
            task.setCharacterImageUrl(campaign.getCharacterImageUrl());
            applyRenderProfile(campaign, task);
            task.setPriority(Priority.MEDIUM);
            task.setStatus(TaskStatus.TODO);
            applyEpisodePlan(campaign, task, number, planned);
            episodes.add(task);
        }
        tasks.saveAll(episodes);
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaigns.save(campaign);
        return tasks.findByCampaignIdOrderByEpisodeNumberAsc(id);
    }

    private void syncPlannedEpisodes(Campaign campaign, List<WorkTask> episodes) {
        JsonNode plannedEpisodes = readPlan(campaign).path("episodes");
        for (int index = 0; index < episodes.size(); index++) {
            WorkTask task = episodes.get(index);
            TaskStatus status = task.getStatus();
            if (status != TaskStatus.TODO && status != TaskStatus.FAILED) continue;
            int number = task.getEpisodeNumber() == null ? index + 1 : task.getEpisodeNumber();
            JsonNode planned = plannedEpisodes.isArray() && plannedEpisodes.size() >= number
                    ? plannedEpisodes.get(number - 1) : json.createObjectNode();
            task.setTargetDurationSeconds(campaign.getTargetDurationSeconds());
            task.setVisualStyle(campaign.getVisualStyle());
            task.setCharacterDescription(campaign.getCharacterDescription());
            task.setCharacterImageUrl(campaign.getCharacterImageUrl());
            applyRenderProfile(campaign, task);
            if (status == TaskStatus.FAILED) {
                task.setStatus(TaskStatus.TODO);
                task.setErrorMessage(null);
            }
            applyEpisodePlan(campaign, task, number, planned);
        }
        tasks.saveAll(episodes);
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaigns.save(campaign);
    }

    private void applyEpisodePlan(Campaign campaign, WorkTask task, int number, JsonNode planned) {
        String angle = ANGLES.get((number - 1) % ANGLES.size());
        String plannedTitle = planned.path("title").asText("").trim();
        String synopsis = planned.path("synopsis").asText(angle).trim();
        String objective = planned.path("learning_objective").asText("").trim();
        String hook = planned.path("hook").asText("").trim();
        String guardrails = planned.path("factual_guardrails").isArray()
                ? planned.path("factual_guardrails").toString() : "[]";
        task.setTitle(limit(plannedTitle.isBlank()
                ? campaign.getName() + " — Tập " + number
                : campaign.getName() + " — " + plannedTitle, 160));
        task.setTopic(limit(campaign.getTheme() + ". Tập " + number + "/" + campaign.getEpisodeCount()
                + ": " + synopsis
                + (hook.isBlank() ? "" : ". Hook: " + hook)
                + ". Mục tiêu: " + objective
                + ". Ràng buộc kiểm chứng: " + guardrails
                + ". Không lặp lại nội dung của tập khác.", 500));
        task.setDescription(limit(campaign.getDescription()
                + (objective.isBlank() ? "" : "\nMục tiêu: " + objective), 2000));
    }

    private WorkTask claimNext(Campaign campaign, LocalDateTime now) {
        if (tasks.findByCampaignIdOrderByEpisodeNumberAsc(campaign.getId()).isEmpty()) {
            createEpisodes(campaign);
        }
        WorkTask task = tasks.findFirstByCampaignIdAndStatusOrderByEpisodeNumberAsc(
                campaign.getId(), TaskStatus.TODO).orElseThrow(() -> {
            campaign.setStatus(CampaignStatus.COMPLETED);
            campaign.setProductionEnabled(false);
            campaign.setNextRunAt(null);
            campaigns.save(campaign);
            return new ResponseStatusException(HttpStatus.CONFLICT, "Campaign không còn tập chờ sản xuất");
        });
        task.setStatus(TaskStatus.GENERATING);
        task.setErrorMessage(null);
        tasks.save(task);

        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setLastRunAt(now);
        campaign.setNextRunAt(nextRun(campaign.getCadence(), now));
        if (campaign.getCadence() == CampaignCadence.MANUAL) campaign.setProductionEnabled(false);
        campaigns.save(campaign);
        return task;
    }

    private Campaign accessible(Long id, Authentication authentication) {
        Campaign campaign = campaigns.findWithOwnerById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy campaign"));
        AppUser viewer = authService.current(authentication);
        if (viewer.getRole() != UserRole.ADMIN && !viewer.getId().equals(campaign.getOwner().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập campaign này");
        }
        return campaign;
    }

    private static void apply(Campaign campaign, CampaignRequest request) {
        campaign.setName(request.name().trim());
        campaign.setTheme(request.theme().trim());
        campaign.setDescription(clean(request.description()));
        campaign.setEpisodeCount(request.episodeCount() == null ? 5 : request.episodeCount());
        campaign.setTargetDurationSeconds(request.targetDurationSeconds() == null ? 60 : request.targetDurationSeconds());
        campaign.setVisualStyle(clean(request.visualStyle()));
        campaign.setCharacterDescription(clean(request.characterDescription()));
        campaign.setCharacterImageUrl(clean(request.characterImageUrl()).isBlank() ? null : clean(request.characterImageUrl()));
        campaign.setCharacterReferencePrompt(clean(request.characterReferencePrompt()));
        campaign.setAudioMode(option(request.audioMode(), "narrated"));
        campaign.setVideoProvider(option(request.videoProvider(), "kenburns"));
        campaign.setAspectRatio(option(request.aspectRatio(), "9:16"));
        campaign.setRenderQuality(option(request.renderQuality(), "draft"));
        campaign.setAudience(clean(request.audience()));
        campaign.setCadence(request.cadence() == null ? CampaignCadence.MANUAL : request.cadence());
        campaign.setProductionEnabled(Boolean.TRUE.equals(request.productionEnabled())
                && campaign.getCadence() != CampaignCadence.MANUAL);
        campaign.setNextRunAt(campaign.isProductionEnabled()
                ? (request.nextRunAt() == null ? LocalDateTime.now() : request.nextRunAt()) : null);
        campaign.setStatus(request.status() == null ? CampaignStatus.PLANNING : request.status());
    }

    private JsonNode readPlan(Campaign campaign) {
        try {
            if (campaign.getSeriesPlanJson() == null || campaign.getSeriesPlanJson().isBlank()) {
                return json.createObjectNode();
            }
            return json.readTree(campaign.getSeriesPlanJson());
        } catch (Exception exception) {
            return json.createObjectNode();
        }
    }

    private static void applyRenderProfile(Campaign campaign, WorkTask task) {
        task.setAudioMode(campaign.getAudioMode());
        task.setVideoProvider(campaign.getVideoProvider());
        task.setAspectRatio(campaign.getAspectRatio());
        task.setRenderQuality(campaign.getRenderQuality());
    }

    private static String option(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private static LocalDateTime nextRun(CampaignCadence cadence, LocalDateTime from) {
        return switch (cadence) {
            case HOURLY -> from.plusHours(1);
            case DAILY -> from.plusDays(1);
            case MANUAL -> null;
        };
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
    private static String tail(String value, int limit) {
        if (value == null) return "Lỗi không xác định";
        return value.length() <= limit ? value : value.substring(value.length() - limit);
    }
    private static Optional<String> parseCharacterUrl(String output) {
        Matcher matcher = Pattern.compile("CHARACTER_IMAGE_URL=(https?://\\S+)").matcher(output);
        return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
    }

    private static String sha1(String value) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception exception) {
            throw new IOException("Không tạo được chữ ký Cloudinary", exception);
        }
    }

    private void syncOpenEpisodes(Campaign campaign) {
        List<WorkTask> episodes = tasks.findByCampaignIdOrderByEpisodeNumberAsc(campaign.getId());
        for (WorkTask task : episodes) {
            if (task.getStatus() == TaskStatus.TODO || task.getStatus() == TaskStatus.FAILED) {
                task.setCharacterDescription(campaign.getCharacterDescription());
                task.setCharacterImageUrl(campaign.getCharacterImageUrl());
            }
        }
        if (!episodes.isEmpty()) tasks.saveAll(episodes);
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = clean(filename).isBlank() ? "character-reference.png" : filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
