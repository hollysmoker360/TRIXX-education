import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../session/session.dart';

/// English and Kiswahili string catalogue (specification sections 2.7, 9 and 12).
///
/// The translation content also lives in `lib/l10n/app_en.arb` and
/// `lib/l10n/app_sw.arb` (the source of record for translators); this runtime
/// catalogue mirrors it so the build stays free of code-generation steps. Keep
/// the two in step when strings change.
///
/// Copy follows the voice rules: plain, active, sentence case; buttons say what
/// happens; errors say how to fix the problem; empty states invite an action.
class S {
  const S._(this._values);

  final Map<String, String> _values;

  static const en = S._(_en);
  static const sw = S._(_sw);

  static S of(BuildContext context) => LocaleScope.of(context);

  String _v(String key) => _values[key] ?? _en[key] ?? key;

  /// Keys defined in one catalogue but not the other. Both sets are empty when
  /// English and Kiswahili are in step. The parity test asserts this, so a
  /// missing translation fails the build instead of silently falling back to
  /// English at runtime.
  static ({Set<String> missingInSw, Set<String> missingInEn}) get parityIssues {
    final enKeys = _en.keys.toSet();
    final swKeys = _sw.keys.toSet();
    return (
      missingInSw: enKeys.difference(swKeys),
      missingInEn: swKeys.difference(enKeys),
    );
  }

  // --- Brand and session ---------------------------------------------------
  String get appName => _v('appName');
  String get tagline => _v('tagline');
  String get signIn => _v('signIn');
  String get email => _v('email');
  String get password => _v('password');
  String get signingIn => _v('signingIn');
  String get signOut => _v('signOut');
  String get profile => _v('profile');
  String get forgotPassword => _v('forgotPassword');
  String get showPassword => _v('showPassword');
  String get hidePassword => _v('hidePassword');
  String get resetPassword => _v('resetPassword');
  String get resetPasswordHelp => _v('resetPasswordHelp');
  String get sendResetLink => _v('sendResetLink');
  String get checkYourEmail => _v('checkYourEmail');
  String get backToSignIn => _v('backToSignIn');
  String get verificationCode => _v('verificationCode');
  String get verify => _v('verify');
  String get mfaTitle => _v('mfaTitle');
  String get mfaHelp => _v('mfaHelp');
  String get useRecoveryCode => _v('useRecoveryCode');
  String get selectSchoolTitle => _v('selectSchoolTitle');
  String get sessionExpired => _v('sessionExpired');
  String get sessionExpiredHelp => _v('sessionExpiredHelp');
  String get staySignedIn => _v('staySignedIn');
  String get sessionExpiringSoon => _v('sessionExpiringSoon');
  String get demoAccounts => _v('demoAccounts');
  String get demoPasswordHint => _v('demoPasswordHint');

  // --- Navigation ----------------------------------------------------------
  String get overview => _v('overview');
  String get timetable => _v('timetable');
  String get attendance => _v('attendance');
  String get assignments => _v('assignments');
  String get quizzes => _v('quizzes');
  String get tasks => _v('tasks');
  String get messages => _v('messages');
  String get people => _v('people');
  String get academics => _v('academics');
  String get reports => _v('reports');
  String get resources => _v('resources');
  String get syncCenter => _v('syncCenter');
  String get settings => _v('settings');
  String get more => _v('more');
  String get notifications => _v('notifications');
  String get marks => _v('marks');
  String get learn => _v('learn');
  String get family => _v('family');
  String get finances => _v('finances');
  String get commandGallery => _v('commandGallery');
  String get audit => _v('audit');
  String get search => _v('search');
  String get commandPaletteHint => _v('commandPaletteHint');
  String get noMatches => _v('noMatches');

  // --- Common actions ------------------------------------------------------
  String get retry => _v('retry');
  String get refresh => _v('refresh');
  String get save => _v('save');
  String get saveChanges => _v('saveChanges');
  String get saved => _v('saved');
  String get saving => _v('saving');
  String get unsavedChanges => _v('unsavedChanges');
  String get unsavedChangesHelp => _v('unsavedChangesHelp');
  String get discard => _v('discard');
  String get keepEditing => _v('keepEditing');
  String get cancel => _v('cancel');
  String get confirm => _v('confirm');
  String get close => _v('close');
  String get done => _v('done');
  String get create => _v('create');
  String get edit => _v('edit');
  String get delete => _v('delete');
  String get submit => _v('submit');
  String get send => _v('send');
  String get publish => _v('publish');
  String get publishResults => _v('publishResults');
  String get resultsPublished => _v('resultsPublished');
  String get sendReminder => _v('sendReminder');
  String get clear => _v('clear');
  String get clearAll => _v('clearAll');
  String get clearSelection => _v('clearSelection');
  String get select => _v('select');
  String get undo => _v('undo');
  String get goBack => _v('goBack');
  String get details => _v('details');
  String get review => _v('review');
  String get moreActions => _v('moreActions');
  String get viewAll => _v('viewAll');
  String get next => _v('next');
  String get previous => _v('previous');
  String get add => _v('add');
  String get remove => _v('remove');
  String get apply => _v('apply');
  String get reset => _v('reset');
  String get open => _v('open');
  String get download => _v('download');
  String get upload => _v('upload');
  String get export => _v('export');
  String get printReport => _v('printReport');

  // --- States --------------------------------------------------------------
  String get loading => _v('loading');
  String get emptyList => _v('emptyList');
  String get somethingWentWrong => _v('somethingWentWrong');
  String get networkError => _v('networkError');
  String get networkErrorHelp => _v('networkErrorHelp');
  String get accessDenied => _v('accessDenied');
  String get accessDeniedHelp => _v('accessDeniedHelp');
  String get notFound => _v('notFound');
  String get notFoundHelp => _v('notFoundHelp');
  String get referenceLabel => _v('referenceLabel');
  String get masked => _v('masked');
  String get noPermission => _v('noPermission');
  String get staleData => _v('staleData');
  String get updatedAgo => _v('updatedAgo');

  // --- Offline and sync ----------------------------------------------------
  String get offlineSaved => _v('offlineSaved');
  String get offlineQueued => _v('offlineQueued');
  String get pendingChanges => _v('pendingChanges');
  String get syncingChanges => _v('syncingChanges');
  String get syncingLabel => _v('syncingLabel');
  String get syncNow => _v('syncNow');
  String get syncFailed => _v('syncFailed');
  String get needsReview => _v('needsReview');
  String get upToDate => _v('upToDate');
  String get heroTodaySubtitle => _v('heroTodaySubtitle');
  String get connection => _v('connection');
  String get server => _v('server');
  String get online => _v('online');
  String get offline => _v('offline');
  String get lastSynced => _v('lastSynced');
  String get lastError => _v('lastError');
  String get never => _v('never');
  String get justNow => _v('justNow');
  String get minutesAgo => _v('minutesAgo');
  String get hoursAgo => _v('hoursAgo');
  String get daysAgo => _v('daysAgo');
  String get savedOnDevice => _v('savedOnDevice');
  String get savedToServer => _v('savedToServer');

  // --- Conflicts and confirmations ----------------------------------------
  String get conflictTitle => _v('conflictTitle');
  String get conflictIntro => _v('conflictIntro');
  String get changedFields => _v('changedFields');
  String get localVersion => _v('localVersion');
  String get serverVersion => _v('serverVersion');
  String get useServer => _v('useServer');
  String get keepLocal => _v('keepLocal');
  String get noFieldChanges => _v('noFieldChanges');
  String get selectDates => _v('selectDates');

  // --- Language, theme, accessibility --------------------------------------
  String get language => _v('language');
  String get english => _v('english');
  String get kiswahili => _v('kiswahili');
  String get darkTheme => _v('darkTheme');
  String get lowBandwidthMode => _v('lowBandwidthMode');
  String get lowBandwidthHelp => _v('lowBandwidthHelp');
  String get reduceMotion => _v('reduceMotion');

  // --- Tables and charts ---------------------------------------------------
  String get showChart => _v('showChart');
  String get showTable => _v('showTable');
  String get tableSummary => _v('tableSummary');
  String get sortAscending => _v('sortAscending');
  String get sortDescending => _v('sortDescending');

