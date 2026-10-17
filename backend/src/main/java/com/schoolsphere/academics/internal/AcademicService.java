package com.schoolsphere.academics.internal;

import com.schoolsphere.academics.api.AcademicsApi;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Academic structure CRUD with validation (spec 11.1). */
@Service
@TenantScoped
public class AcademicService implements AcademicsApi {

    private final AcademicYearRepository yearRepository;
    private final TermRepository termRepository;
    private final DepartmentRepository departmentRepository;
    private final ClassGroupRepository classRepository;
    private final StreamRepository streamRepository;
    private final SubjectRepository subjectRepository;
    private final ClassroomRepository classroomRepository;
    private final TimetablePeriodRepository periodRepository;

    public AcademicService(AcademicYearRepository yearRepository,
                           TermRepository termRepository,
                           DepartmentRepository departmentRepository,
                           ClassGroupRepository classRepository,
                           StreamRepository streamRepository,
                           SubjectRepository subjectRepository,
                           ClassroomRepository classroomRepository,
                           TimetablePeriodRepository periodRepository) {
        this.yearRepository = yearRepository;
        this.termRepository = termRepository;
        this.departmentRepository = departmentRepository;
        this.classRepository = classRepository;
        this.streamRepository = streamRepository;
        this.subjectRepository = subjectRepository;
        this.classroomRepository = classroomRepository;
        this.periodRepository = periodRepository;
    }

    // ----- academic years -----

    public List<YearView> listYears() {
        return yearRepository.findBySchoolIdAndDeletedAtIsNullOrderByStartsOnDesc(schoolId()).stream()
                .map(this::toYearView).toList();
    }

    public YearView createYear(String name, LocalDate startsOn, LocalDate endsOn, boolean active) {
        if (yearRepository.existsBySchoolIdAndNameAndDeletedAtIsNull(schoolId(), name)) {
            throw ApiException.conflict("year_exists", "An academic year with this name already exists.");
        }
        if (!endsOn.isAfter(startsOn)) {
            throw ApiException.badRequest("invalid_dates", "The end date must be after the start date.");
        }
        AcademicYear year = new AcademicYear();
        year.setSchoolId(schoolId());
        year.setName(name);
        year.setStartsOn(startsOn);
        year.setEndsOn(endsOn);
        year.setActive(active && yearRepository.findBySchoolIdAndIsActiveTrueAndDeletedAtIsNull(schoolId()).isEmpty());
        return toYearView(yearRepository.save(year));
    }

    public YearView updateYear(UUID id, String name, LocalDate startsOn, LocalDate endsOn) {
        AcademicYear year = requireYear(id);
        if (name != null) {
            year.setName(name);
        }
        if (startsOn != null) {
            year.setStartsOn(startsOn);
        }
        if (endsOn != null) {
            year.setEndsOn(endsOn);
        }
        if (!year.getEndsOn().isAfter(year.getStartsOn())) {
            throw ApiException.badRequest("invalid_dates", "The end date must be after the start date.");
        }
        return toYearView(yearRepository.save(year));
    }

    public YearView setActiveYear(UUID id) {
        AcademicYear year = requireYear(id);
        yearRepository.findBySchoolIdAndIsActiveTrueAndDeletedAtIsNull(schoolId()).ifPresent(active -> {
            active.setActive(false);
            yearRepository.save(active);
        });
        year.setActive(true);
        return toYearView(yearRepository.save(year));
    }

    public void deleteYear(UUID id) {
        AcademicYear year = requireYear(id);
        softDelete(year);
        yearRepository.save(year);
    }

    // ----- terms -----

    public List<TermView> listTerms(UUID academicYearId) {
        List<Term> terms = academicYearId == null
                ? termRepository.findBySchoolIdAndDeletedAtIsNullOrderByStartsOnDesc(schoolId())
                : termRepository.findBySchoolIdAndAcademicYearIdAndDeletedAtIsNullOrderBySeq(schoolId(), academicYearId);
        return terms.stream().map(this::toTermView).toList();
    }

    public TermView createTerm(UUID academicYearId, String name, int seq, LocalDate startsOn, LocalDate endsOn,
                               boolean current) {
        requireYear(academicYearId);
        if (!endsOn.isAfter(startsOn)) {
            throw ApiException.badRequest("invalid_dates", "The end date must be after the start date.");
        }
        Term term = new Term();
        term.setSchoolId(schoolId());
        term.setAcademicYearId(academicYearId);
        term.setName(name);
        term.setSeq(seq);
        term.setStartsOn(startsOn);
        term.setEndsOn(endsOn);
        if (current) {
            clearCurrentTerm();
            term.setCurrent(true);
        }
        return toTermView(termRepository.save(term));
    }

