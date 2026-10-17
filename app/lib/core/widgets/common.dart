import 'package:flutter/material.dart';

import '../networking/api_problem.dart';
import '../theme/tokens.dart';
import 'overlays.dart';

/// Barrel for the shared component library (specification section 7.1).
///
/// Screens import this one file rather than reaching into individual component
/// files, which keeps the import surface stable as the library grows.
export 'brand.dart';
export 'charts.dart';
export 'data.dart';
export 'inputs.dart';
export 'motion.dart';
export 'offline.dart';
export 'overlays.dart';
export 'states.dart';
export 'surfaces.dart';

/// Shows a transient message for a failed mutation. Offline-capable flows
/// should queue instead and call this only for real server rejections.
void showApiError(BuildContext context, Object error) {
  final message = error is ApiProblem ? error.detail : '$error';
  final kind = error is ApiProblem && error.isOffline
      ? StatusKind.warning
      : StatusKind.error;
  showAppSnack(context, message, kind: kind);
}
