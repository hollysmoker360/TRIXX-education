import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../l10n/strings.dart';
import '../theme/tokens.dart';
import '../widgets/brand.dart';
import '../widgets/offline.dart';
import 'app_top_bar.dart';
import 'command_palette.dart';
import 'window_class.dart';

/// A primary destination in the shell navigation. Already filtered by the user's
/// permissions by the caller (specification section 5.3).
class NavItem {
  const NavItem({
    required this.label,
    required this.icon,
    required this.page,
    this.id,
    this.selectedIcon,
    this.group,
    this.keywords = const [],
  });

  final String label;
  final IconData icon;
  final IconData? selectedIcon;
  final Widget page;

  /// Stable identifier used to jump straight to this destination from elsewhere
  /// in the app (see `core/navigation/destinations.dart`).
  final String? id;

  /// Optional role-area grouping shown in the drawer and extended rail.
  final String? group;
  final List<String> keywords;
}

/// One widget chooses bottom bar, rail, or extended sidebar from the window
/// width, and keeps the current page (and its state) alive across size changes
/// (specification sections 5.1 and 7.4).
class AdaptiveShell extends StatefulWidget {
  const AdaptiveShell({
    super.key,
    required this.items,
    required this.selectedIndex,
    required this.onSelected,
    this.title,
    this.onNotifications,
    this.notificationCount = 0,
    this.onSearch,
    this.extraCommands = const [],
    this.schoolName,
    this.userName,
  });

  final List<NavItem> items;
  final int selectedIndex;
  final ValueChanged<int> onSelected;
  final String? title;
  final VoidCallback? onNotifications;
  final int notificationCount;
  final ValueChanged<String>? onSearch;
  final List<CommandItem> extraCommands;
  final String? schoolName;
  final String? userName;

  @override
  State<AdaptiveShell> createState() => _AdaptiveShellState();
}

class _AdaptiveShellState extends State<AdaptiveShell> {
  final _scaffoldKey = GlobalKey<ScaffoldState>();

  /// Pages are created lazily on first visit so we do not fetch every screen's
  /// data on sign-in, then kept so their state survives navigation and resizing.
  late List<Widget?> _pages;

  @override
  void initState() {
    super.initState();
    _pages = List<Widget?>.filled(widget.items.length, null);
    _ensure(widget.selectedIndex);
  }

