/// Brand constants for Trixx Education (specification section 2).
///
/// The product name is kept as a single constant rather than scattered string
/// literals so a future white-label build has one place to change. Text that
/// users read comes from the string catalogue (see `core/l10n/strings.dart`);
/// this file holds the bits that are not translated.
abstract final class AppBrand {
  /// Display name.
  static const name = 'Trixx Education';

  /// Wordmark only, for compact surfaces (collapsed rail, favicon alt text).
  static const shortName = 'Trixx';

  static const tagline = 'School management & learning';

  /// Secondary line shown under the logo on sign-in and footers.
  static const poweredBy = 'Powered by Trixx Education';

  // Bundled SVG assets (regenerate PNG derivatives with
  // tools/brand/render-icons.ps1).
  static const logoHorizontal = 'assets/brand/trixx-logo-horizontal.svg';
  static const logoHorizontalDark = 'assets/brand/trixx-logo-horizontal-dark.svg';
  static const mark = 'assets/brand/trixx-mark.svg';
  static const markMono = 'assets/brand/trixx-mark-mono.svg';
  static const appIcon = 'assets/brand/trixx-app-icon.svg';

  /// Minimum rendered size below which the horizontal lockup must be replaced
  /// by the mark alone (section 2.4).
  static const double minLockupWidth = 120;
  static const double minMarkSize = 24;
}
