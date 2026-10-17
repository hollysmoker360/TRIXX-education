import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';

import '../config/app_config.dart';
import 'api_problem.dart';
import 'token_store.dart';

/// Thin JSON client over Dio with JWT auth, automatic refresh (single-flight) and
/// RFC 7807 problem mapping. Every request carries `X-School-Id` when a school is
/// selected and `X-Device-Id` for sync bookkeeping.
class ApiClient {
  ApiClient._(this._dio, this._tokens, this._onSessionExpired, this._schoolId);

  final Dio _dio;
  final TokenStore _tokens;
  final void Function() _onSessionExpired;
  final String? _schoolId;

  Completer<bool>? _refreshing;

  static ApiClient create(TokenStore tokens, {void Function()? onSessionExpired, String? schoolId}) {
    final dio = Dio(BaseOptions(
      baseUrl: AppConfig.apiBaseUrl,
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 30),
      contentType: Headers.jsonContentType,
    ));
    final client = ApiClient._(dio, tokens, onSessionExpired ?? () {}, schoolId);
    client._install();
    return client;
  }

  void _install() {
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final publicAuth = options.path == '/api/v1/auth/login' || options.path == '/api/v1/auth/refresh';
        if (!publicAuth) {
          final token = await _tokens.accessToken;
          if (token != null) options.headers['Authorization'] = 'Bearer $token';
        }
        final schoolId = publicAuth ? null : _schoolId ?? _tokens.selectedSchoolId;
        if (schoolId != null) options.headers['X-School-Id'] = schoolId;
        options.headers['X-Device-Id'] = _tokens.deviceId;
        handler.next(options);
      },
      onError: (error, handler) async {
        final response = error.response;
        final isAuthEndpoint = error.requestOptions.path.startsWith('/api/v1/auth/');
        if (response?.statusCode == 401 && !isAuthEndpoint && error.requestOptions.extra['retried'] != true) {
          final refreshed = await _ensureRefreshed();
          if (refreshed) {
            try {
              final retry = await _retry(error.requestOptions);
              return handler.resolve(retry);
            } on DioException catch (e) {
              return handler.next(e);
            }
          }
          _onSessionExpired();
        }
        handler.next(error);
      },
    ));
  }

  Future<bool> _ensureRefreshed() {
    final existing = _refreshing;
    if (existing != null) return existing.future;
    final completer = Completer<bool>();
    _refreshing = completer;
    () async {
      try {
        final refresh = await _tokens.refreshToken;
        if (refresh == null) {
          completer.complete(false);
          return;
        }
        final response = await _dio.post('/api/v1/auth/refresh',
            data: {'refreshToken': refresh},
            options: Options(extra: {'skipAuth': true}));
        final data = response.data as Map<String, dynamic>;
        final tokens = data['tokens'] as Map<String, dynamic>? ?? data;
        await _tokens.saveTokens(
          access: '${tokens['accessToken']}',
          refresh: '${tokens['refreshToken']}',
        );
        completer.complete(true);
      } catch (_) {
        await _tokens.clearTokens();
        completer.complete(false);
      } finally {
        _refreshing = null;
      }
    }();
    return completer.future;
  }

  Future<Response<dynamic>> _retry(RequestOptions options) async {
    final token = await _tokens.accessToken;
    final headers = Map<String, dynamic>.from(options.headers);
    if (token != null) headers['Authorization'] = 'Bearer $token';
    return _dio.request<dynamic>(
      options.path,
      data: options.data,
      queryParameters: options.queryParameters,
      cancelToken: options.cancelToken,
      options: Options(method: options.method, headers: headers,
        responseType: options.responseType, extra: {...options.extra, 'retried': true}),
    );
  }

  Future<dynamic> get(String path, {Map<String, dynamic>? query}) =>
      _request(() => _dio.get(path, queryParameters: query));

  Future<dynamic> post(String path, {Object? data, Map<String, dynamic>? query}) =>
      _request(() => _dio.post(path, data: data, queryParameters: query));

  Future<dynamic> patch(String path, {Object? data}) =>
      _request(() => _dio.patch(path, data: data));

  Future<dynamic> delete(String path, {Object? data}) =>
      _request(() => _dio.delete(path, data: data));

  /// Downloads raw bytes (reports). Throws [ApiProblem] on failure.
  Future<List<int>> getBytes(String path, {Map<String, dynamic>? query}) async {
    try {
      final response = await _dio.get<List<int>>(
        path,
        queryParameters: query,
        options: Options(responseType: ResponseType.bytes),
      );
      return response.data ?? const [];
    } on DioException catch (e) {
      throw _mapError(e);
    }
  }

  Future<dynamic> _request(Future<Response<dynamic>> Function() send) async {
    try {
      final response = await send();
      return response.data;
    } on DioException catch (e) {
      throw _mapError(e);
    }
  }

  ApiProblem _mapError(DioException e) {
    final response = e.response;
    if (response != null && response.data is! List<int>) {
      return ApiProblem.fromResponse(response);
    }
    if (response != null) {
      final decoded = _tryDecode(response.data);
      return ApiProblem.fromResponse(Response<dynamic>(
        requestOptions: response.requestOptions,
        statusCode: response.statusCode,
        statusMessage: response.statusMessage,
        data: decoded,
        headers: response.headers,
      ));
    }
    switch (e.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
        return ApiProblem.network('The server took too long to respond.');
      case DioExceptionType.connectionError:
        return ApiProblem.network('Cannot reach the server. Check your connection.');
      default:
        return ApiProblem.network(e.message ?? 'Network error.');
    }
  }

  static dynamic _tryDecode(dynamic data) {
    if (data is List<int>) {
      try {
        return jsonDecode(utf8.decode(data, allowMalformed: true));
      } catch (_) {
        return null;
      }
    }
    return data;
  }
}
