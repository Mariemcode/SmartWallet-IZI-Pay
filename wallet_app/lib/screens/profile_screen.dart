// ════════════════════════════════════════════════════════════════
//  SmartWallet — Profile (refonte minimaliste)
//  Avatar + nom + 3 stats clés + paramètres en cartes
// ════════════════════════════════════════════════════════════════
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/api_service.dart';
import '../services/notification_service.dart';
import '../theme/app_theme.dart';
import '../widgets/sw_widgets.dart';
import 'login_screen.dart';

class ProfileScreen extends StatefulWidget {
  final String clientId;
  const ProfileScreen({super.key, required this.clientId});
  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  Map<String, dynamic>? _profile;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final p = await ApiService.getClientProfileRaw(widget.clientId);
      if (mounted) setState(() => _profile = p);
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _logout(BuildContext context) async {
    // Désabonnement du topic profil + suppression token + reset prefs
    await NotificationService().unsubscribeFromProfileTopic();
    await ApiService.logout();
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
          (_) => false,
    );
  }

  String _initials() {
    final f = _profile?['firstName'] as String? ?? '';
    final l = _profile?['lastName'] as String? ?? '';
    if (f.isNotEmpty && l.isNotEmpty) return '${f[0]}${l[0]}'.toUpperCase();
    if (f.isNotEmpty) return f[0].toUpperCase();
    return '?';
  }

  String _displayName() {
    final f = _profile?['firstName'] as String? ?? '';
    final l = _profile?['lastName'] as String? ?? '';
    final name = '$f $l'.trim();
    return name.isEmpty ? 'Utilisateur' : name;
  }

  String _formatDate(dynamic d) {
    if (d == null) return '—';
    try {
      final dt = DateTime.tryParse(d.toString());
      if (dt == null) return d.toString();
      return '${dt.day}/${dt.month}/${dt.year}';
    } catch (_) {
      return d.toString();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return Scaffold(
        backgroundColor: AppTheme.background,
        body: const Center(
          child: CircularProgressIndicator(color: AppTheme.primary),
        ),
      );
    }

    final solde = (_profile?['solde'] as num?)?.toDouble() ?? 0;
    final nbTx = _profile?['totalTransactions'] ?? 0;
    final topCat = _profile?['topSpendingCategory'] as String? ?? '—';
    final phone = _profile?['phoneNumber'] as String? ?? '';

    return Scaffold(
      backgroundColor: AppTheme.background,
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _load,
          color: AppTheme.primary,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              // ─── Avatar hero ──────────────────────────────
              Center(
                child: Column(
                  children: [
                    Container(
                      width: 92, height: 92,
                      decoration: BoxDecoration(
                        gradient: AppTheme.primaryGradient,
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                              color: AppTheme.primary.withOpacity(0.3),
                              blurRadius: 20,
                              offset: const Offset(0, 4)),
                        ],
                      ),
                      child: Center(
                        child: Text(_initials(),
                            style: const TextStyle(
                                color: Colors.white,
                                fontSize: 32,
                                fontWeight: FontWeight.w800)),
                      ),
                    ),
                    const SizedBox(height: 14),
                    Text(_displayName(),
                        style: const TextStyle(
                            color: AppTheme.textPrimary,
                            fontSize: 20,
                            fontWeight: FontWeight.w800)),
                    if (phone.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(phone,
                          style: const TextStyle(
                              color: AppTheme.textMuted,
                              fontSize: 13,
                              fontFamily: 'monospace')),
                    ],
                  ],
                ),
              ),

              const SizedBox(height: 28),

              // ─── 3 KPI horizontaux ────────────────────────
              Row(
                children: [
                  _StatChip(
                    label: 'Solde',
                    value: '${solde.toStringAsFixed(0)} TND',
                    icon: Icons.account_balance_wallet_rounded,
                    color: solde >= 0 ? AppTheme.credit : AppTheme.debit,
                  ),
                  const SizedBox(width: 10),
                  _StatChip(
                    label: 'Transactions',
                    value: '$nbTx',
                    icon: Icons.receipt_long_rounded,
                    color: AppTheme.primary,
                  ),
                ],
              ),

              const SizedBox(height: 24),

              // ─── Informations détaillées ──────────────────
              const SwSectionHeader(
                  title: 'INFORMATIONS', icon: Icons.info_outline_rounded),
              SwCard(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Column(
                  children: [
                    SwKeyValue(
                        label: 'Catégorie principale',
                        value: topCat,
                        icon: Icons.label_outline_rounded),
                    const Divider(color: AppTheme.border, height: 1),
                    SwKeyValue(
                        label: 'Membre depuis',
                        value: _formatDate(_profile?['memberSince']),
                        icon: Icons.calendar_today_rounded),
                    const Divider(color: AppTheme.border, height: 1),

                  ],
                ),
              ),

              const SizedBox(height: 18),

              // ─── Actions utilitaires ──────────────────────
              const SwSectionHeader(
                  title: 'ACTIONS', icon: Icons.settings_rounded),
              SwCard(
                padding: EdgeInsets.zero,
                child: Column(
                  children: [
                    const Divider(color: AppTheme.border, height: 1),
                    _ActionRow(
                      icon: Icons.refresh_rounded,
                      label: 'Actualiser mon profil',
                      onTap: _load,
                    ),
                    const Divider(color: AppTheme.border, height: 1),
                    _ActionRow(
                      icon: Icons.logout_rounded,
                      label: 'Se déconnecter',
                      color: AppTheme.debit,
                      onTap: () => _logout(context),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 24),
              Center(
                child: const Text('SmartWallet IZI Pay  •  v1.0',
                    style: TextStyle(
                        color: AppTheme.textMuted, fontSize: 11)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StatChip extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color color;
  const _StatChip({
    required this.label,
    required this.value,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: SwCard(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: color, size: 20),
            const SizedBox(height: 8),
            Text(value,
                style: TextStyle(
                    color: color,
                    fontSize: 17,
                    fontWeight: FontWeight.w800)),
            const SizedBox(height: 2),
            Text(label,
                style: const TextStyle(
                    color: AppTheme.textMuted, fontSize: 11)),
          ],
        ),
      ),
    );
  }
}

class _ActionRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final Color? color;
  const _ActionRow({
    required this.icon,
    required this.label,
    required this.onTap,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppTheme.textPrimary;
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Row(
          children: [
            Icon(icon, color: c, size: 18),
            const SizedBox(width: 12),
            Expanded(
              child: Text(label,
                  style: TextStyle(
                      color: c, fontSize: 13.5, fontWeight: FontWeight.w600)),
            ),
            Icon(Icons.chevron_right_rounded, color: AppTheme.textMuted, size: 18),
          ],
        ),
      ),
    );
  }
}