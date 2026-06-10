// ════════════════════════════════════════════════════════════════
//  SmartWallet — Widgets UI réutilisables
//  Réduit la duplication entre les écrans et impose un look cohérent.
// ════════════════════════════════════════════════════════════════
import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

/// AppBar minimaliste avec back, titre, action optionnelle.
class SwAppBar extends StatelessWidget implements PreferredSizeWidget {
  final String title;
  final String? subtitle;
  final List<Widget>? actions;
  final Widget? leading;
  final bool showBack;

  const SwAppBar({
    super.key,
    required this.title,
    this.subtitle,
    this.actions,
    this.leading,
    this.showBack = true,
  });

  @override
  Size get preferredSize => Size.fromHeight(subtitle == null ? 56 : 72);

  @override
  Widget build(BuildContext context) {
    return AppBar(
      backgroundColor: AppTheme.background,
      elevation: 0,
      scrolledUnderElevation: 0,
      leading: leading ??
          (showBack
              ? IconButton(
                  icon: const Icon(Icons.arrow_back_ios_new_rounded,
                      size: 18, color: AppTheme.textPrimary),
                  onPressed: () => Navigator.maybePop(context),
                )
              : null),
      title: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(title,
              style: const TextStyle(
                  fontSize: 17, fontWeight: FontWeight.w700, color: AppTheme.textPrimary)),
          if (subtitle != null)
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(subtitle!,
                  style: const TextStyle(fontSize: 11, color: AppTheme.textMuted)),
            ),
        ],
      ),
      centerTitle: false,
      actions: actions,
    );
  }
}

/// Carte standard avec padding et bordure cohérente.
class SwCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry? padding;
  final EdgeInsetsGeometry? margin;
  final Color? bg;
  final Color? borderColor;
  final BorderRadius? radius;
  final VoidCallback? onTap;

  const SwCard({
    super.key,
    required this.child,
    this.padding,
    this.margin,
    this.bg,
    this.borderColor,
    this.radius,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final card = Container(
      margin: margin,
      padding: padding ?? const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: bg ?? AppTheme.surface,
        borderRadius: radius ?? BorderRadius.circular(16),
        border: Border.all(color: borderColor ?? AppTheme.border),
      ),
      child: child,
    );
    if (onTap == null) return card;
    return InkWell(
      onTap: onTap,
      borderRadius: radius ?? BorderRadius.circular(16),
      child: card,
    );
  }
}

/// En-tête de section : titre + (optionnel) action à droite.
class SwSectionHeader extends StatelessWidget {
  final String title;
  final String? action;
  final VoidCallback? onAction;
  final IconData? icon;

  const SwSectionHeader({
    super.key,
    required this.title,
    this.action,
    this.onAction,
    this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 8, 4, 12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: [
              if (icon != null) ...[
                Icon(icon, size: 16, color: AppTheme.textMuted),
                const SizedBox(width: 6),
              ],
              Text(title,
                  style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: AppTheme.textPrimary,
                      letterSpacing: 0.3)),
            ],
          ),
          if (action != null)
            InkWell(
              onTap: onAction,
              borderRadius: BorderRadius.circular(8),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
                child: Row(
                  children: [
                    Text(action!,
                        style: const TextStyle(
                            fontSize: 11,
                            color: AppTheme.primary,
                            fontWeight: FontWeight.w600)),
                    const Icon(Icons.chevron_right_rounded,
                        size: 14, color: AppTheme.primary),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// Badge pastille colorée.
class SwBadge extends StatelessWidget {
  final String text;
  final Color color;
  final IconData? icon;

  const SwBadge({super.key, required this.text, required this.color, this.icon});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withOpacity(0.15),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 11, color: color),
            const SizedBox(width: 4),
          ],
          Text(text,
              style: TextStyle(
                  fontSize: 10, color: color, fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

/// État vide cohérent.
class SwEmpty extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? description;
  final Widget? action;

  const SwEmpty({
    super.key,
    required this.icon,
    required this.title,
    this.description,
    this.action,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 30, vertical: 50),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 64, height: 64,
              decoration: BoxDecoration(
                color: AppTheme.primary.withOpacity(0.08),
                shape: BoxShape.circle,
              ),
              child: Icon(icon, size: 30, color: AppTheme.primary.withOpacity(0.7)),
            ),
            const SizedBox(height: 16),
            Text(title,
                style: const TextStyle(
                    color: AppTheme.textPrimary,
                    fontSize: 15,
                    fontWeight: FontWeight.w700)),
            if (description != null) ...[
              const SizedBox(height: 6),
              Text(description!,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                      color: AppTheme.textMuted, fontSize: 13, height: 1.4)),
            ],
            if (action != null) ...[const SizedBox(height: 20), action!],
          ],
        ),
      ),
    );
  }
}

