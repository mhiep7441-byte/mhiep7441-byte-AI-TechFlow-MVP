package vn.techflow.manager.campaign;

import jakarta.validation.constraints.Size;

public record CharacterGenerationRequest(
        @Size(max = 700) String description
) {}
