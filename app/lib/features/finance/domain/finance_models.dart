library;

class Invoice {
  const Invoice({
    required this.id,
    required this.title,
    this.status = 'PENDING',
    this.amount,
    this.dueAt,
  });

  final String id;
  final String title;
  final String status; // PENDING, PAID, OVERDUE, CANCELLED
  final double? amount;
  final String? dueAt;

  factory Invoice.fromJson(Map<String, dynamic> json) => Invoice(
        id: '${json['id']}',
        title: '${json['title'] ?? json['reference'] ?? ''}',
        status: '${json['status'] ?? 'PENDING'}',
        amount: (json['amount'] as num?)?.toDouble(),
        dueAt: json['dueAt']?.toString(),
      );
}

class Payment {
  const Payment({
    required this.id,
    this.status = 'PENDING',
    this.amount,
    this.reference,
    this.paidAt,
  });

  final String id;
  final String status; // INITIATED, PENDING, CONFIRMED, FAILED, REFUNDED
  final double? amount;
  final String? reference;
  final String? paidAt;

  factory Payment.fromJson(Map<String, dynamic> json) => Payment(
        id: '${json['id']}',
        status: '${json['status'] ?? 'PENDING'}',
        amount: (json['amount'] as num?)?.toDouble(),
        reference: json['reference']?.toString(),
        paidAt: json['paidAt']?.toString(),
      );
}
