// ═══════════════════════════════════════════════════════════════════
//  SmartWallet — SessionGuard
//  lib/utils/session_guard.dart
//
//  Usage dans n'importe quel écran :
//
//    } catch (e) {
//      if (await SessionGuard.handle(context, e)) return;
//      // ... gérer les autres erreurs normalement
//    }
// ═══════════════════════════════════════════════════════════════════

import 'package:flutter/material.dart';
import '../services/api_service.dart';
import '../screens/login_screen.dart';

class SessionGuard {
  /// Vérifie si l'erreur est une SESSION_EXPIRED.
  /// Si oui → logout + navigation vers LoginScreen + snackbar.
  /// Retourne true si géré (le caller doit faire return).
  /// Retourne false si c'est une autre erreur (gérer normalement).
  static Future<bool> handle(BuildContext context, Object error) async {
    final msg = error.toString();

    if (msg.contains('SESSION_EXPIRED')) {
      await ApiService.logout();

      if (context.mounted) {
        // Snackbar discret avant la navigation
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Row(
              children: [
                Icon(Icons.lock_outline_rounded, color: Colors.white, size: 16),
                SizedBox(width: 8),
                Text(
                  'Session expirée — veuillez vous reconnecter.',
                  style: TextStyle(fontSize: 13),
                ),
              ],
            ),
            backgroundColor: Color(0xFFE67E22),
            duration: Duration(seconds: 3),
            behavior: SnackBarBehavior.floating,
          ),
        );

        // Retour au login, on vide tout le stack de navigation
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(builder: (_) => const LoginScreen()),
          (_) => false,
        );
      }
      return true; // erreur gérée
    }

    return false; // pas une session expirée
  }

  /// Version pour usage depuis le navigatorKey global
  /// (depuis un provider ou service sans BuildContext)
  static Future<void> handleGlobal(
      GlobalKey<NavigatorState> navigatorKey) async {
    await ApiService.logout();
    final context = navigatorKey.currentContext;
    if (context != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Row(
            children: [
              Icon(Icons.lock_outline_rounded, color: Colors.white, size: 16),
              SizedBox(width: 8),
              Text(
                'Session expirée — veuillez vous reconnecter.',
                style: TextStyle(fontSize: 13),
              ),
            ],
          ),
          backgroundColor: Color(0xFFE67E22),
          duration: Duration(seconds: 3),
          behavior: SnackBarBehavior.floating,
        ),
      );
      navigatorKey.currentState?.pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const LoginScreen()),
        (_) => false,
      );
    }
  }
}