  // --- Upload --------------------------------------------------------------
  String get chooseFile => _v('chooseFile');
  String get replaceFile => _v('replaceFile');

  // --- School context ------------------------------------------------------
  String get switchSchool => _v('switchSchool');
  String get noSchool => _v('noSchool');
  String get selectSchool => _v('selectSchool');
  String get notAMember => _v('notAMember');

  // --- Overview / dashboards ----------------------------------------------
  String get today => _v('today');
  String get dueSoon => _v('dueSoon');
  String get goodMorning => _v('goodMorning');
  String get goodAfternoon => _v('goodAfternoon');
  String get goodEvening => _v('goodEvening');
  String get whatToDoNext => _v('whatToDoNext');
  String get nextLessons => _v('nextLessons');
  String get attendanceNotTaken => _v('attendanceNotTaken');
  String get workToMark => _v('workToMark');
  String get recentFeedback => _v('recentFeedback');
  String get upcomingAssessments => _v('upcomingAssessments');
  String get pendingActions => _v('pendingActions');
  String get upcomingEvents => _v('upcomingEvents');
  String get onboardingProgress => _v('onboardingProgress');
  String get students => _v('students');
  String get staff => _v('staff');
  String get classes => _v('classes');
  String get subjects => _v('subjects');
  String get attendanceToday => _v('attendanceToday');
  String get enrolledStudents => _v('enrolledStudents');
  String get teachingStaff => _v('teachingStaff');
  String get classCount => _v('classCount');
  String get subjectCount => _v('subjectCount');

  // --- Attendance ----------------------------------------------------------
  String get markPresent => _v('markPresent');
  String get markAbsent => _v('markAbsent');
  String get markLate => _v('markLate');
  String get markExcused => _v('markExcused');
  String get openSession => _v('openSession');
  String get closeSession => _v('closeSession');
  String get markAllPresent => _v('markAllPresent');
  String get savedLocally => _v('savedLocally');
  String get attendanceSaved => _v('attendanceSaved');
  String get registerClosed => _v('registerClosed');
  String get reason => _v('reason');
  String get correctionRequest => _v('correctionRequest');
  String get originalValue => _v('originalValue');
  String get proposedValue => _v('proposedValue');
  String get justification => _v('justification');
  String get requestCorrection => _v('requestCorrection');

  // --- Learning ------------------------------------------------------------
  String get lessons => _v('lessons');
  String get classGroup => _v('classGroup');
  String get stream => _v('stream');
  String get subject => _v('subject');
  String get topic => _v('topic');
  String get openRoom => _v('openRoom');
  String get joinLesson => _v('joinLesson');
  String get typeMessage => _v('typeMessage');

  /// Real-time chat status and message states (specification section 5.3).
  String get chatLive => _v('chatLive');
  String get chatReconnecting => _v('chatReconnecting');
  String get chatPolling => _v('chatPolling');
  String get messageDeleted => _v('messageDeleted');
  String typingNow(String name) => _v('typingNow').replaceAll('%name%', name);
  String get grade => _v('grade');
  String get feedback => _v('feedback');
  String get deadline => _v('deadline');
  String get submissions => _v('submissions');
  String get markingWorkspace => _v('markingWorkspace');
  String get autosaved => _v('autosaved');
  String get questionTypes => _v('questionTypes');
  String get timeLimit => _v('timeLimit');
  String get submitQuiz => _v('submitQuiz');
  String get submissionConfirmation => _v('submissionConfirmation');
  String get completion => _v('completion');
  String get bookmarks => _v('bookmarks');
  String get suggestedTasks => _v('suggestedTasks');

  // --- Assessment and results ---------------------------------------------
  String get assessmentSetup => _v('assessmentSetup');
  String get marksEntry => _v('marksEntry');
  String get gradebook => _v('gradebook');
  String get moderation => _v('moderation');
  String get draft => _v('draft');
  String get inReview => _v('inReview');
  String get published => _v('published');
  String get reportCard => _v('reportCard');
  String get total => _v('total');
  String get weight => _v('weight');
  String get missingMarks => _v('missingMarks');
  String get publishConfirm => _v('publishConfirm');
  String get notifyGuardians => _v('notifyGuardians');

  // --- Timetable -----------------------------------------------------------
  String get timetableManage => _v('timetableManage');
  String get generate => _v('generate');
  String get validate => _v('validate');
  String get conflicts => _v('conflicts');
  String get conflictReason => _v('conflictReason');
  String get publishTimetable => _v('publishTimetable');
  String get weekGrid => _v('weekGrid');
  String get dayAgenda => _v('dayAgenda');
  String get period => _v('period');
  String get room => _v('room');
  String get teacher => _v('teacher');

  // --- Subscriptions -------------------------------------------------------
  String get subscription => _v('subscription');
  String get currentPlan => _v('currentPlan');
  String get plan => _v('plan');
  String get status => _v('status');
  String get active => _v('active');
  String get trial => _v('trial');
  String get pastDue => _v('pastDue');
  String get suspended => _v('suspended');
  String get cancelled => _v('cancelled');
  String get entitlements => _v('entitlements');
  String get maxStudents => _v('maxStudents');
  String get maxTeachers => _v('maxTeachers');
  String get features => _v('features');
  String get upgrade => _v('upgrade');
  String get checkout => _v('checkout');
  String get payNow => _v('payNow');
  String get paymentHistory => _v('paymentHistory');
  String get reference => _v('reference');
  String get amount => _v('amount');
  String get date => _v('date');
  String get cancelSubscription => _v('cancelSubscription');
  String get resumeSubscription => _v('resumeSubscription');
  String get confirmCancel => _v('confirmCancel');
  String get cancelConfirmText => _v('cancelConfirmText');
  String get provider => _v('provider');
  String get paymentInitiated => _v('paymentInitiated');
  String get paymentPending => _v('paymentPending');
  String get paymentConfirmed => _v('paymentConfirmed');
  String get paymentFailed => _v('paymentFailed');
  String get paymentRefunded => _v('paymentRefunded');

  // --- Notifications and communication ------------------------------------
  String get notificationSettings => _v('notificationSettings');
  String get inApp => _v('inApp');
  String get emailChannel => _v('emailChannel');
  String get smsChannel => _v('smsChannel');
  String get quietHours => _v('quietHours');
  String get markAllRead => _v('markAllRead');
  String get noNotifications => _v('noNotifications');
  String get announcement => _v('announcement');
  String get audience => _v('audience');
  String get channels => _v('channels');
  String get deliveryLog => _v('deliveryLog');
  String get notifyPreviewNote => _v('notifyPreviewNote');

  // --- People --------------------------------------------------------------
  String get studentDirectory => _v('studentDirectory');
  String get staffDirectory => _v('staffDirectory');
  String get admissions => _v('admissions');
  String get guardians => _v('guardians');
  String get enrolment => _v('enrolment');
  String get documents => _v('documents');
  String get bulkImport => _v('bulkImport');
  String get csvTemplate => _v('csvTemplate');
  String get columnMapping => _v('columnMapping');
  String get preview => _v('preview');
  String get importComplete => _v('importComplete');
  String get relation => _v('relation');
  String get contactPreferences => _v('contactPreferences');

  // --- Data subject --------------------------------------------------------
  String get myData => _v('myData');
  String get exportData => _v('exportData');
  String get correctData => _v('correctData');
  String get requestDeletion => _v('requestDeletion');
  String get retentionPeriod => _v('retentionPeriod');

  // --- Security ------------------------------------------------------------
  String get mustReauthenticate => _v('mustReauthenticate');
  String get stepUpHelp => _v('stepUpHelp');
  String get signOutAllDevices => _v('signOutAllDevices');
  String get trustedDevices => _v('trustedDevices');

  // --- Gallery -------------------------------------------------------------
  String get componentGallery => _v('componentGallery');
  String get galleryHint => _v('galleryHint');

  // --- Parameterised -------------------------------------------------------
  String uploadHint(String extensions, int megabytes) => _v('uploadHint')
      .replaceAll('%ext%', extensions)
      .replaceAll('%mb%', '$megabytes');

  String typeToConfirm(String value) => _v('typeToConfirm').replaceAll('%value%', value);

