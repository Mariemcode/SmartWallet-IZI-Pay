/// SmartWallet — Configuration centralisée
/// Avant la soutenance → changer UNIQUEMENT gatewayHost
class AppConfig {
  static const String gatewayHost = '192.168.1.68';  // ← CHANGER ICI
  static const int    gatewayPort = 8222;
  static const String baseUrl = 'http://$gatewayHost:$gatewayPort';
  static const Duration defaultTimeout = Duration(seconds: 15);
  static const Duration aiTimeout      = Duration(seconds: 30);
}
