import 'package:dio/dio.dart';

/// RFC 7807 problem details returned by the API.
class ApiProblem implements Exception {
  ApiProblem({
    required this.status,
    required this.code,
    required this.detail,
    this.title,
    this.fieldErrors = const [],
  });

  final int status;
  final String code;
  final String detail;
  final String? title;
  final List<Map<String, String>> fieldErrors;

  bool get isNetwork => status == 0;
  bool get isUnauthorized => status == 401;
  bool get isForbidden => status == 403;
  bool get isConflict => status == 409;
  bool get isNotFound => status == 404;

  /// True when the failure is a connectivity/timeout problem (offline-capable flows
  /// should queue instead of surfacing an error).
  bool get isOffline => status == 0 || status == 503 || status == 504;

  factory ApiProblem.fromResponse(Response<dynamic> response) {
    final data = response.data;
    if (data is Map<String, dynamic>) {
      final errors = <Map<String, String>>[];
      final raw = data['errors'];
      if (raw is List) {
        for (final item in raw) {
          if (item is Map) {
            errors.add({
              'field': '${item['field'] ?? ''}',
              'message': '${item['message'] ?? ''}',
            });
          }
        }
      }
      return ApiProblem(
        status: response.statusCode ?? 0,
        code: '${data['code'] ?? 'error'}',
        detail: '${data['detail'] ?? data['title'] ?? 'Request failed.'}',
        title: data['title'] as String?,
        fieldErrors: errors,
      );
    }
    return ApiProblem(
      status: response.statusCode ?? 0,
      code: 'error',
      detail: 'Request failed (${response.statusCode}).',
    );
  }

  factory ApiProblem.network(String message) =>
      ApiProblem(status: 0, code: 'network_error', detail: message);

  @override
  String toString() => detail;
}