  @override
  void didUpdateWidget(covariant AdaptiveShell oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.items.length != oldWidget.items.length) {
      _pages = List<Widget?>.filled(widget.items.length, null);
    }
    _ensure(widget.selectedIndex);
  }

  void _ensure(int index) {
    if (index < 0 || index >= widget.items.length) return;
    _pages[index] ??= KeyedSubtree(
      key: ValueKey('page-${widget.items[index].label}'),
      child: widget.items[index].page,
    );
  }

  List<CommandItem> _commands(BuildContext context) => [
        for (var i = 0; i < widget.items.length; i++)
          CommandItem(
            label: widget.items[i].label,
            icon: widget.items[i].icon,
            keywords: widget.items[i].keywords,
            onSelect: () => widget.onSelected(i),
          ),
        ...widget.extraCommands,
      ];

  void _openPalette() => showCommandPalette(context, _commands(context));

  @override
  Widget build(BuildContext context) {
    final window = windowClassOfContext(context);
    final index = widget.selectedIndex.clamp(0, widget.items.length - 1);
    _ensure(index);

    final content = Column(
      children: [
        AppTopBar(
          title: widget.title,
          onCommandPalette: _openPalette,
          onNotifications: widget.onNotifications,
          notificationCount: widget.notificationCount,
        ),
        const OfflineBanner(),
        Expanded(
          child: IndexedStack(
            index: index,
            sizing: StackFit.expand,
            children: [
              for (var i = 0; i < _pages.length; i++)
                _pages[i] ?? const SizedBox.shrink(),
            ],
          ),
        ),
      ],
    );

    final body = window.usesRail
        ? Row(
            children: [
              _rail(context, window),
              const VerticalDivider(width: 1),
              Expanded(child: content),
            ],
          )
        : content;

    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.keyK, control: true): _openPalette,
        const SingleActivator(LogicalKeyboardKey.keyK, meta: true): _openPalette,
      },
      child: Focus(
        autofocus: true,
        child: Scaffold(
          key: _scaffoldKey,
          body: body,
          drawer: window.isCompact ? _drawer(context) : null,
          bottomNavigationBar: window.isCompact ? _bottomBar(context, index) : null,
        ),
      ),
    );
  }

  Widget _rail(BuildContext context, WindowClass window) {
    final extended = window.extendedRail;
    if (extended) return _sidebar(context);
    return SingleChildScrollView(
      child: ConstrainedBox(
        constraints: BoxConstraints(minHeight: MediaQuery.sizeOf(context).height),
        child: IntrinsicHeight(
          child: NavigationRail(
            selectedIndex: widget.selectedIndex,
            onDestinationSelected: widget.onSelected,
            labelType: NavigationRailLabelType.all,
            leading: const Padding(
              padding: EdgeInsets.symmetric(vertical: AppSpacing.s16),
              child: TrixxLogo(height: 32, markOnly: true),
            ),
            destinations: [
              for (var i = 0; i < widget.items.length; i++)
                NavigationRailDestination(
                  icon: Icon(widget.items[i].icon),
                  selectedIcon: widget.items[i].selectedIcon == null
                      ? null
                      : Icon(widget.items[i].selectedIcon),
                  label: Text(widget.items[i].label),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _bottomBar(BuildContext context, int index) {
    final primaryCount = widget.items.length > 4 ? 4 : widget.items.length;
    final moreSelected = index >= primaryCount;
    return NavigationBar(
      selectedIndex: moreSelected ? primaryCount : index,
      onDestinationSelected: (i) {
        if (i == primaryCount && widget.items.length > primaryCount) {
          _scaffoldKey.currentState?.openDrawer();
          return;
        }
        widget.onSelected(i);
      },
      destinations: [
        for (var i = 0; i < primaryCount; i++)
          NavigationDestination(
            icon: Icon(widget.items[i].icon),
            selectedIcon: widget.items[i].selectedIcon == null
                ? null
                : Icon(widget.items[i].selectedIcon),
            label: widget.items[i].label,
          ),
        if (widget.items.length > primaryCount)
          NavigationDestination(icon: const Icon(Icons.more_horiz), label: S.of(context).more),
      ],
    );
  }

  Widget _drawer(BuildContext context) {
    return Drawer(
      child: SafeArea(
        child: ListView(
          children: [
            Padding(
              padding: const EdgeInsets.all(AppSpacing.s16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (widget.userName != null)
                    Text(widget.userName!, style: context.texts.titleSmall),
                  if (widget.schoolName != null)
                    Text(widget.schoolName!, style: context.texts.bodySmall),
                  const SizedBox(height: AppSpacing.s8),
                  const TrixxLogo(height: 28),
                ],
              ),
            ),
            const Divider(height: 1),
            ..._groupedTiles(context, onTap: (i) {
              widget.onSelected(i);
              Navigator.of(context).pop();
            }),
          ],
        ),
      ),
    );
  }

  /// The extended sidebar: the rail cannot show section headings, so wide
  /// windows list the destinations under their group labels instead.
  Widget _sidebar(BuildContext context) {
    return Material(
      color: Theme.of(context).navigationRailTheme.backgroundColor,
      child: SizedBox(
        width: AppLayout.sidebarWidth,
        child: ListView(
          padding: const EdgeInsets.only(bottom: AppSpacing.s16),
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(AppSpacing.s16, AppSpacing.s16, AppSpacing.s16, AppSpacing.s8),
              child: Align(alignment: Alignment.centerLeft, child: TrixxLogo(height: 32)),
            ),
            ..._groupedTiles(context, onTap: widget.onSelected, dense: true),
          ],
        ),
      ),
    );
  }

  /// Destinations in order, with a heading wherever the group changes.
  List<Widget> _groupedTiles(
    BuildContext context, {
    required ValueChanged<int> onTap,
    bool dense = false,
  }) {
    final items = widget.items;
    return [
      for (var i = 0; i < items.length; i++) ...[
        if (items[i].group != null && (i == 0 || items[i].group != items[i - 1].group))
          Padding(
            padding: const EdgeInsets.fromLTRB(AppSpacing.s16, AppSpacing.s16, AppSpacing.s16, AppSpacing.s4),
            child: Semantics(
              header: true,
              child: Text(
                items[i].group!.toUpperCase(),
                style: context.texts.labelSmall?.copyWith(
                  color: context.tokens.textSecondary,
                  fontWeight: FontWeight.w600,
                  letterSpacing: 0.8,
                ),
              ),
            ),
          ),
        Padding(
          padding: dense ? const EdgeInsets.symmetric(horizontal: AppSpacing.s8) : EdgeInsets.zero,
          child: ListTile(
            dense: dense,
            visualDensity: dense ? VisualDensity.compact : null,
            leading: Icon(i == widget.selectedIndex ? (items[i].selectedIcon ?? items[i].icon) : items[i].icon),
            title: Text(items[i].label),
            selected: i == widget.selectedIndex,
            onTap: () => onTap(i),
          ),
        ),
      ],
    ];
  }
}