/// Loader minimaliste centré.
class SwLoader extends StatelessWidget {
  final String? label;
  const SwLoader({super.key, this.label});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(
            width: 28, height: 28,
            child: CircularProgressIndicator(color: AppTheme.primary, strokeWidth: 2.5),
          ),
          if (label != null) ...[
            const SizedBox(height: 12),
            Text(label!,
                style: const TextStyle(color: AppTheme.textMuted, fontSize: 12)),
          ],
        ],
      ),
    );
  }
}

/// Bouton primaire pleine largeur, style premium.
class SwPrimaryButton extends StatelessWidget {
  final String label;
  final IconData? icon;
  final VoidCallback? onPressed;
  final bool loading;
  final bool fullWidth;

  const SwPrimaryButton({
    super.key,
    required this.label,
    this.icon,
    this.onPressed,
    this.loading = false,
    this.fullWidth = true,
  });

  @override
  Widget build(BuildContext context) {
    final btn = ElevatedButton(
      onPressed: loading ? null : onPressed,
      style: ElevatedButton.styleFrom(
        backgroundColor: AppTheme.primary,
        foregroundColor: AppTheme.background,
        disabledBackgroundColor: AppTheme.primary.withOpacity(0.4),
        elevation: 0,
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 22),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
      child: loading
          ? const SizedBox(
              width: 18, height: 18,
              child: CircularProgressIndicator(
                  color: AppTheme.background, strokeWidth: 2),
            )
          : Row(
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                if (icon != null) ...[
                  Icon(icon, size: 18),
                  const SizedBox(width: 8),
                ],
                Text(label,
                    style: const TextStyle(
                        fontSize: 14, fontWeight: FontWeight.w700)),
              ],
            ),
    );
    return fullWidth ? SizedBox(width: double.infinity, child: btn) : btn;
  }
}

/// Ligne d'info simple (label gauche, valeur droite).
class SwKeyValue extends StatelessWidget {
  final String label;
  final String value;
  final Color? valueColor;
  final IconData? icon;

  const SwKeyValue({
    super.key,
    required this.label,
    required this.value,
    this.valueColor,
    this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          if (icon != null) ...[
            Icon(icon, size: 16, color: AppTheme.textMuted),
            const SizedBox(width: 8),
          ],
          Expanded(
            child: Text(label,
                style: const TextStyle(
                    color: AppTheme.textMuted, fontSize: 12)),
          ),
          Text(value,
              style: TextStyle(
                  color: valueColor ?? AppTheme.textPrimary,
                  fontSize: 13,
                  fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

/// Format de montant TND uniformisé.
String formatTnd(num? value, {bool withSign = false, int decimals = 2}) {
  if (value == null) return '— TND';
  final v = value.toDouble();
  final sign = withSign ? (v >= 0 ? '+' : '') : '';
  return '$sign${v.toStringAsFixed(decimals)} TND';
}

/// Format compact pour grand nombre.
String formatCompact(num? value) {
  if (value == null) return '—';
  final v = value.abs();
  if (v >= 1e6) return '${(v / 1e6).toStringAsFixed(1)}M';
  if (v >= 1e3) return '${(v / 1e3).toStringAsFixed(1)}K';
  return v.toStringAsFixed(0);
}
