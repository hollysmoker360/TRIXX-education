library;

class TransportRoute {
  const TransportRoute({required this.id, this.name = '', this.status = 'ACTIVE'});
  final String id;
  final String name;
  final String status;
  factory TransportRoute.fromJson(Map<String, dynamic> json) => TransportRoute(
        id: '${json['id']}',
        name: '${json['name'] ?? json['title'] ?? ''}',
        status: '${json['status'] ?? 'ACTIVE'}',
      );
}
