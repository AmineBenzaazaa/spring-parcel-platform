// Wire types: mirror the Java DTOs (OrderDtos, DeliveryController, LivePayloads, ...).

export type OrderStatus =
  | 'CREATED'
  | 'ASSIGNED'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERY_FAILED'
  | 'DELIVERED'
  | 'RETURNED'
  | 'CANCELLED';

export type DeliveryStatus =
  | 'PENDING_ASSIGNMENT'
  | 'ASSIGNED'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'RETURNED'
  | 'CANCELLED';

export interface Order {
  id: string;
  trackingCode: string;
  merchantId: string;
  recipientName: string;
  recipientPhone: string;
  addressLine: string;
  city: string;
  codAmount: number;
  status: OrderStatus;
  deliveryAttempts: number;
  cancelReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Delivery {
  id: string;
  orderId: string;
  trackingCode: string;
  city: string;
  zone: string | null;
  codAmount: number;
  courierId: string | null;
  status: DeliveryStatus;
  attempts: number;
  maxAttempts: number;
  lastFailureReason: string | null;
  updatedAt: string;
}

export type Availability = 'OFFLINE' | 'ON_ROUTE' | 'AT_HUB' | 'IDLE';

export interface Courier {
  id: string;
  name: string;
  phone: string;
  zone: string;
  active: boolean;
  activeLoad: number;
  outForDelivery: number;
  availability: Availability;
}

export type NotificationStatus = 'SENT' | 'FAILED' | 'SKIPPED';

export interface NotificationItem {
  id: string;
  orderId: string;
  trackingCode: string;
  channel: 'SMS' | 'LOG';
  message: string;
  status: NotificationStatus;
  error: string | null;
  createdAt: string;
}

export type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN' | 'DISABLED' | 'FORCED_OPEN' | 'METRICS_ONLY';

export interface CircuitInfo {
  name: string;
  state: CircuitState;
  failureRate: number;
  bufferedCalls: number;
}

export interface OrderStats {
  since: string;
  ordersToday: number;
  deliveredToday: number;
  codCollectedToday: number;
  inTransit: number;
  failedOrReturnedToday: number;
  deliveredRate: number | null;
  byStatus: Record<OrderStatus, number>;
}

export interface HealthComponent {
  name: string;
  status: 'UP' | 'DOWN' | 'UNKNOWN' | 'OUT_OF_SERVICE' | string;
  latencyMs: number;
}

export interface SystemHealth {
  status: 'UP' | 'DEGRADED' | 'DOWN';
  checkedAt: string;
  components: HealthComponent[];
}

export interface TimelineEntry {
  eventId: string;
  eventType: 'ORDER_CREATED' | 'ORDER_CANCELLED' | 'DELIVERY_STATUS_CHANGED';
  status: string | null;
  previousStatus: string | null;
  attempt: number;
  reason: string | null;
  courierId: string | null;
  correlationId: string | null;
  occurredAt: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  role: string | null;
}

// ---- live (SSE) payloads -------------------------------------------------------------

export interface OrderLive {
  eventId: string;
  type: 'ORDER_CREATED' | 'ORDER_CANCELLED';
  orderId: string;
  trackingCode: string;
  merchantId: string | null;
  recipientName: string | null;
  city: string | null;
  codAmount: number | null;
  reason: string | null;
  correlationId: string | null;
  occurredAt: string;
}

export interface DeliveryLive {
  eventId: string;
  orderId: string;
  trackingCode: string;
  courierId: string | null;
  previousStatus: DeliveryStatus | null;
  newStatus: DeliveryStatus;
  attempt: number;
  reason: string | null;
  codAmount: number | null;
  sequence: number;
  correlationId: string | null;
  occurredAt: string;
}

export interface Hello {
  instance: string;
  serverTime: string;
  circuit: CircuitInfo;
}
