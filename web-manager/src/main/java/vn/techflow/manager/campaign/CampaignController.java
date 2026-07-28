package vn.techflow.manager.campaign;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.techflow.manager.task.WorkTask;

import java.util.List;

@Tag(name = "Campaigns")
@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {
    private final CampaignService service;
    private final vn.techflow.manager.task.TaskService taskService;

    public CampaignController(CampaignService service, vn.techflow.manager.task.TaskService taskService) {
        this.service = service;
        this.taskService = taskService;
    }

    @Operation(summary = "Danh sách campaign có phân trang và phân quyền")
    @GetMapping
    public Page<Campaign> search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication) {
        return service.search(query, page, size, authentication);
    }

    @Operation(summary = "Chi tiết campaign")
    @GetMapping("/{id}")
    public Campaign get(@PathVariable Long id, Authentication authentication) {
        return service.get(id, authentication);
    }

    @Operation(summary = "Tạo campaign hoặc series")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Campaign create(@Valid @RequestBody CampaignRequest request, Authentication authentication) {
        return service.create(request, authentication);
    }

    @Operation(summary = "Cập nhật campaign hoặc series")
    @PutMapping("/{id}")
    public Campaign update(@PathVariable Long id, @Valid @RequestBody CampaignRequest request,
                           Authentication authentication) {
        return service.update(id, request, authentication);
    }

    @Operation(summary = "Tạo toàn bộ tập thành các task video")
    @PostMapping("/{id}/episodes")
    public List<WorkTask> createEpisodes(@PathVariable Long id, Authentication authentication) {
        return service.createEpisodes(id, authentication);
    }

    @Operation(summary = "Dùng AI tạo series bible và kế hoạch chi tiết cho từng tập")
    @PostMapping("/{id}/plan")
    public Campaign plan(@PathVariable Long id, Authentication authentication) {
        return service.planSeries(id, authentication);
    }

    @Operation(summary = "Dung AI tao series bible va danh sach tap trong mot buoc")
    @PostMapping("/{id}/ai-series")
    public List<WorkTask> planAndCreateEpisodes(@PathVariable Long id, Authentication authentication) {
        return service.planAndCreateEpisodes(id, authentication);
    }

    @Operation(summary = "Tạo lại ảnh reference nhân vật bằng AI")
    @PostMapping("/{id}/generate-character")
    public Campaign generateCharacter(@PathVariable Long id,
                                      @Valid @RequestBody CharacterGenerationRequest request,
                                      Authentication authentication) {
        return service.generateCharacter(id, request, authentication);
    }

    @Operation(summary = "Upload ảnh reference nhân vật thủ công lên Cloudinary")
    @PostMapping("/{id}/character-image")
    public Campaign uploadCharacter(@PathVariable Long id,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(defaultValue = "") String description,
                                    Authentication authentication) {
        return service.uploadCharacter(id, file, description, authentication);
    }

    @Operation(summary = "Sản xuất tập TODO kế tiếp thành bản nháp cần duyệt")
    @PostMapping("/{id}/produce-next")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WorkTask produceNext(@PathVariable Long id, Authentication authentication) {
        WorkTask task = service.prepareNext(id, authentication);
        taskService.generate(task.getId());
        return task;
    }

    @Operation(summary = "Xóa campaign, giữ lại video đã tạo")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.delete(id, authentication);
    }
}
