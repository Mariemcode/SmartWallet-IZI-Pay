/**
 * SmartWallet IZI Pay — Configuration d'environnement (production)
 * =====================================================================
 * En production, les URLs doivent pointer vers le domaine final.
 * Pour la soutenance/démo locale, c'est environment.ts qui est utilisé.
 */
export const environment = {
  production: true,
  gatewayUrl: 'https://api.smartwallet-izipay.tn',
  api: {
    adminClients:      'https://api.smartwallet-izipay.tn/api/admin-service/clients',
    clients:           'https://api.smartwallet-izipay.tn/api/clients',
    dashboard:         'https://api.smartwallet-izipay.tn/api/dashboard',
    transaction:       'https://api.smartwallet-izipay.tn/api/transaction',
    analysis:          'https://api.smartwallet-izipay.tn/api/analysis',
    providers:         'https://api.smartwallet-izipay.tn/api/providers',
    classification:    'https://api.smartwallet-izipay.tn/api/v1/classification',
    recommendations:   'https://api.smartwallet-izipay.tn/api/recommendations',
    offers:            'https://api.smartwallet-izipay.tn/api/offers',
    interaction:       'https://api.smartwallet-izipay.tn/api/interaction',
    pipeline:          'https://api.smartwallet-izipay.tn/api/pipeline',
    metrics:           'https://api.smartwallet-izipay.tn/api/metrics',
    ia:                'https://api.smartwallet-izipay.tn/api/ia',
  },
  wsUrl: 'wss://api.smartwallet-izipay.tn/ws',
  authMock: {
    email:    'admin@admin.com',
    password: 'admin123',
  },
};
