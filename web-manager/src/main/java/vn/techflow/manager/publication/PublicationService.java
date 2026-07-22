package vn.techflow.manager.publication;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.techflow.manager.task.TaskService;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PublicationService {
    private final PublicationRepository repository;
    private final TaskService taskService;

    public PublicationService(PublicationRepository repository, TaskService taskService) {
        this.repository = repository;
        this.taskService = taskService;
    }

    @Transactional(readOnly = true)
    public List<PublicationResponse> all() {
        return repository.findAllByOrderByScheduledAtAscCreatedAtDesc().stream().map(PublicationResponse::from).toList();
    }

    public PublicationResponse create(PublicationRequest request) {
        return PublicationResponse.from(save(new Publication(), request));
    }

    public PublicationResponse update(Long id, PublicationRequest request) {
        Publication item = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đăng " + id));
        return PublicationResponse.from(save(item, request));
    }

    private Publication save(Publication item, PublicationRequest request) {
        item.setTask(taskService.get(request.taskId()));
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

    public void delete(Long id) {
        Publication item = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đăng " + id));
        repository.delete(item);
    }
}
