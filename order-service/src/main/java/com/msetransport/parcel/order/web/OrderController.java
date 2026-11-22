package com.msetransport.parcel.order.web;

import com.msetransport.parcel.order.application.CreateOrderCommand;
import com.msetransport.parcel.order.application.OrderService;
import com.msetransport.parcel.order.application.OrderStats;
import com.msetransport.parcel.order.domain.OrderStatus;
import com.msetransport.parcel.order.domain.Order;
import com.msetransport.parcel.order.web.OrderDtos.CancelOrderRequest;
import com.msetransport.parcel.order.web.OrderDtos.CreateOrderRequest;
import com.msetransport.parcel.order.web.OrderDtos.OrderResponse;
import com.msetransport.parcel.order.web.OrderDtos.PageResponse;
import com.msetransport.parcel.support.security.AuthenticatedUser;
import com.msetransport.parcel.support.web.BusinessRuleException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasAnyRole('MERCHANT', 'OPS')")
@Validated
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @Operation(summary = "Create a cash-on-delivery order (send Idempotency-Key to make retries safe)")
    @PostMapping
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        String merchantId = user.isOps() ? request.merchantId() : user.merchantId();
        if (merchantId == null) {
            throw new BusinessRuleException("merchant-required", "merchantId is required when OPS creates an order");
        }
        Order order = orders.create(new CreateOrderCommand(merchantId, request.recipientName(),
                request.recipientPhone(), request.addressLine(), request.city(), request.codAmount(), idempotencyKey));
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(order.getId());
        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    @Operation(summary = "Get one order (merchants only see their own)")
    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return OrderResponse.from(orders.get(id, AuthenticatedUser.from(jwt)));
    }

    @Operation(summary = "List orders, newest first; repeat ?status= to filter (e.g. status=ASSIGNED&status=OUT_FOR_DELIVERY)")
    @GetMapping
    public PageResponse<OrderResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(name = "status", required = false) List<OrderStatus> statuses) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(orders.list(AuthenticatedUser.from(jwt), statuses, pageable));
    }

    @Operation(summary = "KPIs for today (Africa/Casablanca): orders, delivered rate, COD collected, in transit")
    @GetMapping("/stats")
    public OrderStats stats(@AuthenticationPrincipal Jwt jwt) {
        return orders.stats(AuthenticatedUser.from(jwt));
    }

    @Operation(summary = "Cancel an order that has not left the warehouse")
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody CancelOrderRequest request) {
        return OrderResponse.from(orders.cancel(id, request.reason(), AuthenticatedUser.from(jwt)));
    }
}
