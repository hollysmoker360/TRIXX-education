package com.schoolsphere.cbe.internal;

import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
@TenantScoped
public class CbeService {

    public record CurriculumView(UUID id, String code, String title, String status, int version) {
    }

    public record CompetencyView(UUID id, UUID curriculumVersionId, UUID parentId, String code, String title,
                                 String description, String status, List<String> evidenceTypes) {
    }

    public record RubricView(UUID id, String title) {
    }

    private final CurriculumVersionRepository curriculumRepository;
    private final CompetencyRepository competencyRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository criterionRepository;

    public CbeService(CurriculumVersionRepository curriculumRepository, CompetencyRepository competencyRepository,
                      RubricRepository rubricRepository, RubricCriterionRepository criterionRepository) {
        this.curriculumRepository = curriculumRepository;
        this.competencyRepository = competencyRepository;
        this.rubricRepository = rubricRepository;
        this.criterionRepository = criterionRepository;
    }

    public List<CurriculumView> listCurricula() {
        return curriculumRepository.findBySchoolIdAndDeletedAtIsNullOrderByCreatedAtDesc(TenantContext.schoolId())
                .stream().map(this::toCurriculumView).toList();
    }

    public CurriculumView createCurriculum(String code, String title) {
        UUID schoolId = TenantContext.schoolId();
        CurriculumVersion v = new CurriculumVersion();
        v.setSchoolId(schoolId);
        v.setCode(code);
        v.setTitle(title);
        v.setStatus("DRAFT");
        v.setVersion(1);
        v.setCreatedBy(TenantContext.actorUserId());
        v.setUpdatedBy(TenantContext.actorUserId());
        return toCurriculumView(curriculumRepository.save(v));
    }

    public CurriculumView getCurriculum(UUID id) {
        return toCurriculumView(curriculumRepository.findBySchoolIdAndIdAndDeletedAtIsNull(TenantContext.schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("curriculum_not_found", "Curriculum version not found.")));
    }

    public void deleteCurriculum(UUID id) {
        CurriculumVersion v = curriculumRepository.findBySchoolIdAndIdAndDeletedAtIsNull(TenantContext.schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("curriculum_not_found", "Curriculum version not found."));
        v.setDeletedAt(Instant.now());
        curriculumRepository.save(v);
    }

    public List<CompetencyView> listCompetencies(UUID curriculumVersionId) {
        return competencyRepository.findBySchoolIdAndCurriculumVersionIdAndDeletedAtIsNull(
                TenantContext.schoolId(), curriculumVersionId)
                .stream().map(this::toCompetencyView).toList();
    }

    public CompetencyView createCompetency(UUID curriculumVersionId, String code, String title, String description) {
        Competency c = new Competency();
        c.setSchoolId(TenantContext.schoolId());
        c.setCurriculumVersionId(curriculumVersionId);
        c.setCode(code);
        c.setTitle(title);
        c.setDescription(description);
        c.setStatus("DRAFT");
        c.setCreatedBy(TenantContext.actorUserId());
        c.setUpdatedBy(TenantContext.actorUserId());
        return toCompetencyView(competencyRepository.save(c));
    }

    public List<RubricView> listRubrics() {
        return rubricRepository.findBySchoolIdAndDeletedAtIsNullOrderByCreatedAtDesc(TenantContext.schoolId())
                .stream().map(r -> new RubricView(r.getId(), r.getTitle())).toList();
    }

    public RubricView createRubric(String title) {
        Rubric r = new Rubric();
        r.setSchoolId(TenantContext.schoolId());
        r.setTitle(title);
        r.setCreatedBy(TenantContext.actorUserId());
        r.setUpdatedBy(TenantContext.actorUserId());
        Rubric saved = rubricRepository.save(r);
        return new RubricView(saved.getId(), saved.getTitle());
    }

    private CurriculumView toCurriculumView(CurriculumVersion v) {
        return new CurriculumView(v.getId(), v.getCode(), v.getTitle(), v.getStatus(), v.getVersion());
    }

    private CompetencyView toCompetencyView(Competency c) {
        return new CompetencyView(c.getId(), c.getCurriculumVersionId(), c.getParentId(), c.getCode(), c.getTitle(),
                c.getDescription(), c.getStatus(), c.getEvidenceTypes());
    }
}
