import 'dart:math';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Persists auth tokens (secure storage) and non-secret device/session preferences.
class TokenStore {
  TokenStore(this._secure, this._prefs);

  final FlutterSecureStorage _secure;
  final SharedPreferences _prefs;

  static const _kAccess = 'auth.accessToken';
  static const _kRefresh = 'auth.refreshToken';
  static const _kDeviceId = 'device.id';
  static const _kSchoolId = 'session.schoolId';
  static const _kUserId = 'session.userId';
  static const _kLocale = 'app.locale';
  static const _kThemeMode = 'app.themeMode';
  static const _kLowBandwidth = 'app.lowBandwidth';
  static const _kNotifyInApp = 'notifications.inApp';
  static const _kNotifyEmail = 'notifications.email';
  static const _kNotifySms = 'notifications.sms';

  static Future<TokenStore> create() async {
    final prefs = await SharedPreferences.getInstance();
    const secure = FlutterSecureStorage(
      aOptions: AndroidOptions(encryptedSharedPreferences: true),
    );
    return TokenStore(secure, prefs);
  }

  Future<String?> get accessToken => _secure.read(key: _kAccess);
  Future<String?> get refreshToken => _secure.read(key: _kRefresh);

  Future<void> saveTokens({required String access, required String refresh}) async {
    await _secure.write(key: _kAccess, value: access);
    await _secure.write(key: _kRefresh, value: refresh);
  }

  Future<void> clearTokens() async {
    await _secure.delete(key: _kAccess);
    await _secure.delete(key: _kRefresh);
  }

  String get deviceId {
    final existing = _prefs.getString(_kDeviceId);
    if (existing != null) return existing;
    final generated = _generateId();
    _prefs.setString(_kDeviceId, generated);
    return generated;
  }

  String? get selectedSchoolId => _prefs.getString(_kSchoolId);
  set selectedSchoolId(String? value) {
    if (value == null) {
      _prefs.remove(_kSchoolId);
    } else {
      _prefs.setString(_kSchoolId, value);
    }
  }

  String? get lastUserId => _prefs.getString(_kUserId);
  set lastUserId(String? value) {
    if (value == null) {
      _prefs.remove(_kUserId);
    } else {
      _prefs.setString(_kUserId, value);
    }
  }

  String? get localeCode => _prefs.getString(_kLocale);
  set localeCode(String? value) {
    if (value == null) {
      _prefs.remove(_kLocale);
    } else {
      _prefs.setString(_kLocale, value);
    }
  }

  /// 'system' | 'light' | 'dark' — the user's theme preference.
  String get themeModeCode => _prefs.getString(_kThemeMode) ?? 'system';
  set themeModeCode(String value) => _prefs.setString(_kThemeMode, value);

  /// Defers images and media, compresses uploads, and shortens lists
  /// (specification section 8.6).
  bool get lowBandwidth => _prefs.getBool(_kLowBandwidth) ?? false;
  set lowBandwidth(bool value) => _prefs.setBool(_kLowBandwidth, value);

  /// Per-table column visibility, remembered per user.
  String? getColumnVisibility(String tableId) => _prefs.getString('table.columns.$tableId');
  Future<void> setColumnVisibility(String tableId, String value) async {
    await _prefs.setString('table.columns.$tableId', value);
  }

  /// Device-local notification preferences (in-app, email and SMS). These are client hints;
  /// the server owns delivery itself.
  bool get notificationsInApp => _prefs.getBool(_kNotifyInApp) ?? true;
  set notificationsInApp(bool value) => _prefs.setBool(_kNotifyInApp, value);

  bool get notificationsEmail => _prefs.getBool(_kNotifyEmail) ?? true;
  set notificationsEmail(bool value) => _prefs.setBool(_kNotifyEmail, value);

  bool get notificationsSms => _prefs.getBool(_kNotifySms) ?? true;
  set notificationsSms(bool value) => _prefs.setBool(_kNotifySms, value);

  String getOrCreateCursorKey(String schoolId) => 'sync.cursor.$schoolId';

  int getSyncCursor(String schoolId) => _prefs.getInt(getOrCreateCursorKey(schoolId)) ?? 0;

  void setSyncCursor(String schoolId, int cursor) =>
      _prefs.setInt(getOrCreateCursorKey(schoolId), cursor);

  String getPendingOpsKey(String schoolId) => 'sync.queue.$schoolId';

  String? getRawPendingOps(String schoolId) => _prefs.getString(getPendingOpsKey(schoolId));

  void setRawPendingOps(String schoolId, String? value) {
    if (value == null) {
      _prefs.remove(getPendingOpsKey(schoolId));
    } else {
      _prefs.setString(getPendingOpsKey(schoolId), value);
    }
  }

  static String _generateId() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-'
        '${hex.substring(16, 20)}-${hex.substring(20, 32)}';
  }
}
