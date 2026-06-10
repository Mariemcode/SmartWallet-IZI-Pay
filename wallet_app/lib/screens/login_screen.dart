// ════════════════════════════════════════════════════════════════
//  SmartWallet — LoginScreen (refonte minimaliste)
//  Hero logo + 2 champs + bouton + lien inscription.
// ════════════════════════════════════════════════════════════════
import 'package:flutter/material.dart';
import '../services/api_service.dart';
import '../services/notification_service.dart';
import '../theme/app_theme.dart';
import '../widgets/sw_widgets.dart';
import 'dashboard_screen.dart';
import 'register_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _phoneController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _loading = false;
  bool _showPwd = false;
  String? _error;

  @override
  void dispose() {
    _phoneController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    final phone = _phoneController.text.trim();
    final password = _passwordController.text.trim();

    if (phone.isEmpty) {
      setState(() => _error = 'Veuillez saisir votre numéro de téléphone');
      return;
    }
    if (password.isEmpty) {
      setState(() => _error = 'Veuillez saisir votre mot de passe');
      return;
    }

    setState(() { _loading = true; _error = null; });

    try {
      final result = await ApiService.login(username: phone, password: password);
      final accessToken = result['access_token'] as String?;
      final clientId = result['client_id'] as String?;
      if (accessToken == null || accessToken.isEmpty) throw Exception('Token non reçu');
      if (clientId == null || clientId.isEmpty) {
        throw Exception('Client IZI Pay introuvable pour ce numéro.\nVérifiez que le compte existe.');
      }

      await ApiService.saveToken(accessToken);
      await ApiService.saveClientId(clientId);

      // Enregistrement FCM lancé en arrière-plan (fire-and-forget).
      // On NE l'attend PAS : la navigation vers le dashboard ne doit jamais
      // dépendre de l'envoi du token de notification. Si le backend FCM est
      // lent ou indisponible, cela ne bloque plus l'utilisateur.
      NotificationService().registerAfterLogin().catchError((e) {
        debugPrint('⚠️ FCM token non envoyé (sans impact): $e');
      });

      if (mounted) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => DashboardScreen(clientId: clientId)),
        );
      }
    } catch (e) {
      String message = e.toString();
      if (message.contains('401') || message.contains('Unauthorized') || message.contains('incorrect')) {
        message = 'Numéro de téléphone ou mot de passe incorrect';
      } else if (message.contains('SocketException') || message.contains('Connection refused')) {
        message = 'Impossible de contacter le serveur.\nVérifiez votre connexion réseau.';
      } else if (message.contains('TimeoutException')) {
        message = 'Délai dépassé — le serveur ne répond pas.';
      }
      setState(() { _error = message; _loading = false; });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // ── Hero logo ─────────────────────────────────
                Container(
                  width: 86, height: 86,
                  decoration: BoxDecoration(
                    gradient: AppTheme.primaryGradient,
                    borderRadius: BorderRadius.circular(24),
                    boxShadow: [
                      BoxShadow(
                          color: AppTheme.primary.withOpacity(0.4),
                          blurRadius: 30,
                          offset: const Offset(0, 8)),
                    ],
                  ),
                  child: const Icon(Icons.account_balance_wallet_rounded,
                      color: Colors.white, size: 40),
                ),
                const SizedBox(height: 22),
                const Text('SmartWallet',
                    style: TextStyle(
                        color: AppTheme.textPrimary,
                        fontSize: 28,
                        fontWeight: FontWeight.w900,
                        letterSpacing: -0.5)),
                const SizedBox(height: 6),
                const Text('Bienvenue, connectez-vous',
                    style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),

                const SizedBox(height: 38),

                // ── Champs ────────────────────────────────────
                _Field(
                  controller: _phoneController,
                  hint: 'Numéro de téléphone',
                  icon: Icons.phone_rounded,
                  keyboard: TextInputType.phone,
                  onSubmit: _login,
                ),
                const SizedBox(height: 14),
                _Field(
                  controller: _passwordController,
                  hint: 'Mot de passe',
                  icon: Icons.lock_rounded,
                  obscure: !_showPwd,
                  suffix: IconButton(
                    onPressed: () => setState(() => _showPwd = !_showPwd),
                    icon: Icon(
                        _showPwd ? Icons.visibility_off_rounded : Icons.visibility_rounded,
                        size: 18, color: AppTheme.textMuted),
                  ),
                  onSubmit: _login,
                ),

                // ── Erreur ─────────────────────────────────────
                if (_error != null) ...[
                  const SizedBox(height: 12),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppTheme.debit.withOpacity(0.08),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: AppTheme.debit.withOpacity(0.3)),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.error_outline_rounded,
                            color: AppTheme.debit, size: 18),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(_error!,
                              style: const TextStyle(
                                  color: AppTheme.debit, fontSize: 12)),
                        ),
                      ],
                    ),
                  ),
                ],

                const SizedBox(height: 24),

                // ── CTA ──────────────────────────────────────
                SwPrimaryButton(
                  label: 'Se connecter',
                  icon: Icons.arrow_forward_rounded,
                  loading: _loading,
                  onPressed: _login,
                ),

                const SizedBox(height: 26),

                // ── Inscription ───────────────────────────────
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Text('Pas encore de compte ? ',
                        style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
                    GestureDetector(
                      onTap: () => Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const RegisterScreen()),
                      ),
                      child: const Text("S'inscrire",
                          style: TextStyle(
                              color: AppTheme.primary,
                              fontSize: 13,
                              fontWeight: FontWeight.w700)),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Field extends StatelessWidget {
  final TextEditingController controller;
  final String hint;
  final IconData icon;
  final TextInputType? keyboard;
  final bool obscure;
  final Widget? suffix;
  final VoidCallback? onSubmit;

  const _Field({
    required this.controller,
    required this.hint,
    required this.icon,
    this.keyboard,
    this.obscure = false,
    this.suffix,
    this.onSubmit,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppTheme.border),
      ),
      child: TextField(
        controller: controller,
        keyboardType: keyboard,
        obscureText: obscure,
        onSubmitted: (_) => onSubmit?.call(),
        style: const TextStyle(color: AppTheme.textPrimary, fontSize: 14),
        decoration: InputDecoration(
          hintText: hint,
          hintStyle: const TextStyle(color: AppTheme.textMuted, fontSize: 14),
          prefixIcon: Icon(icon, color: AppTheme.textMuted, size: 19),
          suffixIcon: suffix,
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(vertical: 16, horizontal: 4),
        ),
      ),
    );
  }
}