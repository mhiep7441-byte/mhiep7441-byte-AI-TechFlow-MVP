package vn.techflow.manager.campaign;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.techflow.manager.task.WorkTask;

import java.util.List;

@Tag(name = "Campaigns")
@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {
    private final CampaignService service;

    public CampaignController(CampaignService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách campaign/series")
    @GetMapping
    public List<Campaign> all() {
        return service.all();
    }

    @Operation(summary = "Tạo campaign/series")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Campaign create(@Valid @RequestBody CampaignRequest request) {
        return service.save(new Campaign(), request);
    }

    @Operation(summary = "Cập nhật campaign/series")
    @PutMapping("/{id}")
    public Campaign update(@PathVariable Long id, @Valid @RequestBody CampaignRequest request) {
        return service.save(service.get(id), request);
    }

    @Operation(summary = "Tạo danh sách tập thành các công việc video")
    @PostMapping("/{id}/episodes")
    public List<WorkTask> createEpisodes(@PathVariable Long id) {
        return service.createEpisodes(id);
    }

    @Operation(summary = "Xóa campaign, giữ lại các video đã tạo")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
