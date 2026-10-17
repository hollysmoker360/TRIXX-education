library;

class InventoryItem {
  const InventoryItem({required this.id, this.name = '', this.status = 'IN_STOCK'});
  final String id;
  final String name;
  final String status;
  factory InventoryItem.fromJson(Map<String, dynamic> json) => InventoryItem(
        id: '${json['id']}',
        name: '${json['name'] ?? json['title'] ?? ''}',
        status: '${json['status'] ?? 'IN_STOCK'}',
      );
}
