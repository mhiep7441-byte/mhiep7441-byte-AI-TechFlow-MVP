package vn.techflow.manager.publication;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/publications")
public class PublicationController {
    private final PublicationService service;

    public PublicationController(PublicationService service) { this.service = service; }

    @GetMapping
    public Page<PublicationResponse> search(
            @RequestParam(required = false) PublicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return service.search(status, page, size, authentication);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public PublicationResponse create(@Valid @RequestBody PublicationRequest request, Authentication authentication) {
        return service.create(request, authentication);
    }

    @PutMapping("/{id}")
    public PublicationResponse update(@PathVariable Long id, @Valid @RequestBody PublicationRequest request,
                                      Authentication authentication) {
        return service.update(id, request, authentication);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.delete(id, authentication);
    }
}
