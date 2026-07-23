package vn.techflow.manager.campaign;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.techflow.manager.task.WorkTask;

import java.util.List;

@Tag(name = "Campaigns")
@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {
    private final CampaignService service;

    public CampaignController(CampaignService service) { this.service = service; }

    @Operation(summary = "Danh sách campaign có phân trang và phân quyền")
    @GetMapping
    public Page<Campaign> search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication) {
        return service.search(query, page, size, authentication);
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

    @Operation(summary = "Xóa campaign, giữ lại video đã tạo")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.delete(id, authentication);
    }
}
