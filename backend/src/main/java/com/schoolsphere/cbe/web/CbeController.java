package com.schoolsphere.cbe.web;

import com.schoolsphere.cbe.internal.CbeService;
import com.schoolsphere.cbe.internal.CbeService.CompetencyView;
import com.schoolsphere.cbe.internal.CbeService.CurriculumView;
import com.schoolsphere.cbe.internal.CbeService.RubricView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cbe")
public class CbeController {

    private final CbeService cbeService;

    public CbeController(CbeService cbeService) {
        this.cbeService = cbeService;
    }

    @GetMapping("/curricula")
    @PreAuthorize("@policy.can('academics.view')")
    public List<CurriculumView> curricula() {
        return cbeService.listCurricula();
    }

    @PostMapping("/curricula")
    @PreAuthorize("@policy.can('academics.manage')")
    public CurriculumView createCurriculum(@Valid @RequestBody CreateCurriculumRequest request) {
        return cbeService.createCurriculum(request.code(), request.title());
    }

    @GetMapping("/curricula/{id}")
    @PreAuthorize("@policy.can('academics.view')")
    public CurriculumView getCurriculum(@PathVariable UUID id) {
        return cbeService.getCurriculum(id);
    }

    @DeleteMapping("/curricula/{id}")
    @PreAuthorize("@policy.can('academics.manage')")
    public void deleteCurriculum(@PathVariable UUID id) {
        cbeService.deleteCurriculum(id);
    }

    @GetMapping("/competencies")
    @PreAuthorize("@policy.can('academics.view')")
    public List<CompetencyView> competencies(@RequestParam UUID curriculumVersionId) {
        return cbeService.listCompetencies(curriculumVersionId);
    }

    @PostMapping("/competencies")
    @PreAuthorize("@policy.can('academics.manage')")
    public CompetencyView createCompetency(@Valid @RequestBody CreateCompetencyRequest request) {
        return cbeService.createCompetency(request.curriculumVersionId(), request.code(), request.title(),
                request.description());
    }

    @GetMapping("/rubrics")
    @PreAuthorize("@policy.can('academics.view')")
    public List<RubricView> rubrics() {
        return cbeService.listRubrics();
    }

    @PostMapping("/rubrics")
    @PreAuthorize("@policy.can('academics.manage')")
    public RubricView createRubric(@Valid @RequestBody CreateRubricRequest request) {
        return cbeService.createRubric(request.title());
    }

    public record CreateCurriculumRequest(@NotBlank String code, @NotBlank String title) {
    }

    public record CreateCompetencyRequest(UUID curriculumVersionId, @NotBlank String code, @NotBlank String title,
                                         String description) {
    }

    public record CreateRubricRequest(@NotBlank String title) {
    }
}
