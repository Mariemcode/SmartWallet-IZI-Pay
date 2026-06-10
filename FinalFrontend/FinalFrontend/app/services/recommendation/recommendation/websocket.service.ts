import { Injectable } from '@angular/core';
import { RxStompService } from '@stomp/ng2-stompjs';
import { rxStompServiceFactory } from '@stomp/ng2-stompjs';
import { InjectableRxStompConfig } from '@stomp/ng2-stompjs';
import { environment } from '../../../../environments/environment';

/**
 * Configuration STOMP WebSocket.
 *
 * NOTE FUSION : la gateway Spring Cloud ne route pas nativement le STOMP/WebSocket,
 * donc on se connecte DIRECTEMENT au client-dashboard-service (port 8090) via
 * environment.wsUrl. Pour la production il faudra ajouter un filtre WebSocket
 * dans la gateway ou utiliser un broker dédié.
 */
const myStompConfig: InjectableRxStompConfig = {
  brokerURL: environment.wsUrl,
  connectHeaders: {},
  heartbeatIncoming: 0,
  heartbeatOutgoing: 20000,
  reconnectDelay: 5000,
};

@Injectable({
  providedIn: 'root',
})
export class WebSocketService {
  private stompService: RxStompService;

  constructor() {
    this.stompService = rxStompServiceFactory(myStompConfig);
  }

  watch(topic: string) {
    return this.stompService.watch(topic);
  }

  activate() {
    this.stompService.activate();
  }

  deactivate() {
    this.stompService.deactivate();
  }
}
