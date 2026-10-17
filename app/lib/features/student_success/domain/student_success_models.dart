library;

class LearnerProgress {
  const LearnerProgress({
    required this.studentId,
    this.masteryPercent = 0,
    this.evidenceCount = 0,
    this.pendingInterventions = 0,
  });

  final String studentId;
  final int masteryPercent;
  final int evidenceCount;
  final int pendingInterventions;

  factory LearnerProgress.fromJson(Map<String, dynamic> json) => LearnerProgress(
        studentId: '${json['studentId'] ?? json['id'] ?? ''}',
        masteryPercent: (json['masteryPercent'] as num?)?.toInt() ?? 0,
        evidenceCount: (json['evidenceCount'] as num?)?.toInt() ?? 0,
        pendingInterventions: (json['pendingInterventions'] as num?)?.toInt() ?? 0,
      );
}

class Intervention {
  const Intervention({
    required this.id,
    required this.goal,
    this.status = 'DRAFT',
    this.reviewDate,
  });

  final String id;
  final String goal;
  final String status; // DRAFT, ACTIVE, UNDER_REVIEW, COMPLETED, CANCELLED
  final String? reviewDate;

  factory Intervention.fromJson(Map<String, dynamic> json) => Intervention(
        id: '${json['id']}',
        goal: '${json['goal'] ?? ''}',
        status: '${json['status'] ?? 'DRAFT'}',
        reviewDate: json['reviewDate']?.toString(),
      );
}
