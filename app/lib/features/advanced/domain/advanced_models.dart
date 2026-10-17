library;

class AiSuggestion {
  const AiSuggestion({required this.id, this.text = '', this.source = ''});
  final String id;
  final String text;
  final String source;
  factory AiSuggestion.fromJson(Map<String, dynamic> json) => AiSuggestion(
        id: '${json['id']}',
        text: '${json['text'] ?? json['suggestion'] ?? ''}',
        source: '${json['source'] ?? ''}',
      );
}
