library;

class Assessment {
  const Assessment({
    required this.id,
    required this.title,
    this.status = 'DRAFT',
    this.totalMarks,
    this.durationMinutes,
  });

  final String id;
  final String title;
  final String status; // DRAFT, IN_REVIEW, APPROVED, SCHEDULED, CLOSED
  final int? totalMarks;
  final int? durationMinutes;

  factory Assessment.fromJson(Map<String, dynamic> json) => Assessment(
        id: '${json['id']}',
        title: '${json['title'] ?? json['name'] ?? ''}',
        status: '${json['status'] ?? 'DRAFT'}',
        totalMarks: (json['totalMarks'] as num?)?.toInt(),
        durationMinutes: (json['durationMinutes'] as num?)?.toInt(),
      );
}

class ExamSession {
  const ExamSession({
    required this.id,
    required this.title,
    this.status = 'SCHEDULED',
    this.startsAt,
    this.endsAt,
  });

  final String id;
  final String title;
  final String status;
  final String? startsAt;
  final String? endsAt;

  factory ExamSession.fromJson(Map<String, dynamic> json) => ExamSession(
        id: '${json['id']}',
        title: '${json['title'] ?? json['name'] ?? ''}',
        status: '${json['status'] ?? 'SCHEDULED'}',
        startsAt: json['startsAt']?.toString(),
        endsAt: json['endsAt']?.toString(),
      );
}
