import 'package:flutter_test/flutter_test.dart';
import 'package:schoolsphere_app/core/brand/brand.dart';
import 'package:schoolsphere_app/core/format.dart';
import 'package:schoolsphere_app/core/ids.dart';
import 'package:schoolsphere_app/core/l10n/strings.dart';

void main() {
  test('newUuid produces a v4-shaped id', () {
    final id = newUuid();
    expect(RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')
        .hasMatch(id), isTrue);
    expect(newUuid() == id, isFalse);
  });

  test('dayName maps ISO weekdays', () {
    expect(dayName(1), 'Monday');
    expect(dayName(7), 'Sunday');
    expect(dayName(9), 'Sunday');
  });

  test('string catalogues cover both locales', () {
    expect(S.en.appName, AppBrand.name);
    expect(S.sw.appName, AppBrand.name);
    expect(S.en.signIn, isNot(S.sw.signIn));
  });

  test('the product name is Trixx Education and is not translated', () {
    expect(AppBrand.name, 'Trixx Education');
    expect(AppBrand.shortName, 'Trixx');
  });

  test('parameterised strings interpolate their placeholders', () {
    expect(S.en.uploadHint('pdf', 10), contains('pdf'));
    expect(S.en.uploadHint('pdf', 10), contains('10'));
    expect(S.en.selectedCount(3), contains('3'));
    expect(S.en.typeToConfirm('Grade 8'), contains('Grade 8'));
    expect(S.sw.uploadHint('pdf', 10), isNot(contains('%ext%')));
  });
}
