import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../networking/api_client.dart';
import '../networking/api_problem.dart';
import '../networking/token_store.dart';

class AppUser {
  AppUser({required this.id, required this.email, required this.displayName, this.phone, this.status});
  final String id;
  final String email;
  final String displayName;
  final String? phone;
  final String? status;

  factory AppUser.fromJson(Map<String, dynamic> json) => AppUser(
    id: json['id'] as String,
    email: json['email'] as String,
    displayName: json['displayName'] as String,
    phone: json['phone'] as String?,
    status: json['status'] as String?,
  );
}

class Membership {
  Membership({required this.membershipId, required this.schoolId, required this.schoolName,
    required this.schoolSlug, required this.status, this.title, this.roles = const []});
  final String membershipId;
  final String schoolId;
  final String schoolName;
  final String schoolSlug;
  final String status;
  final String? title;
  final List<String> roles;

  String get roleLabel => roles.isEmpty ? 'member' : roles.first.replaceAll('_', ' ');

  factory Membership.fromJson(Map<String, dynamic> json) => Membership(
    membershipId: json['membershipId'] as String,
    schoolId: json['schoolId'] as String,
    schoolName: json['schoolName'] as String,
    schoolSlug: json['schoolSlug'] as String,
    status: json['status'] as String,
    title: json['title'] as String?,
    roles: (json['roles'] as List).cast<String>(),
  );
}

class SessionState {
  const SessionState({this.initializing = true, this.user, this.memberships = const [],
    this.selectedSchoolId, this.permissions = const {}, this.error, this.signingIn = false});

  final bool initializing;
  final AppUser? user;
  final List<Membership> memberships;
  final String? selectedSchoolId;
  final Set<String> permissions;
  final String? error;
  final bool signingIn;
  bool get isSignedIn => user != null;

  Membership? get membership {
    for (final membership in memberships) {
      if (membership.schoolId == selectedSchoolId && membership.status == 'active') return membership;
    }
    return null;
  }

  bool can(String permission) => membership != null && permissions.contains(permission);

  factory SessionState.fromMe(Map<String, dynamic> me) => SessionState(
    initializing: false,
    user: AppUser.fromJson(me['user'] as Map<String, dynamic>),
    memberships: (me['memberships'] as List)
        .map((m) => Membership.fromJson(m as Map<String, dynamic>)).toList(),
    selectedSchoolId: me['activeSchoolId'] as String?,
    permissions: (me['permissions'] as List).cast<String>().toSet(),
  );

  SessionState copyWith({bool? initializing, String? error, bool? signingIn, bool clearError = false}) =>
    SessionState(initializing: initializing ?? this.initializing, user: user, memberships: memberships,
      selectedSchoolId: selectedSchoolId, permissions: permissions,
      error: clearError ? null : error ?? this.error, signingIn: signingIn ?? this.signingIn);
}

class SessionController extends Notifier<SessionState> {
  late final ApiClient _api;
  late final TokenStore _tokens;

  @override
  SessionState build() {
    _api = ref.watch(authApiClientProvider);
    _tokens = ref.watch(tokenStoreProvider);
    Future.microtask(_restore);
    return const SessionState();
  }

  Future<void> _restore() async {
    if (await _tokens.accessToken == null) {
      state = const SessionState(initializing: false);
      return;
    }
    try {
      await _loadMe();
    } on ApiProblem catch (e) {
      state = SessionState(initializing: false, error: e.detail);
    }
  }

  Future<void> _loadMe() async {
    final preferred = _tokens.selectedSchoolId;
    _tokens.selectedSchoolId = null;
    final unscoped = SessionState.fromMe(await _api.get('/api/v1/me') as Map<String, dynamic>);
    final active = unscoped.memberships.where((m) => m.status == 'active').toList();
    final selected = active.where((m) => m.schoolId == preferred).firstOrNull ?? active.firstOrNull;
    _tokens.selectedSchoolId = selected?.schoolId;
    final next = selected == null ? unscoped : SessionState.fromMe(
      await _api.get('/api/v1/me') as Map<String, dynamic>);
    _tokens.lastUserId = next.user!.id;
    state = next;
  }

  Future<bool> signIn(String email, String password) async {
    state = state.copyWith(signingIn: true, clearError: true);
    try {
      _tokens.selectedSchoolId = null;
      final result = await _api.post('/api/v1/auth/login',
        data: {'email': email.trim(), 'password': password}) as Map<String, dynamic>;
      final tokens = result['tokens'] as Map<String, dynamic>;
      await _tokens.saveTokens(access: tokens['accessToken'] as String, refresh: tokens['refreshToken'] as String);
      await _loadMe();
      return true;
    } on ApiProblem catch (e) {
      await _tokens.clearTokens();
      state = SessionState(initializing: false, error: e.detail);
      return false;
    }
  }

  Future<void> selectSchool(String schoolId) async {
    if (state.initializing || schoolId == state.selectedSchoolId) return;
    final previous = state;
    state = state.copyWith(initializing: true);
    _tokens.selectedSchoolId = schoolId;
    try {
      state = SessionState.fromMe(await _api.get('/api/v1/me') as Map<String, dynamic>);
    } on ApiProblem catch (e) {
      _tokens.selectedSchoolId = previous.selectedSchoolId;
      state = previous.copyWith(error: e.detail);
    }
  }

  Future<void> refreshMe() => _loadMe();

  Future<void> signOut() async {
    final refresh = await _tokens.refreshToken;
    if (refresh != null) {
      try {
        await _api.post('/api/v1/auth/logout', data: {'refreshToken': refresh});
      } on ApiProblem {
        // Local sign-out must still work without a connection.
      }
    }
    await _tokens.clearTokens();
    _tokens.selectedSchoolId = null;
    state = const SessionState(initializing: false);
  }

  void sessionExpired() {
    _tokens.clearTokens();
    _tokens.selectedSchoolId = null;
    state = const SessionState(initializing: false, error: 'Your session expired. Sign in again.');
  }

  /// Clears a sign-in or refresh error once the user has acknowledged it.
  void clearError() {
    if (state.error == null) return;
    state = state.copyWith(clearError: true);
  }
}

final tokenStoreProvider = Provider<TokenStore>((ref) => throw UnimplementedError());

final authApiClientProvider = Provider<ApiClient>((ref) => ApiClient.create(
  ref.watch(tokenStoreProvider),
  onSessionExpired: () => ref.read(sessionProvider.notifier).sessionExpired(),
));

final apiClientProvider = Provider<ApiClient>((ref) {
  final scope = ref.watch(sessionProvider.select((s) => (s.user?.id, s.selectedSchoolId)));
  return ApiClient.create(ref.watch(tokenStoreProvider), schoolId: scope.$2,
    onSessionExpired: () {
      final current = ref.read(sessionProvider);
      if ((current.user?.id, current.selectedSchoolId) == scope) {
        ref.read(sessionProvider.notifier).sessionExpired();
      }
    });
});

final sessionProvider = NotifierProvider<SessionController, SessionState>(SessionController.new);
final membershipProvider = Provider<Membership?>((ref) => ref.watch(sessionProvider).membership);
