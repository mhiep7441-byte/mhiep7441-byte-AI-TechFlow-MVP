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
        item.setStatus(request.status() == null ? PublicationStatus.PENDING : request.status());
        item.setScheduledAt(request.scheduledAt());
        item.setExternalId(request.externalId());
        item.setNote(request.note() == null ? "" : request.note().trim());
        if (item.getStatus() == PublicationStatus.PUBLISHED && item.getPublishedAt() == null) {
            item.setPublishedAt(LocalDateTime.now());
        }
        return repository.save(item);
    }

    @Transactional
    public void delete(Long id, Authentication authentication) { repository.delete(accessible(id, authentication)); }

    private Publication accessible(Long id, Authentication authentication) {
        Publication item = repository.findWithTaskById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đăng"));
        taskService.getAccessible(item.getTask().getId(), authentication);
        return item;
    }
}
