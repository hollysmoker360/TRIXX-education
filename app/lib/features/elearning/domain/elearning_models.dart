library;

class Course {
  const Course({
    required this.id,
    required this.title,
    this.description = '',
    this.status = 'DRAFT',
    this.progress = 0,
  });

  final String id;
  final String title;
  final String description;
  final String status;
  final int progress;

  factory Course.fromJson(Map<String, dynamic> json) => Course(
        id: '${json['id']}',
        title: '${json['title'] ?? json['name'] ?? ''}',
        description: '${json['description'] ?? ''}',
        status: '${json['status'] ?? 'DRAFT'}',
        progress: (json['progress'] as num?)?.toInt() ?? 0,
      );
}

class Lesson {
  const Lesson({
    required this.id,
    required this.title,
    this.type = 'TEXT',
    this.completed = false,
    this.durationMinutes,
  });

  final String id;
  final String title;
  final String type; // TEXT, VIDEO, AUDIO, PDF, QUIZ
  final bool completed;
  final int? durationMinutes;

  factory Lesson.fromJson(Map<String, dynamic> json) => Lesson(
        id: '${json['id']}',
        title: '${json['title'] ?? json['name'] ?? ''}',
        type: '${json['type'] ?? 'TEXT'}',
        completed: json['completed'] == true,
        durationMinutes: (json['durationMinutes'] as num?)?.toInt(),
      );
}

class Assignment {
  const Assignment({
    required this.id,
    required this.title,
    this.dueAt,
    this.submissionState = 'NOT_SUBMITTED',
    this.maxScore,
  });

  final String id;
  final String title;
  final String? dueAt;
  final String submissionState;
  final int? maxScore;

  factory Assignment.fromJson(Map<String, dynamic> json) => Assignment(
        id: '${json['id']}',
        title: '${json['title'] ?? ''}',
        dueAt: json['dueAt']?.toString(),
        submissionState: '${json['submissionState'] ?? 'NOT_SUBMITTED'}',
        maxScore: (json['maxScore'] as num?)?.toInt(),
      );
}
