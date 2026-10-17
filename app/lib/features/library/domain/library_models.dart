library;

class LibraryItem {
  const LibraryItem({required this.id, this.title = '', this.status = 'AVAILABLE'});
  final String id;
  final String title;
  final String status;
  factory LibraryItem.fromJson(Map<String, dynamic> json) => LibraryItem(
        id: '${json['id']}',
        title: '${json['title'] ?? json['name'] ?? ''}',
        status: '${json['status'] ?? 'AVAILABLE'}',
      );
}
