package vn.techflow.manager.publication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.techflow.manager.auth.AppUser;
import vn.techflow.manager.auth.AuthService;
import vn.techflow.manager.auth.UserRole;
import vn.techflow.manager.task.TaskService;
import vn.techflow.manager.task.WorkTask;
import vn.techflow.manager.task.TaskStatus;

import java.util.List;

import java.time.LocalDateTime;

@Service
public class PublicationService {
    private final PublicationRepository repository;
    private final TaskService taskService;
    private final AuthService authService;

    public PublicationService(PublicationRepository repository, TaskService taskService, AuthService authService) {
        this.repository = repository;
        this.taskService = taskService;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public Page<PublicationResponse> search(PublicationStatus status, int page, int size, Authentication authentication) {
        AppUser viewer = authService.current(authentication);
        Long ownerId = viewer.getRole() == UserRole.ADMIN ? null : viewer.getId();
        int safeSize = Math.min(Math.max(size, 1), 100);
        return repository.search(ownerId, status,
                        PageRequest.of(Math.max(page, 0), safeSize,
                                Sort.by(Sort.Direction.ASC, "scheduledAt").and(Sort.by(Sort.Direction.DESC, "createdAt"))))
                .map(PublicationResponse::from);
    }

    @Transactional
    public PublicationResponse create(PublicationRequest request, Authentication authentication) {
        return PublicationResponse.from(save(new Publication(), request, authentication));
    }

    @Transactional
    public PublicationResponse update(Long id, PublicationRequest request, Authentication authentication) {
        Publication item = accessible(id, authentication);
        return PublicationResponse.from(save(item, request, authentication));
    }

    private Publication save(Publication item, PublicationRequest request, Authentication authentication) {
        item.setTask(taskService.getAccessible(request.taskId(), authentication));
        item.setPlatform(request.platform());
        item.setStatus(clientManagedStatus(request.status()));
        item.setScheduledAt(request.scheduledAt());
        item.setExternalId("");
        item.setNote(request.note() == null ? "" : request.note().trim());
        return repository.save(item);
    }

    @Transactional
    public void delete(Long id, Authentication authentication) { repository.delete(accessible(id, authentication)); }

    @Transactional
    public PublicationResponse approve(Long id, PublicationApprovalRequest request, Authentication authentication) {
        if (!request.reviewed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần xác nhận đã xem video và kiểm tra nguồn");
        }
        Publication item = accessible(id, authentication);
        WorkTask task = item.getTask();
        if (task.getStatus() != TaskStatus.DRAFT_REQUIRES_REVIEW && task.getStatus() != TaskStatus.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Video phải được dựng xong trước khi duyệt lịch xuất bản");
        }
        if (task.getOutputPath() == null || task.getOutputPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Video chưa có file đầu ra");
        }
        task.setStatus(TaskStatus.DONE);
        item.setStatus(PublicationStatus.READY);
        item.setNote("Đã được người dùng review. Hãy mở Video Studio để xác nhận gửi lên " + item.getPlatform() + ".");
        return PublicationResponse.from(repository.save(item));
    }

    @Transactional(readOnly = true)
    public Publication getAccessibleEntity(Long id, Authentication authentication) {
        return accessible(id, authentication);
    }

    @Transactional
    public PublicationResponse recordTikTokSubmission(WorkTask task, String publishId) {
        Publication item = reusablePlan(task, Platform.TIKTOK);
        item.setTask(task);
        item.setPlatform(Platform.TIKTOK);
        item.setStatus(PublicationStatus.PROCESSING);
        item.setExternalId(publishId);
        item.setNote("TikTok đang xử lý video sau khi người dùng đã duyệt và đồng ý gửi.");
        return PublicationResponse.from(repository.save(item));
    }

    @Transactional
    public PublicationResponse recordYouTubeSubmission(WorkTask task, String videoId) {
        Publication item = reusablePlan(task, Platform.YOUTUBE);
        item.setTask(task);
        item.setPlatform(Platform.YOUTUBE);
        item.setStatus(PublicationStatus.PROCESSING);
        item.setExternalId(videoId);
        item.setNote("YouTube đang xử lý video sau khi người dùng đã xem và xác nhận upload.");
        return PublicationResponse.from(repository.save(item));
    }

    private Publication reusablePlan(WorkTask task, Platform platform) {
        return repository.findFirstByTaskIdAndPlatformAndStatusInOrderByCreatedAtAsc(
                task.getId(), platform, List.of(PublicationStatus.PENDING, PublicationStatus.READY))
                .orElseGet(Publication::new);
    }

    @Transactional
    public PublicationResponse updateTikTokSubmission(String publishId, PublicationStatus status, String note) {
        Publication item = repository.findWithTaskByExternalId(publishId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lượt đăng TikTok"));
        item.setStatus(status);
        item.setNote(note == null ? "" : note.trim());
        if (status == PublicationStatus.PUBLISHED && item.getPublishedAt() == null) {
            item.setPublishedAt(LocalDateTime.now());
        }
        return PublicationResponse.from(repository.save(item));
    }

    private Publication accessible(Long id, Authentication authentication) {
        Publication item = repository.findWithTaskById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đăng"));
        taskService.getAccessible(item.getTask().getId(), authentication);
        return item;
    }

    private static PublicationStatus clientManagedStatus(PublicationStatus requested) {
        if (requested == null || requested == PublicationStatus.PENDING) return PublicationStatus.PENDING;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Trạng thái xuất bản chỉ được cập nhật qua bước duyệt hoặc callback nền tảng");
    }
}
