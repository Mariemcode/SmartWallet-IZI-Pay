import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Design system SmartWallet Dashboard
/// Palette : fond sombre profond + accents émeraude/citron
class AppTheme {
  // ── Couleurs primaires ──────────────────────────────────────────
  static const Color background    = Color(0xFF0A0E1A); // bleu nuit profond
  static const Color surface       = Color(0xFF111827); // cartes
  static const Color surfaceLight  = Color(0xFF1C2537); // cartes secondaires
  static const Color border        = Color(0xFF1E2D45); // bordures subtiles

  static const Color primary       = Color(0xFF00E5A0); // émeraude vif
  static const Color primaryDark   = Color(0xFF00A872); // émeraude foncé
  static const Color accent        = Color(0xFFFFE566); // citron accent
  static const Color accentOrange  = Color(0xFFFF6B35); // orange chaud

  // ── Couleurs texte ──────────────────────────────────────────────
  static const Color textPrimary   = Color(0xFFF0F4FF); // blanc bleuté
  static const Color textSecondary = Color(0xFF8899BB); // gris bleuté
  static const Color textMuted     = Color(0xFF445577); // très discret

  // ── Couleurs sémantiques ────────────────────────────────────────
  static const Color credit        = Color(0xFF00E5A0); // vert = entrée argent
  static const Color debit         = Color(0xFFFF6B6B); // rouge = sortie argent

  // ── Gradients ──────────────────────────────────────────────────
  static const LinearGradient cardGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF0F2040), Color(0xFF0A1628)],
  );

  static const LinearGradient balanceGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF00E5A0), Color(0xFF00A872)],
  );

  static const LinearGradient primaryGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF00E5A0), Color(0xFF00BCD4)],
  );

  // ── Typography ──────────────────────────────────────────────────
  static TextTheme get textTheme => TextTheme(
    displayLarge: GoogleFonts.spaceMono(
      fontSize: 36, fontWeight: FontWeight.bold, color: textPrimary,
      letterSpacing: -1,
    ),
    displayMedium: GoogleFonts.spaceMono(
      fontSize: 28, fontWeight: FontWeight.bold, color: textPrimary,
    ),
    headlineLarge: GoogleFonts.inter(
      fontSize: 22, fontWeight: FontWeight.w700, color: textPrimary,
    ),
    headlineMedium: GoogleFonts.inter(
      fontSize: 18, fontWeight: FontWeight.w600, color: textPrimary,
    ),
    titleLarge: GoogleFonts.inter(
      fontSize: 16, fontWeight: FontWeight.w600, color: textPrimary,
    ),
    titleMedium: GoogleFonts.inter(
      fontSize: 14, fontWeight: FontWeight.w500, color: textPrimary,
    ),
    bodyLarge: GoogleFonts.inter(
      fontSize: 14, fontWeight: FontWeight.w400, color: textPrimary,
    ),
    bodyMedium: GoogleFonts.inter(
      fontSize: 13, fontWeight: FontWeight.w400, color: textSecondary,
    ),
    bodySmall: GoogleFonts.inter(
      fontSize: 11, fontWeight: FontWeight.w400, color: textMuted,
    ),
    labelLarge: GoogleFonts.inter(
      fontSize: 13, fontWeight: FontWeight.w600, color: textPrimary,
      letterSpacing: 0.5,
    ),
  );

  // ── ThemeData principal ──────────────────────────────────────────
  static ThemeData get dark => ThemeData(
    brightness: Brightness.dark,
    scaffoldBackgroundColor: background,
    colorScheme: const ColorScheme.dark(
      primary: primary,
      secondary: accent,
      surface: surface,
      background: background,
      error: debit,
    ),
    textTheme: textTheme,
    appBarTheme: AppBarTheme(
      backgroundColor: background,
      elevation: 0,
      titleTextStyle: GoogleFonts.inter(
        fontSize: 18, fontWeight: FontWeight.w700, color: textPrimary,
      ),
      iconTheme: const IconThemeData(color: textPrimary),
    ),
    cardTheme: const CardThemeData(
      color: surface,
      elevation: 0,
      margin: EdgeInsets.zero,
    ),
    dividerTheme: const DividerThemeData(
      color: border, thickness: 1, space: 1,
    ),
  );

  // ── Icône par catégorie ──────────────────────────────────────────
  static IconData categoryIcon(String category) {
    switch (category) {
      case 'Factures & Services': return Icons.receipt_long_rounded;
      case 'Recharge Telephonique': return Icons.phone_android_rounded;
      case 'Restaurants & Livraison': return Icons.restaurant_rounded;
      case 'Shopping & Paiements': return Icons.shopping_bag_rounded;
      case 'Voyages & Reservations': return Icons.flight_rounded;
      case 'Education & Institutions': return Icons.school_rounded;
      case 'Transferts Envoyes': return Icons.send_rounded;
      case 'Transferts Recus': return Icons.move_to_inbox_rounded;
      case 'Depot & Retrait': return Icons.account_balance_rounded;
      case 'Argent Recu': return Icons.savings_rounded;
      case 'Frais & Commissions': return Icons.receipt_rounded;
      case 'Annulation & Correction': return Icons.undo_rounded;
      default: return Icons.swap_horiz_rounded;
    }
  }

  // ── Couleur par catégorie ────────────────────────────────────────
  static Color categoryColor(String category) {
    switch (category) {
      case 'Factures & Services': return const Color(0xFF4ECDC4);
      case 'Recharge Telephonique': return const Color(0xFF45B7D1);
      case 'Restaurants & Livraison': return const Color(0xFFFF6B35);
      case 'Shopping & Paiements': return const Color(0xFFFF9F43);
      case 'Voyages & Reservations': return const Color(0xFFA29BFE);
      case 'Education & Institutions': return const Color(0xFF6C5CE7);
      case 'Transferts Envoyes': return const Color(0xFFFF6B6B);
      case 'Transferts Recus': return const Color(0xFF00E5A0);
      case 'Depot & Retrait': return const Color(0xFF74B9FF);
      case 'Argent Recu': return const Color(0xFF55EFC4);
      case 'Frais & Commissions': return const Color(0xFFB2BEC3);
      case 'Annulation & Correction': return const Color(0xFFFFE566);
      default: return const Color(0xFF8899BB);
    }
  }
}
