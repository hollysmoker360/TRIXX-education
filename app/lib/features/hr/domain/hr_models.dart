library;

class StaffMember {
  const StaffMember({required this.id, this.name = '', this.status = 'ACTIVE'});
  final String id;
  final String name;
  final String status;
  factory StaffMember.fromJson(Map<String, dynamic> json) => StaffMember(
        id: '${json['id']}',
        name: '${json['firstName'] ?? ''} ${json['lastName'] ?? ''}'.trim(),
        status: '${json['status'] ?? 'ACTIVE'}',
      );
}