    public TermView setCurrentTerm(UUID id) {
        Term term = requireTerm(id);
        clearCurrentTerm();
        term.setCurrent(true);
        return toTermView(termRepository.save(term));
    }

    public void deleteTerm(UUID id) {
        Term term = requireTerm(id);
        softDelete(term);
        termRepository.save(term);
    }

    private void clearCurrentTerm() {
        termRepository.findBySchoolIdAndIsCurrentTrueAndDeletedAtIsNull(schoolId()).ifPresent(existing -> {
            existing.setCurrent(false);
            termRepository.save(existing);
        });
    }

    // ----- departments -----

    public List<DepartmentView> listDepartments() {
        return departmentRepository.findBySchoolIdAndDeletedAtIsNullOrderByName(schoolId()).stream()
                .map(d -> new DepartmentView(d.getId(), d.getName(), d.getCode(), d.getHeadMembershipId())).toList();
    }

    public DepartmentView createDepartment(String name, String code, UUID headMembershipId) {
        if (departmentRepository.existsBySchoolIdAndNameAndDeletedAtIsNull(schoolId(), name)) {
            throw ApiException.conflict("department_exists", "A department with this name already exists.");
        }
        Department department = new Department();
        department.setSchoolId(schoolId());
        department.setName(name);
        department.setCode(code);
        department.setHeadMembershipId(headMembershipId);
        department = departmentRepository.save(department);
        return new DepartmentView(department.getId(), department.getName(), department.getCode(),
                department.getHeadMembershipId());
    }

