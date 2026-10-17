/// CBE curriculum domain models (spec: CBE, E-Learning, Examinations & Student Success).

library;

class CurriculumVersion {
  const CurriculumVersion({
    required this.id,
    required this.code,
    required this.title,
    required this.status,
    required this.version,
  });

  final String id;
  final String code;
  final String title;
  final String status; // DRAFT, IN_REVIEW, APPROVED, PUBLISHED, RETIRED
  final int version;

  factory CurriculumVersion.fromJson(Map<String, dynamic> json) => CurriculumVersion(
        id: '${json['id']}',
        code: '${json['code'] ?? ''}',
        title: '${json['title'] ?? json['name'] ?? ''}',
        status: '${json['status'] ?? 'DRAFT'}',
        version: (json['version'] as num?)?.toInt() ?? 1,
      );
}

class Competency {
  const Competency({
    required this.id,
    required this.code,
    required this.title,
    required this.description,
    this.parentId,
    this.status = 'DRAFT',
    this.evidenceTypes = const [],
  });

  final String id;
  final String code;
  final String title;
  final String description;
  final String? parentId;
  final String status;
  final List<String> evidenceTypes;

  factory Competency.fromJson(Map<String, dynamic> json) => Competency(
        id: '${json['id']}',
        code: '${json['code'] ?? ''}',
        title: '${json['title'] ?? ''}',
        description: '${json['description'] ?? ''}',
        parentId: json['parentId']?.toString(),
        status: '${json['status'] ?? 'DRAFT'}',
        evidenceTypes: (json['evidenceTypes'] as List?)?.map((e) => '$e').toList() ?? const [],
      );
}

class RubricCriterion {
  const RubricCriterion({
    required this.id,
    required this.title,
    this.description = '',
    this.levels = const [],
  });

  final String id;
  final String title;
  final String description;
  final List<String> levels;

  factory RubricCriterion.fromJson(Map<String, dynamic> json) => RubricCriterion(
        id: '${json['id']}',
        title: '${json['title'] ?? ''}',
        description: '${json['description'] ?? ''}',
        levels: (json['levels'] as List?)?.map((e) => '$e').toList() ?? const [],
      );
}

class Rubric {
  const Rubric({
    required this.id,
    required this.title,
    this.criteria = const [],
  });

  final String id;
  final String title;
  final List<RubricCriterion> criteria;

  factory Rubric.fromJson(Map<String, dynamic> json) => Rubric(
        id: '${json['id']}',
        title: '${json['title'] ?? ''}',
        criteria: (json['criteria'] as List?)
                ?.map((e) => RubricCriterion.fromJson(e as Map<String, dynamic>))
                .toList() ??
            const [],
      );
}
