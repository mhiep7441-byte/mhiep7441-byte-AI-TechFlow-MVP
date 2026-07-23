package vn.techflow.manager.publication;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Publications")
@RestController
@RequestMapping("/api/publications")
public class PublicationController {
    private final PublicationService service;

    public PublicationController(PublicationService service) { this.service = service; }

    @Operation(summary = "Danh sách lịch xuất bản")
    @GetMapping public List<PublicationResponse> all() { return service.all(); }
    @Operation(summary = "Tạo lịch xuất bản")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public PublicationResponse create(@Valid @RequestBody PublicationRequest request) { return service.create(request); }
    @Operation(summary = "Cập nhật lịch xuất bản")
    @PutMapping("/{id}")
    public PublicationResponse update(@PathVariable Long id, @Valid @RequestBody PublicationRequest request) { return service.update(id, request); }
    @Operation(summary = "Xóa lịch xuất bản")
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
