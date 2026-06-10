// ════════════════════════════════════════════════════════════════
//  SmartWallet — RegisterScreen (refonte minimaliste)
// ════════════════════════════════════════════════════════════════
import 'package:flutter/material.dart';
import '../services/api_service.dart';
import '../theme/app_theme.dart';
import '../widgets/sw_widgets.dart';
import 'login_screen.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});
  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final _firstNameController = TextEditingController();
  final _lastNameController  = TextEditingController();
  final _phoneController     = TextEditingController();
  final _passwordController  = TextEditingController();
  final _confirmController   = TextEditingController();
  bool _loading = false;
  bool _showPwd = false;
  String? _error;
  String? _success;

  @override
  void dispose() {
    _firstNameController.dispose();
    _lastNameController.dispose();
    _phoneController.dispose();
    _passwordController.dispose();
    _confirmController.dispose();
    super.dispose();
  }

  String? _validate() {
    if (_firstNameController.text.trim().isEmpty) return 'Le prénom est requis';
    if (_lastNameController.text.trim().isEmpty)  return 'Le nom est requis';
    if (_phoneController.text.trim().isEmpty)     return 'Le numéro de téléphone est requis';
    if (_phoneController.text.trim().length < 8)  return 'Numéro de téléphone trop court';
    if (_passwordController.text.trim().length < 6) return 'Mot de passe min. 6 caractères';
    if (_passwordController.text != _confirmController.text) {
      return 'Les mots de passe ne correspondent pas';
    }
    return null;
  }

  Future<void> _register() async {
    final err = _validate();
    if (err != null) {
      setState(() { _error = err; _success = null; });
      return;
    }
    setState(() { _loading = true; _error = null; _success = null; });
    try {
      await ApiService.register(
        phone:     _phoneController.text.trim(),
        password:  _passwordController.text.trim(),
        firstName: _firstNameController.text.trim(),
        lastName:  _lastNameController.text.trim(),
      );
      setState(() {
        _success = 'Compte créé avec succès ! Connectez-vous maintenant.';
        _loading = false;
      });
      await Future.delayed(const Duration(seconds: 2));
      if (mounted) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const LoginScreen()),
        );
      }
    } catch (e) {
      String msg = e.toString().replaceFirst('Exception: ', '');
      if (msg.contains('409') || msg.contains('déjà utilisé')) {
        msg = 'Ce numéro de téléphone est déjà utilisé';
      } else if (msg.contains('SocketException')) {
        msg = 'Impossible de contacter le serveur.';
      }
      setState(() { _error = msg; _loading = false; });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: const SwAppBar(title: 'Créer un compte'),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
          children: [
            const SizedBox(height: 8),
            const Text("Bienvenue ! Renseigne quelques infos\npour commencer à utiliser SmartWallet.",
                style: TextStyle(
                    color: AppTheme.textSecondary, fontSize: 13, height: 1.4)),
            const SizedBox(height: 26),

            // ── 2 colonnes : prénom / nom ─────────────────
            Row(
              children: [
                Expanded(
                  child: _Field(
                    controller: _firstNameController,
                    hint: 'Prénom',
                    icon: Icons.person_outline_rounded,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _Field(
                    controller: _lastNameController,
                    hint: 'Nom',
                    icon: Icons.badge_outlined,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),

            _Field(
              controller: _phoneController,
              hint: 'Numéro de téléphone',
              icon: Icons.phone_rounded,
              keyboard: TextInputType.phone,
            ),
            const SizedBox(height: 12),
            _Field(
              controller: _passwordController,
              hint: 'Mot de passe',
              icon: Icons.lock_outline_rounded,
              obscure: !_showPwd,
              suffix: IconButton(
                onPressed: () => setState(() => _showPwd = !_showPwd),
                icon: Icon(
                    _showPwd ? Icons.visibility_off_rounded : Icons.visibility_rounded,
                    size: 18, color: AppTheme.textMuted),
              ),
            ),
            const SizedBox(height: 12),
            _Field(
              controller: _confirmController,
              hint: 'Confirmer le mot de passe',
              icon: Icons.lock_rounded,
              obscure: !_showPwd,
              onSubmit: _register,
            ),

            if (_error != null) ...[
              const SizedBox(height: 14),
              _Alert(message: _error!, color: AppTheme.debit, icon: Icons.error_outline_rounded),
            ],
            if (_success != null) ...[
              const SizedBox(height: 14),
              _Alert(message: _success!, color: AppTheme.credit, icon: Icons.check_circle_outline_rounded),
            ],

            const SizedBox(height: 24),
            SwPrimaryButton(
              label: 'Créer mon compte',
              icon: Icons.person_add_rounded,
              loading: _loading,
              onPressed: _register,
            ),

            const SizedBox(height: 16),
            Center(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text('Tu as déjà un compte ? ',
                      style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
                  GestureDetector(
                    onTap: () => Navigator.maybePop(context),
                    child: const Text("Se connecter",
                        style: TextStyle(
                            color: AppTheme.primary,
                            fontSize: 13,
                            fontWeight: FontWeight.w700)),
                  ),
                ],
              ),
            ),
          ],
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

class _Alert extends StatelessWidget {
  final String message;
  final Color color;
  final IconData icon;
  const _Alert({required this.message, required this.color, required this.icon});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withOpacity(0.08),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(message,
                style: TextStyle(color: color, fontSize: 12, height: 1.3)),
          ),
        ],
      ),
    );
  }
}
