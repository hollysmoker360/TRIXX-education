import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../brand/brand.dart';
import '../theme/tokens.dart';

/// The Trixx Education lockup or mark, adapting to the active theme by swapping
/// the light and dark SVGs (specification section 2.6).
class TrixxLogo extends StatelessWidget {
  const TrixxLogo({
    super.key,
    this.height = 40,
    this.markOnly = false,
    this.forceBrightness,
  });

  final double height;
  final bool markOnly;
  final Brightness? forceBrightness;

  @override
  Widget build(BuildContext context) {
    final brightness = forceBrightness ?? Theme.of(context).brightness;
    final dark = brightness == Brightness.dark;

    // Below the minimum lockup width, fall back to the mark alone (section 2.4).
    final useMark = markOnly || height * 3 < AppBrand.minLockupWidth;
    if (useMark) {
      return SvgPicture.asset(
        AppBrand.mark,
        height: height < AppBrand.minMarkSize ? AppBrand.minMarkSize : height,
        semanticsLabel: AppBrand.shortName,
      );
    }
    return SvgPicture.asset(
      dark ? AppBrand.logoHorizontalDark : AppBrand.logoHorizontal,
      height: height,
      semanticsLabel: AppBrand.name,
    );
  }
}

/// A school's own logo with an initials fallback, used in the header and on
/// documents. Trixx appears as a small "powered by" mark, not a replacement.
class SchoolLogo extends StatelessWidget {
  const SchoolLogo({
    super.key,
    this.logoUrl,
    required this.schoolName,
    this.size = 32,
  });

  final String? logoUrl;
  final String schoolName;
  final double size;

  @override
  Widget build(BuildContext context) {
    if (logoUrl != null && logoUrl!.isNotEmpty) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(AppRadius.sm),
        child: Image.network(
          logoUrl!,
          width: size,
          height: size,
          fit: BoxFit.contain,
          errorBuilder: (context, _, _) => Avatar(name: schoolName, size: size),
          semanticLabel: schoolName,
        ),
      );
    }
    return Avatar(name: schoolName, size: size, square: true);
  }
}

/// Initials avatar with a deterministic colour derived from the name.
class Avatar extends StatelessWidget {
  const Avatar({super.key, required this.name, this.size = 36, this.square = false, this.imageUrl});

  final String name;
  final double size;
  final bool square;
  final String? imageUrl;

  String get _initials {
    final parts = name.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
    if (parts.isEmpty) return '?';
    if (parts.length == 1) return parts.first.substring(0, 1).toUpperCase();
    return (parts.first.substring(0, 1) + parts.last.substring(0, 1)).toUpperCase();
  }

  Color _color(BuildContext context) {
    const palette = [
      AppColors.tealAction,
      AppColors.infoLight,
      Color(0xFF7A5AF8),
      Color(0xFFD6456B),
      AppColors.warningLight,
    ];
    final hash = name.codeUnits.fold<int>(0, (acc, unit) => (acc * 31 + unit) & 0x7fffffff);
    final base = palette[hash % palette.length];
    return Contrast.ensure(base, context.scheme.surface, min: 3.0);
  }

  @override
  Widget build(BuildContext context) {
    if (imageUrl != null && imageUrl!.isNotEmpty) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(square ? AppRadius.sm : size),
        child: Image.network(imageUrl!, width: size, height: size, fit: BoxFit.cover),
      );
    }
    final color = _color(context);
    return Semantics(
      label: name,
      child: Container(
        width: size,
        height: size,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.15),
          borderRadius: BorderRadius.circular(square ? AppRadius.sm : size),
        ),
        child: Text(
          _initials,
          style: context.texts.labelMedium?.copyWith(color: color, fontSize: size * 0.38),
        ),
      ),
    );
  }
}
