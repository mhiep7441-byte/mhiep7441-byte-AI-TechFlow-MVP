package vn.techflow.manager.research;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Research Notebook")
@RestController
@RequestMapping("/api/research/notebook")
public class ResearchNotebookController {
    private final ResearchNotebookService service;

    public ResearchNotebookController(ResearchNotebookService service) { this.service = service; }

    @GetMapping
    public List<ResearchNotebookEntry> list(@RequestParam(defaultValue = "50") int limit,
                                            Authentication authentication) {
        return service.list(authentication, limit);
    }
}