  String selectedCount(int count) => _v('selectedCount').replaceAll('%count%', '$count');

  String classesNeedAttendance(int count) =>
      _v('classesNeedAttendance').replaceAll('%count%', '$count');

  String dueInDays(int days) => _v('dueInDays').replaceAll('%days%', '$days');

  String signedInAs(String name) => _v('signedInAs').replaceAll('%name%', name);

  // --- Screen-level labels migrated off hardcoded literals -----------------
  String get notSet => _v('notSet');
  String get setCurrent => _v('setCurrent');
  String get submittedWaitingGrading => _v('submittedWaitingGrading');
  String get pickDate => _v('pickDate');
  String get noReason => _v('noReason');
  String get before => _v('before');
  String get after => _v('after');
  String get selectClassFirst => _v('selectClassFirst');
  String get noRoomUrl => _v('noRoomUrl');
  String get allCaughtUp => _v('allCaughtUp');
  String get accountCreated => _v('accountCreated');
  String get noGuardiansLinked => _v('noGuardiansLinked');
  String get enroll => _v('enroll');
  String get linkGuardian => _v('linkGuardian');
  String get changeEnrollment => _v('changeEnrollment');
  String get start => _v('start');
  String get shuffleQuestions => _v('shuffleQuestions');
  String get addQuestion => _v('addQuestion');
  String get question => _v('question');
  String get multipleChoice => _v('multipleChoice');
  String get shortAnswer => _v('shortAnswer');
  String get answerIsTrue => _v('answerIsTrue');
  String get student => _v('student');
  String get rate => _v('rate');
  String get openResource => _v('openResource');
  String get inAppNotifications => _v('inAppNotifications');
  String get emailNotifications => _v('emailNotifications');
  String get smsNotifications => _v('smsNotifications');
  String get current => _v('current');
  String get openPaymentPage => _v('openPaymentPage');
  String get everythingSynced => _v('everythingSynced');
  String get priorityMedium => _v('priorityMedium');
  String get priorityHigh => _v('priorityHigh');
  String get timetableGenerated => _v('timetableGenerated');
  String get validationComplete => _v('validationComplete');
  String get timetablePublished => _v('timetablePublished');
  String get timetableArchived => _v('timetableArchived');
  String get createVersion => _v('createVersion');
  String get noVersionsYet => _v('noVersionsYet');
  String get priorityLow => _v('priorityLow');
  String get noDueDate => _v('noDueDate');
  String get priority => _v('priority');
  String get typeLabel => _v('typeLabel');
  String get promptLabel => _v('promptLabel');
  String get trueFalse => _v('trueFalse');
  String get optionsCommaSeparated => _v('optionsCommaSeparated');
  String get correctOptionNumber => _v('correctOptionNumber');
  String get noActiveEnrollment => _v('noActiveEnrollment');
  String get classLabel => _v('classLabel');
  String get searchPeople => _v('searchPeople');
  String get noMatchingPeople => _v('noMatchingPeople');
  String get when => _v('when');
  String get admShort => _v('admShort');
  String get noOptionsAvailable => _v('noOptionsAvailable');
  String get sharePasswordIntro => _v('sharePasswordIntro');
  String get admissionNumber => _v('admissionNumber');
  String get teachers => _v('teachers');
  String get parents => _v('parents');
  String get teachingAssignments => _v('teachingAssignments');

  // --- Form field labels ---------------------------------------------------
  String get fieldName => _v('fieldName');
  String get fieldAcademicYear => _v('fieldAcademicYear');
  String get fieldSequence => _v('fieldSequence');
  String get fieldStartDate => _v('fieldStartDate');
  String get fieldEndDate => _v('fieldEndDate');
  String get fieldCode => _v('fieldCode');
  String get fieldDepartment => _v('fieldDepartment');
  String get fieldGradeLevel => _v('fieldGradeLevel');
  String get fieldCapacity => _v('fieldCapacity');
  String get fieldRoomType => _v('fieldRoomType');
  String get fieldCoreSubject => _v('fieldCoreSubject');
  String get fieldStartTime => _v('fieldStartTime');
  String get fieldEndTime => _v('fieldEndTime');
  String get fieldBreakPeriod => _v('fieldBreakPeriod');
  String get fieldTerm => _v('fieldTerm');
  String get fieldPeriodsPerWeek => _v('fieldPeriodsPerWeek');
  String get fieldFirstName => _v('fieldFirstName');
  String get fieldOtherNames => _v('fieldOtherNames');
  String get fieldDateOfBirth => _v('fieldDateOfBirth');
  String get fieldGender => _v('fieldGender');
  String get fieldExistingAccount => _v('fieldExistingAccount');
  String get fieldPhone => _v('fieldPhone');
  String get fieldStaffNumber => _v('fieldStaffNumber');
  String get fieldMaxWeeklyPeriods => _v('fieldMaxWeeklyPeriods');
  String get fieldParentGuardian => _v('fieldParentGuardian');
  String get fieldPrimaryGuardian => _v('fieldPrimaryGuardian');
  String get fieldMayViewAttendance => _v('fieldMayViewAttendance');
  String get fieldMayViewGrades => _v('fieldMayViewGrades');
  String get fieldNewStatus => _v('fieldNewStatus');
  String get yourAnswer => _v('yourAnswer');
  String get instructionsLabel => _v('instructionsLabel');
  String get maxMarksLabel => _v('maxMarksLabel');
  String get notesLabel => _v('notesLabel');
  String get timeLimitMin => _v('timeLimitMin');
  String get maxAttempts => _v('maxAttempts');
  String get expectedAnswer => _v('expectedAnswer');
  String get pointsLabel => _v('pointsLabel');
  String get fieldLastName => _v('fieldLastName');
  String get fieldMale => _v('fieldMale');
  String get fieldFemale => _v('fieldFemale');
  String get fieldOther => _v('fieldOther');
  String get enrollmentLabel => _v('enrollmentLabel');
  String get myChildren => _v('myChildren');
  String get rejectedChanges => _v('rejectedChanges');
  String get fromDate => _v('fromDate');
  String get toDate => _v('toDate');
  String get archiveAction => _v('archiveAction');
  String get bookmark => _v('bookmark');
  String get breakLabel => _v('breakLabel');
  String get academicYears => _v('academicYears');
  String get terms => _v('terms');
  String get departments => _v('departments');
  String get streams => _v('streams');
  String get periods => _v('periods');
  String get classrooms => _v('classrooms');

  /// `Level %value%`
  String levelValue(String value) => _v('levelValue').replaceAll('%value%', value);

  /// `%value% seats`
  String seatsLabel(String value) => _v('seatsLabel').replaceAll('%value%', value);

  /// `Manage %name%`
  String manageLabel(String name) => _v('manageLabel').replaceAll('%name%', name);

  /// `%count% unmarked`
  String unmarkedCount(int count) => _v('unmarkedCount').replaceAll('%count%', '$count');
  String get titleLabel => _v('titleLabel');
  String get descriptionLabel => _v('descriptionLabel');

  /// `%field% is required.`
  String fieldRequired(String field) => _v('fieldRequired').replaceAll('%field%', field);
}

