/**
 * SmartWallet IZI Pay — Configuration d'environnement (développement)
 * =====================================================================
 * Centralise toutes les URLs backend pour éviter de les hardcoder
 * dans chaque service Angular.
 *
 * Architecture cible :
 *   Angular (4200) → Gateway (8222) → ClientDashboard (8090) → BDD/IA
 *
 * Pour la production, créer un environment.prod.ts avec les vraies URLs.
 */
export const environment = {
    production: false,

    /** URL racine de la gateway Spring Cloud */
    gatewayUrl: 'http://localhost:8222',

    /** Préfixes API utilisés par les services Angular */
    api: {
        adminClients:      'http://localhost:8222/api/clients',
        clients:           'http://localhost:8222/api/clients',
        dashboard:         'http://localhost:8222/api/dashboard',
        transaction:       'http://localhost:8222/api/transaction',
        analysis:          'http://localhost:8222/api/analysis',
        providers:         'http://localhost:8222/api/providers',
        classification:    'http://localhost:8222/api/v1/classification',

        // Module recommendations
        recommendations:   'http://localhost:8222/api/recommendations',
        offers:            'http://localhost:8222/api/offers',
        interaction:       'http://localhost:8222/api/interaction',
        pipeline:          'http://localhost:8222/api/pipeline',
        metrics:           'http://localhost:8222/api/metrics',

        // ★ NOUVEAU — Dashboard marketing (exposé par MarketingDashboardController)
        marketing:         'http://localhost:8222/api/marketing',

        // Module mobile (prédictions, OCR, chatbot) — via gateway
        ia:                'http://localhost:8222/api/ia',
    },

    /** WebSocket — branché directement sur le client-dashboard */
    wsUrl: 'ws://localhost:8090/ws',

    /** Auth mock — credentials pour la démo */
    authMock: {
        email:    'admin@admin.com',
        password: 'admin123',
    },
};