library;

class Conversation {
  const Conversation({
    required this.id,
    this.title = '',
    this.unread = 0,
  });

  final String id;
  final String title;
  final int unread;

  factory Conversation.fromJson(Map<String, dynamic> json) => Conversation(
        id: '${json['id']}',
        title: '${json['title'] ?? json['name'] ?? ''}',
        unread: (json['unread'] as num?)?.toInt() ?? 0,
      );
}

class NotificationItem {
  const NotificationItem({
    required this.id,
    this.title = '',
    this.body = '',
    this.read = false,
  });

  final String id;
  final String title;
  final String body;
  final bool read;

  factory NotificationItem.fromJson(Map<String, dynamic> json) => NotificationItem(
        id: '${json['id']}',
        title: '${json['title'] ?? ''}',
        body: '${json['body'] ?? json['message'] ?? ''}',
        read: json['read'] == true || json['readAt'] != null,
      );
}