const _en = <String, String>{
  'appName': 'Trixx Education',
  'tagline': 'School management & learning',
  'signIn': 'Sign in',
  'email': 'Email',
  'password': 'Password',
  'signingIn': 'Signing in…',
  'signOut': 'Sign out',
  'profile': 'Profile',
  'forgotPassword': 'Forgot password?',
  'showPassword': 'Show password',
  'hidePassword': 'Hide password',
  'resetPassword': 'Reset password',
  'resetPasswordHelp': "Enter your email and we'll send a reset link if an account exists.",
  'sendResetLink': 'Send reset link',
  'checkYourEmail': 'Check your email',
  'backToSignIn': 'Back to sign in',
  'verificationCode': 'Verification code',
  'verify': 'Verify',
  'mfaTitle': 'Two-step verification',
  'mfaHelp': 'Enter the 6-digit code from your authenticator app.',
  'useRecoveryCode': 'Use a recovery code',
  'selectSchoolTitle': 'Choose a school',
  'sessionExpired': 'Session expired',
  'sessionExpiredHelp': 'Sign in again to continue where you left off.',
  'staySignedIn': 'Stay signed in',
  'sessionExpiringSoon': 'Your session expires soon.',
  'demoAccounts': 'Demo accounts',
  'demoPasswordHint': 'Password for every demo account: Demo1234!',
  'overview': 'Overview',
  'timetable': 'Timetable',
  'attendance': 'Attendance',
  'assignments': 'Assignments',
  'quizzes': 'Quizzes',
  'tasks': 'Tasks',
  'messages': 'Messages',
  'people': 'People',
  'academics': 'Academics',
  'reports': 'Reports',
  'resources': 'Learning Hub',
  'syncCenter': 'Sync',
  'settings': 'Settings',
  'more': 'More',
  'notifications': 'Notifications',
  'marks': 'Marks',
  'learn': 'Learn',
  'family': 'Family',
  'finances': 'Finances',
  'commandGallery': 'Component gallery',
  'audit': 'Audit log',
  'search': 'Search',
  'commandPaletteHint': 'Search people, classes, resources, settings…',
  'noMatches': 'No matches.',
  'retry': 'Retry',
  'refresh': 'Refresh',
  'save': 'Save',
  'saveChanges': 'Save changes',
  'saved': 'Saved',
  'saving': 'Saving…',
  'unsavedChanges': 'Unsaved changes',
  'unsavedChangesHelp': 'You have unsaved changes. Leave without saving?',
  'discard': 'Discard',
  'keepEditing': 'Keep editing',
  'cancel': 'Cancel',
  'confirm': 'Confirm',
  'close': 'Close',
  'done': 'Done',
  'create': 'Create',
  'edit': 'Edit',
  'delete': 'Delete',
  'submit': 'Submit',
  'send': 'Send',
  'publish': 'Publish',
  'publishResults': 'Publish results',
  'resultsPublished': 'Results published',
  'sendReminder': 'Send reminder',
  'clear': 'Clear',
  'clearAll': 'Clear all',
  'clearSelection': 'Clear selection',
  'select': 'Select',
  'undo': 'Undo',
  'goBack': 'Go back',
  'details': 'Details',
  'review': 'Review',
  'moreActions': 'More actions',
  'viewAll': 'View all',
  'next': 'Next',
  'previous': 'Previous',
  'add': 'Add',
  'remove': 'Remove',
  'apply': 'Apply',
  'reset': 'Reset',
  'open': 'Open',
  'download': 'Download',
  'upload': 'Upload',
  'export': 'Export',
  'printReport': 'Print report',
  'loading': 'Loading…',
  'emptyList': 'Nothing here yet.',
  'somethingWentWrong': 'Something went wrong',
  'networkError': "Can't reach the server",
  'networkErrorHelp': 'Check your connection and try again.',
  'accessDenied': "You don't have access to this page",
  'accessDeniedHelp': 'Ask a school administrator to grant you access.',
  'notFound': "We couldn't find that",
  'notFoundHelp': 'It may have been moved or removed.',
  'referenceLabel': 'Reference',
  'masked': 'Hidden for privacy',
  'noPermission': 'Not permitted',
  'staleData': 'Showing cached data',
  'updatedAgo': 'Updated %value% ago',
  'offlineSaved': 'You are offline. Changes are saved on this device',
  'offlineQueued': 'Saved offline — it will sync automatically.',
  'pendingChanges': 'pending changes',
  'syncingChanges': 'Syncing',
  'syncingLabel': 'Syncing…',
  'syncNow': 'Sync now',
  'syncFailed': 'Sync failed. Your work is safe.',
  'needsReview': 'Some changes need your review',
  'upToDate': 'Up to date',
  'heroTodaySubtitle': 'Here is what needs you today.',
  'connection': 'Connection',
  'server': 'Server',
  'online': 'Online',
  'offline': 'Offline',
  'lastSynced': 'Last synced',
  'lastError': 'Last error',
  'never': 'Never',
  'justNow': 'just now',
  'minutesAgo': 'm ago',
  'hoursAgo': 'h ago',
  'daysAgo': 'd ago',
  'savedOnDevice': 'Saved on this device',
  'savedToServer': 'Saved to the server',
  'conflictTitle': 'Resolve conflict',
  'conflictIntro': 'This record changed on the server while you were editing it.',
  'changedFields': 'Changed fields',
  'localVersion': 'Your version',
  'serverVersion': 'Server version',
  'useServer': 'Use server version',
  'keepLocal': 'Keep my version',
  'noFieldChanges': 'No field differences were detected.',
  'selectDates': 'Select dates',
  'language': 'Language',
  'english': 'English',
  'kiswahili': 'Kiswahili',
  'darkTheme': 'Dark theme',
  'lowBandwidthMode': 'Low-bandwidth mode',
  'lowBandwidthHelp': 'Defers images and media, and uses smaller lists.',
  'reduceMotion': 'Reduce motion',
  'showChart': 'Chart',
  'showTable': 'Table',
  'tableSummary': 'Data table alternative',
  'sortAscending': 'sorted ascending',
  'sortDescending': 'sorted descending',
  'chooseFile': 'Choose a file',
  'replaceFile': 'Replace file',
  'switchSchool': 'Switch school',
  'noSchool': 'No school selected',
  'selectSchool': 'Select school',
  'notAMember': 'Your account is not a member of any school yet. Ask a school administrator to invite you.',
  'today': 'Today',
  'dueSoon': 'Due soon',
  'goodMorning': 'Good morning',
  'goodAfternoon': 'Good afternoon',
  'goodEvening': 'Good evening',
  'whatToDoNext': 'What to do next',
  'nextLessons': 'Next lessons',
  'attendanceNotTaken': 'Attendance not taken',
  'workToMark': 'Work to mark',
  'recentFeedback': 'Recent feedback',
  'upcomingAssessments': 'Upcoming assessments',
  'pendingActions': 'Pending actions',
  'upcomingEvents': 'Upcoming events',
  'onboardingProgress': 'Setup progress',
  'students': 'Students',
  'staff': 'Staff',
  'classes': 'Classes',
  'subjects': 'Subjects',
  'attendanceToday': 'Attendance today',
  'enrolledStudents': 'Enrolled students',
  'teachingStaff': 'Teaching staff',
  'classCount': 'Classes',
  'subjectCount': 'Subjects',
  'markPresent': 'Present',
  'markAbsent': 'Absent',
  'markLate': 'Late',
  'markExcused': 'Excused',
  'openSession': 'Open register',
  'closeSession': 'Close register',
  'markAllPresent': 'Mark all present',
  'savedLocally': 'Saved on this device',
  'attendanceSaved': 'Attendance saved',
  'registerClosed': 'Register closed',
  'reason': 'Reason',
  'correctionRequest': 'Correction request',
  'originalValue': 'Original',
  'proposedValue': 'Proposed',
  'justification': 'Justification',
  'requestCorrection': 'Request correction',
  'lessons': 'Lessons',
  'classGroup': 'Class',
  'stream': 'Stream',
  'subject': 'Subject',
  'topic': 'Topic',
  'openRoom': 'Open room',
  'joinLesson': 'Join lesson',
  'typeMessage': 'Type a message…',
  'chatLive': 'Live',
  'chatReconnecting': 'Reconnecting…',
  'chatPolling': 'Updates every 15 seconds',
  'messageDeleted': 'This message was deleted.',
  'typingNow': '%name% is typing…',
  'grade': 'Grade',
  'feedback': 'Feedback',
  'deadline': 'Deadline',
  'submissions': 'Submissions',
  'markingWorkspace': 'Marking workspace',
  'autosaved': 'Autosaved',
  'questionTypes': 'Question types',
  'timeLimit': 'Time limit',
  'submitQuiz': 'Submit quiz',
  'submissionConfirmation': 'Your answers have been submitted.',
  'completion': 'Completion',
  'bookmarks': 'Bookmarks',
  'suggestedTasks': 'Suggested tasks',
  'assessmentSetup': 'Assessment setup',
  'marksEntry': 'Marks entry',
  'gradebook': 'Gradebook',
  'moderation': 'Moderation',
  'draft': 'Draft',
  'inReview': 'In review',
  'published': 'Published',
  'reportCard': 'Report card',
  'total': 'Total',
  'weight': 'Weight',
  'missingMarks': 'Missing marks',
  'publishConfirm': 'Once published, results are visible to guardians. Corrections create a new audited version.',
  'notifyGuardians': 'Notify guardians',
  'timetableManage': 'Manage timetable',
  'generate': 'Generate',
  'validate': 'Validate',
  'conflicts': 'Conflicts',
  'conflictReason': 'Reason',
  'publishTimetable': 'Publish timetable',
  'weekGrid': 'Week',
  'dayAgenda': 'Day',
  'period': 'Period',
  'room': 'Room',
  'teacher': 'Teacher',
  'subscription': 'Subscription',
  'currentPlan': 'Current plan',
  'plan': 'Plan',
  'status': 'Status',
  'active': 'Active',
  'trial': 'Trial',
  'pastDue': 'Past due',
  'suspended': 'Suspended',
  'cancelled': 'Cancelled',
  'entitlements': 'Entitlements',
  'maxStudents': 'Max students',
  'maxTeachers': 'Max teachers',
  'features': 'Features',
  'upgrade': 'Upgrade',
  'checkout': 'Checkout',
  'payNow': 'Pay now',
  'paymentHistory': 'Payment history',
  'reference': 'Reference',
  'amount': 'Amount',
  'date': 'Date',
  'cancelSubscription': 'Cancel subscription',
  'resumeSubscription': 'Resume subscription',
  'confirmCancel': 'Cancel subscription?',
  'cancelConfirmText': 'You will keep access until the end of the current billing period.',
  'provider': 'Provider',
  'paymentInitiated': 'Payment initiated',
  'paymentPending': 'Payment pending',
  'paymentConfirmed': 'Payment confirmed',
  'paymentFailed': 'Payment failed',
  'paymentRefunded': 'Refunded',
  'notificationSettings': 'Notifications',
  'inApp': 'In app',
  'emailChannel': 'Email',
  'smsChannel': 'SMS',
  'quietHours': 'Quiet hours',
  'markAllRead': 'Mark all read',
  'noNotifications': 'No notifications yet.',
  'announcement': 'Announcement',
  'audience': 'Audience',
  'channels': 'Channels',
  'deliveryLog': 'Delivery log',
  'notifyPreviewNote': 'Previews never include grades or balances — they say there is something new.',
  'studentDirectory': 'Students',
  'staffDirectory': 'Staff',
  'admissions': 'Admissions and students',
  'guardians': 'Guardians',
  'enrolment': 'Enrolment',
  'documents': 'Documents',
  'bulkImport': 'Bulk import',
  'csvTemplate': 'CSV template',
  'columnMapping': 'Column mapping',
  'preview': 'Preview',
  'importComplete': 'Import complete',
  'relation': 'Relationship',
  'contactPreferences': 'Contact preferences',
  'myData': 'My data',
  'exportData': 'Export my data',
  'correctData': 'Request a correction',
  'requestDeletion': 'Request deletion',
  'retentionPeriod': 'Retention period',
  'mustReauthenticate': 'Confirm it is you',
  'stepUpHelp': 'This action needs you to sign in again.',
  'signOutAllDevices': 'Sign out of all devices',
  'trustedDevices': 'Trusted devices',
  'componentGallery': 'Component gallery',
  'galleryHint': 'Every shared component in light and dark themes.',
  'uploadHint': 'Allowed: %ext%. Up to %mb% MB.',
  'typeToConfirm': 'Type %value% to confirm',
  'selectedCount': '%count% selected',
  'classesNeedAttendance': '%count% classes have no attendance today',
  'dueInDays': 'Due in %days% days',
  'signedInAs': 'Signed in as %name%',
  'notSet': 'Not set',
  'setCurrent': 'Set current',
  'submittedWaitingGrading': 'Submitted. Waiting for grading.',
  'pickDate': 'Pick date',
  'noReason': 'No reason',
  'before': 'Before',
  'after': 'After',
  'selectClassFirst': 'Please select a class.',
  'noRoomUrl': 'No room URL returned.',
  'allCaughtUp': 'You are all caught up.',
  'accountCreated': 'Account created',
  'noGuardiansLinked': 'No guardians linked.',
  'enroll': 'Enroll',
  'linkGuardian': 'Link guardian',
  'changeEnrollment': 'Change enrollment',
  'start': 'Start',
  'shuffleQuestions': 'Shuffle questions',
  'addQuestion': 'Add question',
  'question': 'Question',
  'multipleChoice': 'Multiple choice',
  'shortAnswer': 'Short answer',
  'answerIsTrue': 'Answer is True',
  'student': 'Student',
  'rate': 'Rate',
  'openResource': 'Open resource',
  'inAppNotifications': 'In-app notifications',
  'emailNotifications': 'Email notifications',
  'smsNotifications': 'SMS notifications',
  'current': 'Current',
  'openPaymentPage': 'Open payment page',
  'everythingSynced': 'Everything is synced.',
  'priorityMedium': 'Medium',
  'priorityHigh': 'High',
  'timetableGenerated': 'Timetable generated.',
  'validationComplete': 'Validation complete.',
  'timetablePublished': 'Timetable published.',
  'timetableArchived': 'Timetable archived.',
  'createVersion': 'Create version',
  'noVersionsYet': 'No versions yet.',
  'priorityLow': 'Low',
  'noDueDate': 'No due date',
  'priority': 'Priority',
  'typeLabel': 'Type',
  'promptLabel': 'Prompt',
  'trueFalse': 'True / False',
  'optionsCommaSeparated': 'Options (comma separated)',
  'correctOptionNumber': 'Correct option number (1-based)',
  'noActiveEnrollment': 'No active enrollment',
  'classLabel': 'Class',
  'searchPeople': 'Search people',
  'noMatchingPeople': 'No matching people. Try a different search.',
  'when': 'When',
  'admShort': 'Adm#',
  'noOptionsAvailable': 'No options available. Create the required records first.',
  'sharePasswordIntro': 'Share this temporary password privately with the account holder:',
  'admissionNumber': 'Admission number',
  'teachers': 'Teachers',
  'parents': 'Parents',
  'teachingAssignments': 'Teaching assignments',
  'fieldName': 'Name',
  'fieldAcademicYear': 'Academic year',
  'fieldSequence': 'Sequence',
  'fieldStartDate': 'Start date',
  'fieldEndDate': 'End date',
  'fieldCode': 'Code',
  'fieldDepartment': 'Department',
  'fieldGradeLevel': 'Grade / level',
  'fieldCapacity': 'Capacity',
  'fieldRoomType': 'Room type',
  'fieldCoreSubject': 'Core subject',
  'fieldStartTime': 'Start time',
  'fieldEndTime': 'End time',
  'fieldBreakPeriod': 'Break period',
  'fieldTerm': 'Term',
  'fieldPeriodsPerWeek': 'Periods per week',
  'fieldFirstName': 'First name',
  'fieldOtherNames': 'Other names',
  'fieldDateOfBirth': 'Date of birth',
  'fieldGender': 'Gender',
  'fieldExistingAccount': 'Existing school account',
  'fieldPhone': 'Phone',
  'fieldStaffNumber': 'Staff number',
  'fieldMaxWeeklyPeriods': 'Maximum weekly periods',
  'fieldParentGuardian': 'Parent / guardian',
  'fieldPrimaryGuardian': 'Primary guardian',
  'fieldMayViewAttendance': 'May view attendance',
  'fieldMayViewGrades': 'May view grades',
  'fieldNewStatus': 'New status',
  'yourAnswer': 'Your answer',
  'instructionsLabel': 'Instructions',
  'maxMarksLabel': 'Max marks',
  'notesLabel': 'Notes',
  'timeLimitMin': 'Time limit (min)',
  'maxAttempts': 'Max attempts',
  'expectedAnswer': 'Expected answer',
  'pointsLabel': 'Points',
  'fieldLastName': 'Last name',
  'fieldMale': 'Male',
  'fieldFemale': 'Female',
  'fieldOther': 'Other',
  'enrollmentLabel': 'Enrollment',
  'myChildren': 'My children',
  'rejectedChanges': 'Rejected changes',
  'fromDate': 'From',
  'toDate': 'To',
  'archiveAction': 'Archive',
  'bookmark': 'Bookmark',
  'breakLabel': 'Break',
  'academicYears': 'Academic years',
  'terms': 'Terms',
  'departments': 'Departments',
  'streams': 'Streams',
  'periods': 'Periods',
  'classrooms': 'Classrooms',
  'levelValue': 'Level %value%',
  'seatsLabel': '%value% seats',
  'manageLabel': 'Manage %name%',
  'unmarkedCount': '%count% unmarked',
  'titleLabel': 'Title',
  'descriptionLabel': 'Description',
  'fieldRequired': '%field% is required.',
};

