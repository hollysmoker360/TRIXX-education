import 'package:flutter/foundation.dart';

/// Runtime configuration. The API base URL can be overridden at build time with
/// `--dart-define=API_BASE_URL=...`; otherwise sensible development defaults are used
/// (localhost for web/desktop, 10.0.2.2 for the Android emulator).
class AppConfig {
  static const String _override = String.fromEnvironment('API_BASE_URL');
  static const String _serverLabel = String.fromEnvironment('SERVER_LABEL');

  static String get apiBaseUrl {
    if (_override.isNotEmpty) return _override;
    if (kIsWeb) return 'http://localhost:8080';
    if (defaultTargetPlatform == TargetPlatform.android) return 'http://10.0.2.2:8080';
    return 'http://localhost:8080';
  }

  /// Whether this client talks to the cloud or the school's own edge server.
  /// Shown in the sync detail sheet (specification section 8.3).
  static String get serverLabel => _serverLabel.isNotEmpty ? _serverLabel : 'Cloud';

  static const String appName = 'Trixx Education';
}
