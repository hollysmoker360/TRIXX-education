import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:schoolsphere_app/core/brand/brand.dart';
import 'package:schoolsphere_app/core/l10n/strings.dart';

/// Guards the string catalogue (specification sections 2.7, 9 and 12).
///
/// The runtime catalogue is hand-written so the build needs no code generation
/// step; these tests keep it honest as screens are added.
void main() {
  test('every English string has a Kiswahili counterpart', () {
    final issues = S.parityIssues;
    expect(
      issues.missingInSw,
      isEmpty,
      reason: 'missing from _sw in core/l10n/strings.dart: ${issues.missingInSw}',
    );
    expect(
      issues.missingInEn,
      isEmpty,
      reason: 'missing from _en in core/l10n/strings.dart: ${issues.missingInEn}',
    );
  });

  test('catalogue keys and getters stay in step', () {
    final source = File('lib/core/l10n/strings.dart').readAsStringSync();
    final catalogueStart = source.indexOf('const _en = <String, String>{');
    expect(catalogueStart, isPositive, reason: 'could not locate the catalogue definition');

    final getters = RegExp(r"_v\('([A-Za-z0-9_]+)'\)")
        .allMatches(source.substring(0, catalogueStart))
        .map((match) => match.group(1)!)
        .toSet();
    final keys = RegExp(r"^\s*'([A-Za-z0-9_]+)':", multiLine: true)
        .allMatches(source.substring(catalogueStart))
        .map((match) => match.group(1)!)
        .toSet();

    expect(
      keys.difference(getters),
      isEmpty,
      reason: 'catalogue entries no getter can reach (dead strings)',
    );
    expect(
      getters.difference(keys),
      isEmpty,
      reason: 'getters reading a key that is not in the catalogue',
    );
  });

  test('the product name is a single untranslated constant', () {
    expect(AppBrand.name, 'Trixx Education');
    expect(S.en.appName, AppBrand.name);
    expect(S.sw.appName, AppBrand.name);
  });

  test('no catalogue string still says SchoolSphere', () {
    final source = File('lib/core/l10n/strings.dart').readAsStringSync();
    expect(
      source.contains('SchoolSphere'),
      isFalse,
      reason: 'the rebrand is incomplete — update core/l10n/strings.dart',
    );
  });

  group('translator ARB files', () {
    final source = File('lib/core/l10n/strings.dart').readAsStringSync();
    final catalogueStart = source.indexOf('const _en = <String, String>{');
    final catalogueKeys = RegExp(r"^\s*'([A-Za-z0-9_]+)':", multiLine: true)
        .allMatches(source.substring(catalogueStart))
        .map((match) => match.group(1)!)
        .toSet();

    Map<String, Object?> arbOf(String file) =>
        jsonDecode(File('lib/l10n/$file').readAsStringSync()) as Map<String, Object?>;

    for (final (file, locale) in const [('app_en.arb', 'en'), ('app_sw.arb', 'sw')]) {
      test('$file matches the runtime catalogue', () {
        final arb = arbOf(file);
        expect(arb['@@locale'], locale);
        final messages = arb.keys.where((key) => !key.startsWith('@')).toSet();
        expect(messages, catalogueKeys);
        expect(
          File('lib/l10n/$file').readAsStringSync().contains('%'),
          isFalse,
          reason: 'ARB files use {placeholder}, not %placeholder%',
        );
      });
    }

    test('every {placeholder} is declared in the ARB metadata', () {
      final arb = arbOf('app_en.arb');
      final placeholders = RegExp(r'\{([A-Za-z0-9_]+)\}');
      var checked = 0;
      for (final entry in Map<String, Object?>.from(arb).entries) {
        if (entry.key.startsWith('@') || entry.value is! String) continue;
        for (final match in placeholders.allMatches(entry.value! as String)) {
          final name = match.group(1)!;
          final meta = arb['@${entry.key}'] as Map<String, Object?>?;
          final declared = (meta?['placeholders'] as Map<String, Object?>?) ?? const {};
          expect(declared.keys, contains(name), reason: '${entry.key} uses {$name} undeclared');
          checked++;
        }
      }
      expect(checked, greaterThan(0), reason: 'the placeholder check inspected nothing');
    });
  });
}