    public DepartmentView updateDepartment(UUID id, String name, String code, UUID headMembershipId) {
        Department department = departmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("department_not_found", "Department not found."));
        if (name != null) {
            department.setName(name);
        }
        if (code != null) {
            department.setCode(code);
        }
        if (headMembershipId != null) {
            department.setHeadMembershipId(headMembershipId);
        }
        department = departmentRepository.save(department);
        return new DepartmentView(department.getId(), department.getName(), department.getCode(),
                department.getHeadMembershipId());
    }

    public void deleteDepartment(UUID id) {
        Department department = departmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("department_not_found", "Department not found."));
        softDelete(department);
        departmentRepository.save(department);
    }

    // ----- classes -----

    public List<ClassView> listClasses() {
        List<ClassGroup> classes = classRepository.findBySchoolIdAndDeletedAtIsNullOrderByLevelAscNameAsc(schoolId());
        return classes.stream().map(c -> new ClassView(c.getId(), c.getName(), c.getLevel(), c.getDepartmentId(),
                c.getClassTeacherMembershipId(), c.getCapacity(), streamNames(c.getId()))).toList();
    }

    public ClassView createClass(String name, Integer level, UUID departmentId, UUID classTeacherMembershipId,
                                 Integer capacity) {
        if (classRepository.existsBySchoolIdAndNameAndDeletedAtIsNull(schoolId(), name)) {
            throw ApiException.conflict("class_exists", "A class with this name already exists.");
        }
        ClassGroup classGroup = new ClassGroup();
        classGroup.setSchoolId(schoolId());
        classGroup.setName(name);
        classGroup.setLevel(level);
        classGroup.setDepartmentId(departmentId);
        classGroup.setClassTeacherMembershipId(classTeacherMembershipId);
        classGroup.setCapacity(capacity);
        classGroup = classRepository.save(classGroup);
        return new ClassView(classGroup.getId(), classGroup.getName(), classGroup.getLevel(),
                classGroup.getDepartmentId(), classGroup.getClassTeacherMembershipId(), classGroup.getCapacity(),
                List.of());
    }

    public ClassView updateClass(UUID id, String name, Integer level, UUID departmentId, Integer capacity) {
        ClassGroup classGroup = requireClass(id);
        if (name != null) {
            classGroup.setName(name);
        }
        if (level != null) {
            classGroup.setLevel(level);
        }
        if (departmentId != null) {
            classGroup.setDepartmentId(departmentId);
        }
        if (capacity != null) {
            classGroup.setCapacity(capacity);
        }
        classGroup = classRepository.save(classGroup);
        return new ClassView(classGroup.getId(), classGroup.getName(), classGroup.getLevel(),
                classGroup.getDepartmentId(), classGroup.getClassTeacherMembershipId(), classGroup.getCapacity(),
                streamNames(classGroup.getId()));
    }

    public void deleteClass(UUID id) {
        ClassGroup classGroup = requireClass(id);
        softDelete(classGroup);
        classRepository.save(classGroup);
    }

    private List<String> streamNames(UUID classGroupId) {
        return streamRepository.findBySchoolIdAndClassGroupIdAndDeletedAtIsNullOrderByName(schoolId(), classGroupId)
                .stream().map(Stream::getName).toList();
    }

    // ----- streams -----

    public List<StreamView> listStreams(UUID classGroupId) {
        List<Stream> streams = classGroupId == null
                ? streamRepository.findBySchoolIdAndDeletedAtIsNull(schoolId())
                : streamRepository.findBySchoolIdAndClassGroupIdAndDeletedAtIsNullOrderByName(schoolId(), classGroupId);
        return streams.stream().map(s -> new StreamView(s.getId(), s.getClassGroupId(), s.getName())).toList();
    }

    public StreamView createStream(UUID classGroupId, String name) {
        requireClass(classGroupId);
        Stream stream = new Stream();
        stream.setSchoolId(schoolId());
        stream.setClassGroupId(classGroupId);
        stream.setName(name);
        stream = streamRepository.save(stream);
        return new StreamView(stream.getId(), stream.getClassGroupId(), stream.getName());
    }

    public void deleteStream(UUID id) {
        Stream stream = streamRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("stream_not_found", "Stream not found."));
        softDelete(stream);
        streamRepository.save(stream);
    }

    // ----- subjects -----

    public List<SubjectView> listSubjects() {
        return subjectRepository.findBySchoolIdAndDeletedAtIsNullOrderByName(schoolId()).stream()
                .map(s -> new SubjectView(s.getId(), s.getName(), s.getCode(), s.getDepartmentId(), s.isCore()))
                .toList();
    }

    public SubjectView createSubject(String name, String code, UUID departmentId, boolean core) {
        if (subjectRepository.existsBySchoolIdAndNameAndDeletedAtIsNull(schoolId(), name)) {
            throw ApiException.conflict("subject_exists", "A subject with this name already exists.");
        }
        Subject subject = new Subject();
        subject.setSchoolId(schoolId());
        subject.setName(name);
        subject.setCode(code);
        subject.setDepartmentId(departmentId);
        subject.setCore(core);
        subject = subjectRepository.save(subject);
        return new SubjectView(subject.getId(), subject.getName(), subject.getCode(), subject.getDepartmentId(),
                subject.isCore());
    }

    public SubjectView updateSubject(UUID id, String name, String code, UUID departmentId, Boolean core) {
        Subject subject = subjectRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("subject_not_found", "Subject not found."));
        if (name != null) {
            subject.setName(name);
        }
        if (code != null) {
            subject.setCode(code);
        }
        if (departmentId != null) {
            subject.setDepartmentId(departmentId);
        }
        if (core != null) {
            subject.setCore(core);
        }
        subject = subjectRepository.save(subject);
        return new SubjectView(subject.getId(), subject.getName(), subject.getCode(), subject.getDepartmentId(),
                subject.isCore());
    }

    public void deleteSubject(UUID id) {
        Subject subject = subjectRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("subject_not_found", "Subject not found."));
        softDelete(subject);
        subjectRepository.save(subject);
    }

    // ----- classrooms -----

    public List<RoomView> listClassrooms() {
        return classroomRepository.findBySchoolIdAndDeletedAtIsNullOrderByName(schoolId()).stream()
                .map(r -> new RoomView(r.getId(), r.getName(), r.getRoomType(), r.getCapacity())).toList();
    }

    public RoomView createClassroom(String name, String roomType, Integer capacity) {
        Classroom room = new Classroom();
        room.setSchoolId(schoolId());
        room.setName(name);
        room.setRoomType(roomType == null ? "standard" : roomType);
        room.setCapacity(capacity);
        room = classroomRepository.save(room);
        return new RoomView(room.getId(), room.getName(), room.getRoomType(), room.getCapacity());
    }

    public void deleteClassroom(UUID id) {
        Classroom room = classroomRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("classroom_not_found", "Classroom not found."));
        softDelete(room);
        classroomRepository.save(room);
    }

    // ----- timetable periods -----

    public List<PeriodView> listPeriods(UUID academicYearId) {
        List<TimetablePeriod> periods = academicYearId == null
                ? periodRepository.findBySchoolIdAndDeletedAtIsNullOrderBySeq(schoolId())
                : periodRepository.findBySchoolIdAndAcademicYearIdAndDeletedAtIsNullOrderBySeq(schoolId(),
                academicYearId);
        return periods.stream().map(p -> new PeriodView(p.getId(), p.getAcademicYearId(), p.getName(), p.getSeq(),
                p.getStartsAt(), p.getEndsAt(), p.isBreak())).toList();
    }

    public PeriodView createPeriod(UUID academicYearId, String name, int seq, LocalTime startsAt, LocalTime endsAt,
                                   boolean isBreak) {
        if (!endsAt.isAfter(startsAt)) {
            throw ApiException.badRequest("invalid_times", "The end time must be after the start time.");
        }
        TimetablePeriod period = new TimetablePeriod();
        period.setSchoolId(schoolId());
        period.setAcademicYearId(academicYearId);
        period.setName(name);
        period.setSeq(seq);
        period.setStartsAt(startsAt);
        period.setEndsAt(endsAt);
        period.setBreak(isBreak);
        period = periodRepository.save(period);
        return new PeriodView(period.getId(), period.getAcademicYearId(), period.getName(), period.getSeq(),
                period.getStartsAt(), period.getEndsAt(), period.isBreak());
    }

    public void deletePeriod(UUID id) {
        TimetablePeriod period = periodRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("period_not_found", "Period not found."));
        softDelete(period);
        periodRepository.save(period);
    }

    // ----- AcademicsApi (other modules) -----

    @Override
    public java.util.Optional<ClassInfo> findClass(UUID schoolId, UUID classGroupId) {
        return classRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, classGroupId)
                .map(c -> new ClassInfo(c.getId(), c.getName()));
    }

    @Override
    public List<ClassInfo> findClasses(UUID schoolId, List<UUID> classGroupIds) {
        return classRepository.findBySchoolIdAndIdInAndDeletedAtIsNull(schoolId, classGroupIds).stream()
                .map(c -> new ClassInfo(c.getId(), c.getName())).toList();
    }

    @Override
    public java.util.Optional<SubjectInfo> findSubject(UUID schoolId, UUID subjectId) {
        return subjectRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, subjectId)
                .map(s -> new SubjectInfo(s.getId(), s.getName(), s.getCode()));
    }

    @Override
    public List<SubjectInfo> findSubjects(UUID schoolId, List<UUID> subjectIds) {
        return subjectRepository.findBySchoolIdAndIdInAndDeletedAtIsNull(schoolId, subjectIds).stream()
                .map(s -> new SubjectInfo(s.getId(), s.getName(), s.getCode())).toList();
    }

    // ----- helpers -----

    private UUID schoolId() {
        return TenantContext.schoolId();
    }

    private AcademicYear requireYear(UUID id) {
        return yearRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("year_not_found", "Academic year not found."));
    }

    private Term requireTerm(UUID id) {
        return termRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("term_not_found", "Term not found."));
    }

    private ClassGroup requireClass(UUID id) {
        return classRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("class_not_found", "Class not found."));
    }

    private void softDelete(com.schoolsphere.common.persistence.TenantEntity entity) {
        entity.setDeletedAt(Instant.now());
        entity.setUpdatedBy(TenantContext.actorUserId());
    }

    private YearView toYearView(AcademicYear year) {
        return new YearView(year.getId(), year.getName(), year.getStartsOn(), year.getEndsOn(), year.isActive());
    }

    private TermView toTermView(Term term) {
        return new TermView(term.getId(), term.getAcademicYearId(), term.getName(), term.getSeq(),
                term.getStartsOn(), term.getEndsOn(), term.isCurrent());
    }

    public record YearView(UUID id, String name, LocalDate startsOn, LocalDate endsOn, boolean active) {
    }

    public record TermView(UUID id, UUID academicYearId, String name, int seq, LocalDate startsOn, LocalDate endsOn,
                           boolean current) {
    }

    public record DepartmentView(UUID id, String name, String code, UUID headMembershipId) {
    }

    public record ClassView(UUID id, String name, Integer level, UUID departmentId, UUID classTeacherMembershipId,
                            Integer capacity, List<String> streams) {
    }

    public record StreamView(UUID id, UUID classGroupId, String name) {
    }

    public record SubjectView(UUID id, String name, String code, UUID departmentId, boolean core) {
    }

    public record RoomView(UUID id, String name, String roomType, Integer capacity) {
    }

    public record PeriodView(UUID id, UUID academicYearId, String name, int seq, LocalTime startsAt, LocalTime endsAt,
                             boolean isBreak) {
    }
}
