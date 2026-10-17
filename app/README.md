# Trixx Education — Flutter client

The client for Trixx Education: a multi-school management and learning platform. It talks to
the Spring Boot backend over REST (`/api/v1`) plus a STOMP WebSocket for chat and
notifications, and works online-first with a durable offline mutation queue.

The full run guide (PostgreSQL, backend, and this client) is
[`../docs/RUNNING.md`](../docs/RUNNING.md). This file covers the client itself.

## Layout

```
lib/
├── main.dart                 entry point
├── app/
│   ├── app.dart              MaterialApp.router: themes, locales, provider scope
│   └── router.dart           route table (paths live in core/router/app_routes.dart)
├── core/
│   ├── brand/brand.dart      AppBrand: display name, tagline, logo asset paths
│   ├── theme/                tokens.dart, typography.dart, app_theme.dart  ← visual truth
│   ├── layout/               adaptive_shell, app_top_bar, screen_scaffold, page_header,
│   │                         window_class, command_palette
│   ├── widgets/              the shared component library (states, surfaces, inputs, data,
│   │                         overlays, brand, charts, offline, sync_indicator)
│   ├── l10n/strings.dart     EN + SW catalogue (the runtime source of strings)
│   ├── networking/           Dio client, JWT refresh, RFC 7807 mapping, token store
│   ├── session/              session providers and permission checks
│   ├── settings/             theme, language, low-bandwidth preferences
│   └── sync/                 offline queue, sync status, change-feed cursor
├── l10n/app_en.arb           generated from core/l10n/strings.dart (translator source)
├── features/<area>/presentation/   screens, grouped by role area
└── tool/generate_arb.dart    regenerates the ARB files
```

## Conventions

- **Never hardcode a colour, size, radius, or duration.** Read `context.tokens` /
  `context.texts` (see `core/theme/tokens.dart`). The palette is contrast-checked, including
  school accent colours.
- **Never hardcode user-visible English.** Add the key to both catalogues in
  `core/l10n/strings.dart`, add a typed getter, and re-run the ARB generator.
- **Every page uses `ScreenScaffold`** so the header, breadcrumbs, actions, and gutters match.
  Destinations are embedded; pushed screens (e.g. marks entry) render as pushed pages.
- **Layout responds to width**, not device type: `windowClassOf(width)` gives the Material
  size class and the conventions that follow from it (rail vs bottom bar, column count,
  breadcrumbs).
- **Permission-gate navigation** with `session.can('permission')`; the backend still enforces
  every permission.

## Commands

```powershell
# Dependencies
D:\Trixx\tools\flutter\bin\flutter pub get

# Run (device picker / browser)
D:\Trixx\tools\flutter\bin\flutter run
D:\Trixx\tools\flutter\bin\flutter run -d chrome

# Checks
D:\Trixx\tools\flutter\bin\flutter analyze      # expect: No issues found!
D:\Trixx\tools\flutter\bin\flutter test         # expect: All tests passed!

# Release bundle, then serve it (do not open index.html from disk)
D:\Trixx\tools\flutter\bin\flutter build web --release
python -m http.server 8090 --directory build\web

# Regenerate the translator ARB files after changing strings
D:\Trixx\tools\flutter\bin\dart run tool\generate_arb.dart

# Regenerate Android/web launcher icons after changing the brand assets
D:\Trixx\tools\flutter\bin\dart run flutter_launcher_icons
```

`flutter build web --release` exits with status 1 on this project: the wasm dry-run reports
`flutter_secure_storage_web` using `dart:html`/`dart:js_util`. The build still succeeds
(`✓ Built build\web`). Pass `--no-wasm-dry-run` to silence it.

## Tests

| File | Covers |
|------|--------|
| `test/widget_test.dart` | ids, formatting, the string catalogues, the product name |
| `test/l10n_test.dart` | EN/SW parity, getter↔key coverage, `%placeholder%` declarations, ARB drift, no stale "SchoolSphere" in copy |
| `test/theme_test.dart` | WCAG contrast for text/status/action colours, light vs dark, school-accent correction, Inter typography |
| `test/layout_test.dart` | Material breakpoints and the per-class conventions (rail, breadcrumbs, columns, gutters) |
| `test/components_test.dart` | loading/empty/error states, data tables incl. sensitive masking, buttons and touch targets, logo selection, avatars |

A debug-only **component gallery** (`features/gallery`) renders every shared component in light
and dark and is reachable from the navigation rail — the intended place to eyeball a change and
the natural subject for future golden tests.
