import 'package:flutter/material.dart';
import '../models/dashboard_models.dart';
import '../main.dart' show navigatorKey;
import '../utils/session_guard.dart';
import 'api_service.dart';

/// État global du dashboard — Provider pattern
/// Gère le chargement de toutes les données de visualisation
class DashboardProvider extends ChangeNotifier {
  String? _clientId;
  String? get clientId => _clientId;

  // ── État des données ────────────────────────────────────────────
  WalletSummary?          walletSummary;
  ClientProfile?          clientProfile;
  List<CategoryBreakdown> categoryBreakdown = [];
  List<TopCategory>       topCategories     = [];
  List<MonthlyEvolution>  monthlyEvolution  = [];
  List<WeeklyEvolution>   weeklyEvolution   = [];
  List<CategoryFlow>      categoryFlow      = [];
  PeriodStats?            periodStats;
  List<String>            categories        = [];

  // ── Période sélectionnée ────────────────────────────────────────
  DashboardPeriod _selectedPeriod = DashboardPeriod.thisMonth;
  DashboardPeriod get selectedPeriod => _selectedPeriod;

  // ── Onglet actif (graphes) ──────────────────────────────────────
  int _activeChartTab = 0; // 0 = hebdo, 1 = mensuel
  int get activeChartTab => _activeChartTab;

  // ── États de chargement ─────────────────────────────────────────
  bool _loading         = false;
  bool _loadingHistory  = false;
  bool get loading      => _loading;
  bool get loadingHistory => _loadingHistory;

  String? _error;
  String? get error => _error;

  // ── Historique ──────────────────────────────────────────────────
  List<TransactionItem> transactions  = [];
  int   totalPages   = 0;
  int   currentPage  = 0;
  int   totalElements = 0;
  String? _selectedCategory;
  String? _selectedFlowType;
  String? get selectedCategory  => _selectedCategory;
  String? get selectedFlowType  => _selectedFlowType;

  // ════════════════════════════════════════════════════════════════
  //  INITIALISATION
  // ════════════════════════════════════════════════════════════════

  Future<void> init(String clientId) async {
    _clientId = clientId;
    await loadDashboard();
  }

  // ════════════════════════════════════════════════════════════════
  //  CHARGEMENT GLOBAL DU DASHBOARD
  //  Parallélise toutes les requêtes pour un chargement rapide
  // ════════════════════════════════════════════════════════════════

  Future<void> loadDashboard() async {
    if (_clientId == null) return;
    _loading = true;
    _error   = null;
    notifyListeners();

    try {
      await Future.wait([
        _loadSummary(),
        _loadProfile(),
        _loadCategoryBreakdown(),
        _loadTopCategories(),
        _loadWeeklyEvolution(),
        _loadMonthlyEvolution(),
        _loadStats(),
        _loadCategories(),
        _loadHistory(reset: true),
      ]);
    } catch (e) {
      // SESSION_EXPIRED → redirection login depuis le provider (sans BuildContext)
      if (e.toString().contains('SESSION_EXPIRED')) {
        await SessionGuard.handleGlobal(navigatorKey);
        return;
      }
      _error = e.toString();
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  // ════════════════════════════════════════════════════════════════
  //  CHANGEMENT DE PÉRIODE
  // ════════════════════════════════════════════════════════════════

  Future<void> changePeriod(DashboardPeriod period) async {
    _selectedPeriod = period;
    notifyListeners();

    if (_clientId == null) return;
    await Future.wait([
      _loadCategoryBreakdown(),
      _loadTopCategories(),
      _loadStats(),
      _loadHistory(reset: true),
    ]);
    notifyListeners();
  }

  // ════════════════════════════════════════════════════════════════
  //  ONGLET GRAPHE
  // ════════════════════════════════════════════════════════════════

  void setChartTab(int tab) {
    _activeChartTab = tab;
    notifyListeners();
  }

  // ════════════════════════════════════════════════════════════════
  //  FILTRES HISTORIQUE
  // ════════════════════════════════════════════════════════════════

  Future<void> filterHistory({String? category, String? flowType}) async {
    _selectedCategory = category;
    _selectedFlowType = flowType;
    await _loadHistory(reset: true);
  }

  Future<void> clearHistoryFilters() async {
    _selectedCategory = null;
    _selectedFlowType = null;
    await _loadHistory(reset: true);
  }

  Future<void> loadMoreHistory() async {
    if (currentPage + 1 >= totalPages) return;
    await _loadHistory(page: currentPage + 1, reset: false);
  }

  // ════════════════════════════════════════════════════════════════
  //  CHARGEMENTS INDIVIDUELS (privés)
  // ════════════════════════════════════════════════════════════════

  Future<void> _loadSummary() async {
    walletSummary = await ApiService.getWalletSummary(_clientId!);
  }

  Future<void> _loadProfile() async {
    clientProfile = await ApiService.getClientProfile(_clientId!);
  }

  Future<void> _loadCategoryBreakdown() async {
    categoryBreakdown = await ApiService.getCategoryBreakdown(
      _clientId!, period: _selectedPeriod.value,
    );
  }

  Future<void> _loadTopCategories() async {
    topCategories = await ApiService.getTopCategories(
      _clientId!, period: _selectedPeriod.value,
    );
  }

  Future<void> _loadWeeklyEvolution() async {
    weeklyEvolution = await ApiService.getWeeklyEvolution(_clientId!);
  }

  Future<void> _loadMonthlyEvolution() async {
    monthlyEvolution = await ApiService.getMonthlyEvolution(_clientId!);
  }

  Future<void> _loadStats() async {
    periodStats = await ApiService.getPeriodStats(
      _clientId!, period: _selectedPeriod.value,
    );
  }

  Future<void> _loadCategories() async {
    categories = await ApiService.getCategories(_clientId!);
  }

  Future<void> _loadHistory({int page = 0, bool reset = true}) async {
    _loadingHistory = true;
    if (reset) notifyListeners();

    final result = await ApiService.getHistory(
      _clientId!,
      category: _selectedCategory,
      flowType: _selectedFlowType,
      page: page,
    );

    if (reset) {
      transactions = result.transactions;
    } else {
      transactions = [...transactions, ...result.transactions];
    }
    totalPages    = result.totalPages;
    currentPage   = result.currentPage;
    totalElements = result.totalElements;
    _loadingHistory = false;
    notifyListeners();
  }
}