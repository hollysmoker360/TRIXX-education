/// Navigation paths for the Trixx Education client.
///
/// Kept deliberately free of screen imports so both the router and the shell can
/// depend on it without a cycle.
abstract final class AppRoutes {
  static const landing = '/landing';
  static const login = '/login';
  static const acceptInvitation = '/accept-invitation';
  static const superAdmin = '/super-admin';
  static const resetPassword = '/reset-password';
  static const mfa = '/mfa';
  static const selectSchool = '/select-school';
  static const sessionExpired = '/session-expired';
  static const home = '/';

  static const notifications = '/notifications';
  static const gallery = '/settings/gallery';
  static const audit = '/audit';

  static const studentProfile = '/students/:studentId';
  static const studentReferral = '/student-success/referral';
  static const reportsGradeDistribution = '/reports/grade-distribution';
  static const reportsSubjectComparison = '/reports/subject-comparison';
  static const reportsAssessmentCompletion = '/reports/assessment-completion';
  static const reportsTermOverTerm = '/reports/term-over-term';
  static const reportsCompetencyMastery = '/reports/competency-mastery';
  static const reportsEvidenceCoverage = '/reports/evidence-coverage';
  static const reportsAttendancePatterns = '/reports/attendance-patterns';
  static const reportsStudentEngagement = '/reports/student-engagement';
  static const reportsCollectionSummary = '/reports/collection-summary';
  static const reportsExpenditure = '/reports/expenditure';
  static const reportsEnrollment = '/reports/enrollment';
  static const reportsStaffing = '/reports/staffing';
  static const reportCard = 'report-card';
  static const marksEntry = '/assessments/:assessmentId/marks';
  static const chatRoom = '/chat/:channelId';
  static const quizAttempt = '/quizzes/:quizId/attempt';
  static const attendanceRegister = '/attendance/register/:sessionId';
  static const attendanceHistory = '/attendance/history';
  static const assessmentEntry = '/grades/assessment-entry';
  static const classGradebook = '/grades/class/:classId/gradebook';

  static String studentProfileLocation(String studentId) => '/students/$studentId';
  static String reportCardLocation(String studentId) => '/students/$studentId/report-card';
  static String marksEntryLocation(String assessmentId) => '/assessments/$assessmentId/marks';
  static String chatRoomLocation(String channelId) => '/chat/$channelId';
  static String quizAttemptLocation(String quizId) => '/quizzes/$quizId/attempt';
  static String attendanceRegisterLocation(String sessionId) => '/attendance/register/$sessionId';
}