const _sw = <String, String>{
  'appName': 'Trixx Education',
  'tagline': 'Usimamizi wa shule na kujifunza',
  'signIn': 'Ingia',
  'email': 'Barua pepe',
  'password': 'Nenosiri',
  'signingIn': 'Inaingia…',
  'signOut': 'Toka',
  'profile': 'Wasifu',
  'forgotPassword': 'Umesahau nenosiri?',
  'showPassword': 'Onyesha nenosiri',
  'hidePassword': 'Ficha nenosiri',
  'resetPassword': 'Weka nenosiri jipya',
  'resetPasswordHelp': 'Weka barua pepe yako na tutatuma kiungo cha kuweka nenosiri jipya.',
  'sendResetLink': 'Tuma kiungo',
  'checkYourEmail': 'Angalia barua pepe yako',
  'backToSignIn': 'Rudi kuingia',
  'verificationCode': 'Nambari ya kuthibitisha',
  'verify': 'Thibitisha',
  'mfaTitle': 'Uthibitisho wa hatua mbili',
  'mfaHelp': 'Weka nambari ya tarakimu 6 kutoka kwenye programu yako.',
  'useRecoveryCode': 'Tumia nambari ya akiba',
  'selectSchoolTitle': 'Chagua shule',
  'sessionExpired': 'Kipindi kimeisha',
  'sessionExpiredHelp': 'Ingia tena ili kuendelea.',
  'staySignedIn': 'Endelea kuingia',
  'sessionExpiringSoon': 'Kipindi chako kinaisha hivi karibuni.',
  'demoAccounts': 'Akaunti za majaribio',
  'demoPasswordHint': 'Nenosiri la akaunti zote za majaribio: Demo1234!',
  'overview': 'Muhtasari',
  'timetable': 'Ratiba',
  'attendance': 'Mahudhurio',
  'assignments': 'Kazi za darasa',
  'quizzes': 'Jaribio',
  'tasks': 'Kazi zangu',
  'messages': 'Ujumbe',
  'people': 'Watu',
  'academics': 'Masomo',
  'reports': 'Ripoti',
  'resources': 'Kituo cha kujifunza',
  'syncCenter': 'Usawazishaji',
  'settings': 'Mipangilio',
  'more': 'Zaidi',
  'notifications': 'Arifa',
  'marks': 'Alama',
  'learn': 'Jifunze',
  'family': 'Familia',
  'finances': 'Fedha',
  'commandGallery': 'Maktaba ya vipengele',
  'audit': 'Kumbukumbu',
  'search': 'Tafuta',
  'commandPaletteHint': 'Tafuta watu, madarasa, nyenzo, mipangilio…',
  'noMatches': 'Hakuna matokeo.',
  'retry': 'Jaribu tena',
  'refresh': 'Onyesha upya',
  'save': 'Hifadhi',
  'saveChanges': 'Hifadhi mabadiliko',
  'saved': 'Imehifadhiwa',
  'saving': 'Inahifadhi…',
  'unsavedChanges': 'Mabadiliko hayajahifadhiwa',
  'unsavedChangesHelp': 'Una mabadiliko ambayo hayajahifadhiwa. Toka bila kuhifadhi?',
  'discard': 'Tupa',
  'keepEditing': 'Endelea kuhariri',
  'cancel': 'Ghairi',
  'confirm': 'Thibitisha',
  'close': 'Funga',
  'done': 'Imekamilika',
  'create': 'Unda',
  'edit': 'Hariri',
  'delete': 'Futa',
  'submit': 'Tuma',
  'send': 'Tuma',
  'publish': 'Tangaza',
  'publishResults': 'Tangaza matokeo',
  'resultsPublished': 'Matokeo yametangazwa',
  'sendReminder': 'Tuma ukumbusho',
  'clear': 'Safisha',
  'clearAll': 'Safisha zote',
  'clearSelection': 'Ondoa chaguo',
  'select': 'Chagua',
  'undo': 'Tendua',
  'goBack': 'Rudi nyuma',
  'details': 'Maelezo',
  'review': 'Kagua',
  'moreActions': 'Vitendo zaidi',
  'viewAll': 'Ona zote',
  'next': 'Ifuatayo',
  'previous': 'Iliyotangulia',
  'add': 'Ongeza',
  'remove': 'Ondoa',
  'apply': 'Tumia',
  'reset': 'Weka upya',
  'open': 'Fungua',
  'download': 'Pakua',
  'upload': 'Pakia',
  'export': 'Hamisha',
  'printReport': 'Chapisha ripoti',
  'loading': 'Inapakia…',
  'emptyList': 'Hakuna kitu bado.',
  'somethingWentWrong': 'Kuna hitilafu',
  'networkError': 'Imeshindwa kufikia seva',
  'networkErrorHelp': 'Angalia muunganisho wako na ujaribu tena.',
  'accessDenied': 'Hauna ruhusa ya ukurasa huu',
  'accessDeniedHelp': 'Omba msimamizi wa shule akupatie ruhusa.',
  'notFound': 'Hatukupata hiyo',
  'notFoundHelp': 'Huenda imehamishwa au imeondolewa.',
  'referenceLabel': 'Kumbukumbu',
  'masked': 'Imefichwa kwa faragha',
  'noPermission': 'Hairuhusiwi',
  'staleData': 'Inaonyesha data iliyohifadhiwa',
  'updatedAgo': 'Imesasishwa %value% zilizopita',
  'offlineSaved': 'Huna mtandao. Mabadiliko yamehifadhiwa kwenye kifaa hiki',
  'offlineQueued': 'Imehifadhiwa nje ya mtandao — itasawazishwa.',
  'pendingChanges': 'mabadiliko yanayosubiri',
  'syncingChanges': 'Inasawazisha',
  'syncingLabel': 'Inasawazisha…',
  'syncNow': 'Sawazisha sasa',
  'syncFailed': 'Usawazishaji umeshindikana. Kazi yako iko salama.',
  'needsReview': 'Mabadiliko mengine yanahitaji ukaguzi',
  'upToDate': 'Ime sawa',
  'heroTodaySubtitle': 'Haya ndiyo yanayokuhitaji leo.',
  'connection': 'Muunganisho',
  'server': 'Seva',
  'online': 'Mtandaoni',
  'offline': 'Nje ya mtandao',
  'lastSynced': 'Ilisawazishwa mwisho',
  'lastError': 'Hitilafu ya mwisho',
  'never': 'Kamwe',
  'justNow': 'sasa hivi',
  'minutesAgo': 'dk zilizopita',
  'hoursAgo': 'saa zilizopita',
  'daysAgo': 'siku zilizopita',
  'savedOnDevice': 'Imehifadhiwa kwenye kifaa hiki',
  'savedToServer': 'Imehifadhiwa kwenye seva',
  'conflictTitle': 'Tatua mgongano',
  'conflictIntro': 'Rekodi hii ilibadilika kwenye seva wakati unahariri.',
  'changedFields': 'Sehemu zilizobadilika',
  'localVersion': 'Toleo lako',
  'serverVersion': 'Toleo la seva',
  'useServer': 'Tumia toleo la seva',
  'keepLocal': 'Hifadhi langu',
  'noFieldChanges': 'Hakuna tofauti zilizoonekana.',
  'selectDates': 'Chagua tarehe',
  'language': 'Lugha',
  'english': 'Kiingereza',
  'kiswahili': 'Kiswahili',
  'darkTheme': 'Mandhari nyeusi',
  'lowBandwidthMode': 'Hali ya data ndogo',
  'lowBandwidthHelp': 'Inaahirisha picha na media, na kutumia orodha fupi.',
  'reduceMotion': 'Punguza mwendo',
  'showChart': 'Chati',
  'showTable': 'Jedwali',
  'tableSummary': 'Jedwali mbadala',
  'sortAscending': 'imepangwa juu',
  'sortDescending': 'imepangwa chini',
  'chooseFile': 'Chagua faili',
  'replaceFile': 'Badilisha faili',
  'switchSchool': 'Badilisha shule',
  'noSchool': 'Hakuna shule iliyochaguliwa',
  'selectSchool': 'Chagua shule',
  'notAMember': 'Akaunti yako haijajiunga na shule yoyote. Omba msimamizi wa shule akualike.',
  'today': 'Leo',
  'dueSoon': 'Inakaribia',
  'goodMorning': 'Habari za asubuhi',
  'goodAfternoon': 'Habari za mchana',
  'goodEvening': 'Habari za jioni',
  'whatToDoNext': 'Cha kufanya kinachofuata',
  'nextLessons': 'Masomo yajayo',
  'attendanceNotTaken': 'Mahudhurio hayajachukuliwa',
  'workToMark': 'Kazi za kutia alama',
  'recentFeedback': 'Maoni ya hivi karibuni',
  'upcomingAssessments': 'Mitihani inayokuja',
  'pendingActions': 'Vitendo vinavyosubiri',
  'upcomingEvents': 'Matukio yajayo',
  'onboardingProgress': 'Maendeleo ya usanidi',
  'students': 'Wanafunzi',
  'staff': 'Wafanyakazi',
  'classes': 'Madarasa',
  'subjects': 'Masomo',
  'attendanceToday': 'Mahudhurio leo',
  'enrolledStudents': 'Wanafunzi walioandikishwa',
  'teachingStaff': 'Walimu',
  'classCount': 'Madarasa',
  'subjectCount': 'Masomo',
  'markPresent': 'Yupo',
  'markAbsent': 'Hayupo',
  'markLate': 'Amechelewa',
  'markExcused': 'Ruhusa',
  'openSession': 'Fungua orodha',
  'closeSession': 'Funga orodha',
  'markAllPresent': 'Weka wote wapo',
  'savedLocally': 'Imehifadhiwa kwenye kifaa hiki',
  'attendanceSaved': 'Mahudhurio yamehifadhiwa',
  'registerClosed': 'Orodha imefungwa',
  'reason': 'Sababu',
  'correctionRequest': 'Ombi la marekebisho',
  'originalValue': 'Ya awali',
  'proposedValue': 'Inayopendekezwa',
  'justification': 'Sababu',
  'requestCorrection': 'Omba marekebisho',
  'lessons': 'Somo',
  'classGroup': 'Darasa',
  'stream': 'Mkondo',
  'subject': 'Somo',
  'topic': 'Mada',
  'openRoom': 'Fungua chumba',
  'joinLesson': 'Jiunge na somo',
  'typeMessage': 'Andika ujumbe…',
  'chatLive': 'Moja kwa moja',
  'chatReconnecting': 'Inaunganisha tena…',
  'chatPolling': 'Husasishwa kila sekunde 15',
  'messageDeleted': 'Ujumbe huu ulifutwa.',
  'typingNow': '%name% anaandika…',
  'grade': 'Alama',
  'feedback': 'Maoni',
  'deadline': 'Muda wa mwisho',
  'submissions': 'Mawasilisho',
  'markingWorkspace': 'Eneo la kutia alama',
  'autosaved': 'Imehifadhiwa kiotomatiki',
  'questionTypes': 'Aina za maswali',
  'timeLimit': 'Kikomo cha muda',
  'submitQuiz': 'Tuma jaribio',
  'submissionConfirmation': 'Majibu yako yametumwa.',
  'completion': 'Ukamilishaji',
  'bookmarks': 'Vialamisho',
  'suggestedTasks': 'Kazi zinazopendekezwa',
  'assessmentSetup': 'Usanidi wa mtihani',
  'marksEntry': 'Kuingiza alama',
  'gradebook': 'Kitabu cha alama',
  'moderation': 'Usimamizi',
  'draft': 'Rasimu',
  'inReview': 'Inakaguliwa',
  'published': 'Imetangazwa',
  'reportCard': 'Kadi ya matokeo',
  'total': 'Jumla',
  'weight': 'Uzito',
  'missingMarks': 'Alama zinazokosekana',
  'publishConfirm': 'Baada ya kutangaza, matokeo yanaonekana kwa wazazi. Marekebisho yanaunda toleo jipya lenye kumbukumbu.',
  'notifyGuardians': 'Wajulishe wazazi',
  'timetableManage': 'Usimamizi wa ratiba',
  'generate': 'Zalisha',
  'validate': 'Thibitisha',
  'conflicts': 'Migongano',
  'conflictReason': 'Sababu',
  'publishTimetable': 'Tangaza ratiba',
  'weekGrid': 'Wiki',
  'dayAgenda': 'Siku',
  'period': 'Kipindi',
  'room': 'Chumba',
  'teacher': 'Mwalimu',
  'subscription': 'Usajili',
  'currentPlan': 'Mpango wa sasa',
  'plan': 'Mipango',
  'status': 'Hali',
  'active': 'Inayotumika',
  'trial': 'Muda wa majaribio',
  'pastDue': 'Imekwisha',
  'suspended': 'Imesimamishwa',
  'cancelled': 'Imesitishwa',
  'entitlements': 'Haki',
  'maxStudents': 'Wanafunzi wengi zaidi',
  'maxTeachers': 'Walimu wengi zaidi',
  'features': 'Vipengele',
  'upgrade': 'Pandisha',
  'checkout': 'Malipo',
  'payNow': 'Lipa sasa',
  'paymentHistory': 'Historia ya malipo',
  'reference': 'Kumbukumbu',
  'amount': 'Kiasi',
  'date': 'Tarehe',
  'cancelSubscription': 'Sitisha usajili',
  'resumeSubscription': 'Rejesha usajili',
  'confirmCancel': 'Sitisha usajili?',
  'cancelConfirmText': 'Utaendelea kuwa na ufikiaji hadi mwisho wa kipindi cha malipo.',
  'provider': 'Mtoa huduma',
  'paymentInitiated': 'Malipo yameanzishwa',
  'paymentPending': 'Malipo yanasubiri',
  'paymentConfirmed': 'Malipo yamethibitishwa',
  'paymentFailed': 'Malipo yameshindikana',
  'paymentRefunded': 'Yamerudishwa',
  'notificationSettings': 'Arifa',
  'inApp': 'Ndani ya programu',
  'emailChannel': 'Barua pepe',
  'smsChannel': 'SMS',
  'quietHours': 'Saa za utulivu',
  'markAllRead': 'Weka zote kama zilizosomwa',
  'noNotifications': 'Hakuna arifa bado.',
  'announcement': 'Tangazo',
  'audience': 'Hadhira',
  'channels': 'Njia',
  'deliveryLog': 'Kumbukumbu ya utumaji',
  'notifyPreviewNote': 'Onyesho haliwezi kuwa na alama au salio — linaeleza kuwa kuna kipya.',
  'studentDirectory': 'Wanafunzi',
  'staffDirectory': 'Wafanyakazi',
  'admissions': 'Udahili na wanafunzi',
  'guardians': 'Walezi',
  'enrolment': 'Uandikishaji',
  'documents': 'Nyaraka',
  'bulkImport': 'Uingizaji wa wingi',
  'csvTemplate': 'Kiolezo cha CSV',
  'columnMapping': 'Ulinganishaji wa safu',
  'preview': 'Onyesho',
  'importComplete': 'Uingizaji umekamilika',
  'relation': 'Uhusiano',
  'contactPreferences': 'Mapendeleo ya mawasiliano',
  'myData': 'Data yangu',
  'exportData': 'Hamisha data yangu',
  'correctData': 'Omba marekebisho',
  'requestDeletion': 'Omba kufutwa',
  'retentionPeriod': 'Kipindi cha kuhifadhi',
  'mustReauthenticate': 'Thibitisha ni wewe',
  'stepUpHelp': 'Kitendo hiki kinahitaji uingie tena.',
  'signOutAllDevices': 'Toka kwenye vifaa vyote',
  'trustedDevices': 'Vifaa vinavyoaminika',
  'componentGallery': 'Maktaba ya vipengele',
  'galleryHint': 'Vipengele vyote vilivyoshirikiwa katika mandhari angavu na nyeusi.',
  'uploadHint': 'Inaruhusiwa: %ext%. Hadi %mb% MB.',
  'typeToConfirm': 'Andika %value% kuthibitisha',
  'selectedCount': '%count% zimechaguliwa',
  'classesNeedAttendance': 'Madarasa %count% hayana mahudhurio leo',
  'dueInDays': 'Inahitajika baada ya siku %days%',
  'signedInAs': 'Umeingia kama %name%',
  'notSet': 'Haijawekwa',
  'setCurrent': 'Weka ya sasa',
  'submittedWaitingGrading': 'Imewasilishwa. Inasubiri kupigiwa alama.',
  'pickDate': 'Chagua tarehe',
  'noReason': 'Hakuna sababu',
  'before': 'Kabla',
  'after': 'Baada',
  'selectClassFirst': 'Tafadhali chagua darasa.',
  'noRoomUrl': 'Hakuna kiungo cha chumba kilichorudishwa.',
  'allCaughtUp': 'Hakuna kipya kwa sasa.',
  'accountCreated': 'Akaunti imeundwa',
  'noGuardiansLinked': 'Hakuna walezi waliohungwa.',
  'enroll': 'Andikisha',
  'linkGuardian': 'Hunga mzazi',
  'changeEnrollment': 'Badilisha usajili',
  'start': 'Anza',
  'shuffleQuestions': 'Changanya maswali',
  'addQuestion': 'Ongeza swali',
  'question': 'Swali',
  'multipleChoice': 'Chaguo nyingi',
  'shortAnswer': 'Jibu fupi',
  'answerIsTrue': 'Jibu ni Kweli',
  'student': 'Mwanafunzi',
  'rate': 'Kiwango',
  'openResource': 'Fungua nyenzo',
  'inAppNotifications': 'Arifa za ndani ya programu',
  'emailNotifications': 'Arifa za barua pepe',
  'smsNotifications': 'Arifa za SMS',
  'current': 'Ya sasa',
  'openPaymentPage': 'Fungua ukurasa wa malipo',
  'everythingSynced': 'Kila kitu kimesawazishwa.',
  'priorityMedium': 'Kati',
  'priorityHigh': 'Juu',
  'timetableGenerated': 'Ratiba imetengenezwa.',
  'validationComplete': 'Uthibitishaji umekamilika.',
  'timetablePublished': 'Ratiba imechapishwa.',
  'timetableArchived': 'Ratiba imehifadhiwa.',
  'createVersion': 'Unda toleo',
  'noVersionsYet': 'Hakuna matoleo bado.',
  'priorityLow': 'Chini',
  'noDueDate': 'Hakuna tarehe ya mwisho',
  'priority': 'Kipaumbele',
  'typeLabel': 'Aina',
  'promptLabel': 'Andika swali',
  'trueFalse': 'Kweli / Uongo',
  'optionsCommaSeparated': 'Machaguo (yatenganishwe kwa koma)',
  'correctOptionNumber': 'Namba ya jibu sahihi (kuanzia 1)',
  'noActiveEnrollment': 'Hakuna usajili unaoendelea',
  'classLabel': 'Darasa',
  'searchPeople': 'Tafuta watu',
  'noMatchingPeople': 'Hakuna watu wanaolingana. Jaribu utafutaji mwingine.',
  'when': 'Lini',
  'admShort': 'Namba',
  'noOptionsAvailable': 'Hakuna machaguo. Unda rekodi zinazohitajika kwanza.',
  'sharePasswordIntro': 'Mpe mwenye akaunti nenosiri hili la muda kwa faragha:',
  'admissionNumber': 'Namba ya usajili',
  'teachers': 'Walimu',
  'parents': 'Wazazi',
  'teachingAssignments': 'Kazi za kufundisha',
  'fieldName': 'Jina',
  'fieldAcademicYear': 'Mwaka wa masomo',
  'fieldSequence': 'Mfululizo',
  'fieldStartDate': 'Tarehe ya kuanza',
  'fieldEndDate': 'Tarehe ya kumaliza',
  'fieldCode': 'Msimbo',
  'fieldDepartment': 'Idara',
  'fieldGradeLevel': 'Daraja / ngazi',
  'fieldCapacity': 'Uwezo',
  'fieldRoomType': 'Aina ya chumba',
  'fieldCoreSubject': 'Somo la msingi',
  'fieldStartTime': 'Muda wa kuanza',
  'fieldEndTime': 'Muda wa kumaliza',
  'fieldBreakPeriod': 'Kipindi cha mapumziko',
  'fieldTerm': 'Muhula',
  'fieldPeriodsPerWeek': 'Vipindi kwa wiki',
  'fieldFirstName': 'Jina la kwanza',
  'fieldOtherNames': 'Majina mengine',
  'fieldDateOfBirth': 'Tarehe ya kuzaliwa',
  'fieldGender': 'Jinsia',
  'fieldExistingAccount': 'Akaunti iliyopo ya shule',
  'fieldPhone': 'Simu',
  'fieldStaffNumber': 'Namba ya mfanyakazi',
  'fieldMaxWeeklyPeriods': 'Kiwango cha juu cha vipindi kwa wiki',
  'fieldParentGuardian': 'Mzazi / mlezi',
  'fieldPrimaryGuardian': 'Mlezi mkuu',
  'fieldMayViewAttendance': 'Anaweza kuona mahudhurio',
  'fieldMayViewGrades': 'Anaweza kuona alama',
  'fieldNewStatus': 'Hali mpya',
  'yourAnswer': 'Jibu lako',
  'instructionsLabel': 'Maelekezo',
  'maxMarksLabel': 'Alama za juu',
  'notesLabel': 'Maelezo',
  'timeLimitMin': 'Kikomo cha muda (dakika)',
  'maxAttempts': 'Majaribio ya juu',
  'expectedAnswer': 'Jibu linalotarajiwa',
  'pointsLabel': 'Alama',
  'fieldLastName': 'Jina la mwisho',
  'fieldMale': 'Mwanaume',
  'fieldFemale': 'Mwanamke',
  'fieldOther': 'Nyingine',
  'enrollmentLabel': 'Usajili',
  'myChildren': 'Watoto wangu',
  'rejectedChanges': 'Mabadiliko yaliyokataliwa',
  'fromDate': 'Kuanzia',
  'toDate': 'Hadi',
  'archiveAction': 'Hifadhi',
  'bookmark': 'Alamisha',
  'breakLabel': 'Mapumziko',
  'academicYears': 'Miaka ya masomo',
  'terms': 'Mihula',
  'departments': 'Idara',
  'streams': 'Mikondo',
  'periods': 'Vipindi',
  'classrooms': 'Vyumba vya madarasa',
  'levelValue': 'Ngazi %value%',
  'seatsLabel': 'viti %value%',
  'manageLabel': 'Simamia %name%',
  'unmarkedCount': '%count% hawajawekwa',
  'titleLabel': 'Kichwa',
  'descriptionLabel': 'Maelezo',
  'fieldRequired': '%field% inahitajika.',
};

class LocaleController extends Notifier<Locale> {
  @override
  Locale build() {
    final code = ref.watch(tokenStoreProvider).localeCode;
    return code == 'sw' ? const Locale('sw') : const Locale('en');
  }

  void set(Locale locale) {
    state = locale;
    ref.read(tokenStoreProvider).localeCode = locale.languageCode;
  }
}

final localeProvider = NotifierProvider<LocaleController, Locale>(LocaleController.new);

class LocaleScope extends InheritedWidget {
  const LocaleScope({super.key, required this.strings, required super.child});

  final S strings;

  static S of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<LocaleScope>();
    return scope?.strings ?? S.en;
  }

  @override
  bool updateShouldNotify(LocaleScope oldWidget) => strings != oldWidget.strings;
}
