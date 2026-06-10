// ═══════════════════════════════════════════════════════════════════
//  SmartWallet — main.dart
//
//  FLUX SÉCURITÉ MAX :
//    À chaque ouverture de l'app → token effacé → LoginScreen obligatoire.
//    Pendant l'utilisation → refresh automatique transparent.
//    Session expirée (refresh mort) → retour LoginScreen + snackbar.
// ═══════════════════════════════════════════════════════════════════

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'services/api_service.dart';
import 'services/dashboard_provider.dart';
import 'services/notification_service.dart';
import 'screens/login_screen.dart';
import 'screens/prevision_screen.dart';
import 'screens/received_offers_screen.dart';
import 'theme/app_theme.dart';

@pragma('vm:entry-point')
Future<void> _firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp();
  debugPrint('📩 Message arrière-plan: ${message.messageId}');
}

final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Firebase.initializeApp();
  FirebaseMessaging.onBackgroundMessage(_firebaseMessagingBackgroundHandler);
  await initializeDateFormatting('fr', null);

  // ★ SÉCURITÉ MAX : effacer le token à chaque ouverture de l'app
  // → l'utilisateur doit toujours se connecter
  await ApiService.logout();

  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
    ),
  );

  await NotificationService().init(
    onOpenPrevision: (String clientId) {
      navigatorKey.currentState?.push(
        MaterialPageRoute(
          builder: (_) => PrevisionScreen(clientId: clientId),
        ),
      );
    },
    onOpenOffer: (String clientId, int clientRecoId) {
      navigatorKey.currentState?.push(
        MaterialPageRoute(
          builder: (_) => ReceivedOffersScreen(
            clientId: clientId,
            highlightOfferId: clientRecoId > 0 ? clientRecoId : null,
          ),
        ),
      );
    },
  );

  runApp(const SmartWalletApp());
}

class SmartWalletApp extends StatelessWidget {
  const SmartWalletApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => DashboardProvider()),
      ],
      child: MaterialApp(
        title: 'IZI Pay – Smart Wallet',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark,
        navigatorKey: navigatorKey,
        home: const LoginScreen(), // toujours login au démarrage
      ),
    );
  }
}