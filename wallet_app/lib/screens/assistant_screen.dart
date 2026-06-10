// ════════════════════════════════════════════════════════════════
//  SmartWallet — Assistant IA (chat, refonte minimaliste)
// ════════════════════════════════════════════════════════════════
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../services/api_service.dart';
import '../config/app_config.dart';
import '../theme/app_theme.dart';
import '../widgets/sw_widgets.dart';

class AssistantScreen extends StatefulWidget {
  final String clientId;
  const AssistantScreen({super.key, required this.clientId});
  @override
  State<AssistantScreen> createState() => _AssistantScreenState();
}

class _AssistantScreenState extends State<AssistantScreen> {
  final _ctrl = TextEditingController();
  final _scroll = ScrollController();
  final List<_Msg> _messages = [];
  bool _sending = false;

  static const _suggestions = [
    "Quel est mon solde ?",
    "Combien j'ai dépensé ce mois ?",
    "Quand arrive ma prochaine facture ?",
    "Comment économiser ?",
    "Y a-t-il des alertes ?",
  ];

  @override
  void initState() {
    super.initState();
    _messages.add(const _Msg(
      text: "Bonjour ! Je suis votre assistant financier SmartWallet.\n\n"
          "Posez-moi une question sur vos finances.",
      fromUser: false,
    ));
  }

  Future<void> _send(String text) async {
    if (text.trim().isEmpty || _sending) return;
    _ctrl.clear();
    setState(() {
      _messages.add(_Msg(text: text, fromUser: true));
      _sending = true;
    });
    _scrollToBottom();

    try {
      final token = await ApiService.getToken();
      final url = Uri.parse('${AppConfig.baseUrl}/api/ia/assistant/chat');
      final response = await http.post(
        url,
        headers: {
          HttpHeaders.contentTypeHeader: 'application/json',
          if (token != null) HttpHeaders.authorizationHeader: 'Bearer $token',
        },
        body: jsonEncode({'client_id': widget.clientId, 'message': text}),
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        setState(() {
          _messages.add(_Msg(
            text: data['response'] ?? 'Pas de réponse',
            fromUser: false,
            source: data['source'] as String?,
          ));
        });
      } else {
        setState(() {
          _messages.add(const _Msg(
              text: "Erreur de connexion. Réessayez.",
              fromUser: false,
              isError: true));
        });
      }
    } catch (e) {
      setState(() {
        _messages.add(const _Msg(
            text: "Impossible de contacter l'assistant.",
            fromUser: false,
            isError: true));
      });
    } finally {
      setState(() => _sending = false);
      _scrollToBottom();
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(
          _scroll.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: const SwAppBar(
          title: 'Assistant IA', subtitle: 'Vos finances en un message'),
      body: Column(
        children: [
          // ── Messages ──
          Expanded(
            child: ListView.builder(
              controller: _scroll,
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 16),
              itemCount: _messages.length,
              itemBuilder: (_, i) => _MsgBubble(msg: _messages[i]),
            ),
          ),

          // ── Suggestions rapides ──
          if (_messages.length <= 2)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: _suggestions.map((s) => Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ActionChip(
                      label: Text(s),
                      backgroundColor: AppTheme.surface,
                      side: BorderSide(
                          color: AppTheme.primary.withOpacity(0.3)),
                      labelStyle: const TextStyle(
                          color: AppTheme.primary,
                          fontSize: 12,
                          fontWeight: FontWeight.w600),
                      onPressed: () => _send(s),
                    ),
                  )).toList(),
                ),
              ),
            ),

          // ── Composer ──
          Container(
            padding: EdgeInsets.fromLTRB(
                12, 8, 12, MediaQuery.of(context).padding.bottom + 12),
            decoration: const BoxDecoration(
              color: AppTheme.surface,
              border: Border(
                  top: BorderSide(color: AppTheme.border, width: 0.5)),
            ),
            child: SafeArea(
              top: false,
              child: Row(
                children: [
                  Expanded(
                    child: Container(
                      decoration: BoxDecoration(
                        color: AppTheme.background,
                        borderRadius: BorderRadius.circular(22),
                      ),
                      child: TextField(
                        controller: _ctrl,
                        enabled: !_sending,
                        textInputAction: TextInputAction.send,
                        onSubmitted: _send,
                        style: const TextStyle(
                            color: AppTheme.textPrimary, fontSize: 14),
                        decoration: const InputDecoration(
                          hintText: 'Posez votre question...',
                          hintStyle: TextStyle(
                              color: AppTheme.textMuted, fontSize: 13),
                          border: InputBorder.none,
                          contentPadding: EdgeInsets.symmetric(
                              horizontal: 18, vertical: 12),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  GestureDetector(
                    onTap: _sending ? null : () => _send(_ctrl.text),
                    child: Container(
                      width: 44, height: 44,
                      decoration: BoxDecoration(
                          gradient: AppTheme.primaryGradient,
                          borderRadius: BorderRadius.circular(22)),
                      child: _sending
                          ? const Center(
                          child: SizedBox(
                              width: 18, height: 18,
                              child: CircularProgressIndicator(
                                  color: Colors.white, strokeWidth: 2)))
                          : const Icon(Icons.send_rounded,
                          color: Colors.white, size: 20),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Msg {
  final String text;
  final bool fromUser;
  final String? source;
  final bool isError;
  const _Msg({
    required this.text,
    required this.fromUser,
    this.source,
    this.isError = false,
  });
}

class _MsgBubble extends StatelessWidget {
  final _Msg msg;
  const _MsgBubble({required this.msg});

  @override
  Widget build(BuildContext context) {
    final isUser = msg.fromUser;
    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: BoxConstraints(
            maxWidth: MediaQuery.of(context).size.width * 0.78),
        margin: const EdgeInsets.symmetric(vertical: 4),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: isUser
              ? AppTheme.primary
              : msg.isError
              ? AppTheme.debit.withOpacity(0.1)
              : AppTheme.surface,
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(16),
            topRight: const Radius.circular(16),
            bottomLeft: Radius.circular(isUser ? 16 : 4),
            bottomRight: Radius.circular(isUser ? 4 : 16),
          ),
          border: Border.all(
            color: isUser
                ? Colors.transparent
                : msg.isError
                ? AppTheme.debit.withOpacity(0.3)
                : AppTheme.border,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(msg.text,
                style: TextStyle(
                    color: isUser
                        ? AppTheme.background
                        : msg.isError
                        ? AppTheme.debit
                        : AppTheme.textPrimary,
                    fontSize: 13.5,
                    height: 1.4)),
            if (msg.source != null && !isUser && !msg.isError) ...[
              const SizedBox(height: 4),
              Text(
                  msg.source == 'rules'
                      ? '⚡ règles'
                      : msg.source == 'ml'
                      ? '🤖 ML'
                      : msg.source!,
                  style: const TextStyle(
                      color: AppTheme.textMuted,
                      fontSize: 9.5,
                      fontWeight: FontWeight.w600)),
            ],
          ],
        ),
      ),
    );
  }
}