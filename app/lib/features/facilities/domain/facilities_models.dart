library;

class SimpleRecord {
  const SimpleRecord({required this.id, this.name = '', this.status = 'ACTIVE'});
  final String id;
  final String name;
  final String status;
  factory SimpleRecord.fromJson(Map<String, dynamic> json) => SimpleRecord(
        id: '${json['id']}',
        name: '${json['name'] ?? json['title'] ?? ''}',
        status: '${json['status'] ?? 'ACTIVE'}',
      );
}
