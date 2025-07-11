package com.thederailingmafia.carwash.washerservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thederailingmafia.carwash.washerservice.client.OrderServiceClient;
import com.thederailingmafia.carwash.washerservice.client.PaymentServiceClient;
import com.thederailingmafia.carwash.washerservice.dto.*;
import com.thederailingmafia.carwash.washerservice.model.Invoice;
import com.thederailingmafia.carwash.washerservice.repository.InvoiceRepository;
import feign.FeignException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WasherService {

    @Autowired
    private OrderServiceClient orderServiceClient;

    @Autowired
    private PaymentServiceClient paymentServiceClient;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<WashRequestResponse> getWashRequest(String washerEmail) {
        try {
            List<OrderResponse> orders = orderServiceClient.getCurrentOrders();

            return orders.stream()
                    .filter(o -> washerEmail.equals(o.getWasherEmail()))
                    .filter(o -> OrderStatus.ASSIGNED.equals(OrderStatus.valueOf(o.getStatus()))
                            || OrderStatus.ACCEPTED.equals(OrderStatus.valueOf(o.getStatus())))
                    .map(o -> {
                        WashRequestResponse resp = new WashRequestResponse();
                        resp.setOrderId(o.getId());
                        resp.setCustomerEmail(o.getCustomerEmail());
                        resp.setCarId(o.getCarId());
                        resp.setStatus(o.getStatus());
                        resp.setWasherEmail(o.getWasherEmail());

                        try {
                            PaymentResponse paymentStatus = paymentServiceClient.getPaymentStatus(o.getId());
                            resp.setPaymentStatus(paymentStatus.getStatus());
                            System.out.println("✅ Payment status for order " + o.getId() + ": " + paymentStatus.getStatus());
                        } catch (Exception ex) {
                            resp.setPaymentStatus("UNKNOWN");
                            System.err.println("⚠️ Could not fetch payment status for order " + o.getId() + ": " + ex.getMessage());
                        }

                        return resp;
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("Failed to get wash requests: " + e.getMessage());
        }
    }

    public OrderResponse acceptWashRequest(Long orderId, String washerEmail) {
        System.out.println("Accepting order " + orderId + " for washer " + washerEmail);
        OrderResponse order;

        try {
            order = orderServiceClient.getOrderById(orderId);
        } catch (FeignException e) {
            throw new RuntimeException("Failed to fetch order: " + e.status(), e);
        }

        if (!washerEmail.equals(order.getWasherEmail())) {
            throw new RuntimeException("Order not assigned to this washer");
        }

        if (!"ASSIGNED".equalsIgnoreCase(order.getStatus().trim())) {
            throw new RuntimeException("Order not in assignable state");
        }

        order.setStatus("ACCEPTED");

        try {
            OrderResponse updatedOrder = orderServiceClient.updateOrder(orderId, order);
            publishWasherEvent(orderId, washerEmail, "washer.accepted");
            return updatedOrder;
        } catch (FeignException e) {
            throw new RuntimeException("Failed to update order: " + e.status(), e);
        }
    }

    public OrderResponse rejectWashRequest(Long orderId, String washerEmail) {
        OrderResponse order = orderServiceClient.getOrderById(orderId);

        if (!washerEmail.equals(order.getWasherEmail())) {
            throw new RuntimeException("Washer email does not match");
        }

        if (!"ASSIGNED".equals(order.getStatus())) {
            throw new RuntimeException("Washer status does not match");
        }

        order.setStatus("PENDING");
        publishWasherEvent(orderId, washerEmail, "washer.rejected");
        return orderServiceClient.updateOrder(orderId, order);
    }

    public InvoiceResponse generateInvoice(InvoiceRequest request, String washerEmail, String authorization) {
        OrderResponse order;

        try {
            order = orderServiceClient.getOrderById(request.getOrderId());
        } catch (FeignException e) {
            throw new RuntimeException("Failed to fetch order: " + e.status(), e);
        }

        if (!"ACCEPTED".equals(order.getStatus())) {
            throw new RuntimeException("Order is not in ACCEPTED status");
        }

        if (!washerEmail.equals(order.getWasherEmail())) {
            throw new RuntimeException("Washer email does not match order");
        }

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setOrderId(request.getOrderId());
        paymentRequest.setAmount(request.getAmount());
        paymentRequest.setInvoiceAmount(request.getAmount());

        PaymentResponse paymentResponse;

        try {
            System.out.println("Calling payment service for customer: " + order.getCustomerEmail());
            System.out.println("Sending PaymentRequest: " + paymentRequest);

            paymentResponse = paymentServiceClient.processPayment(
                    paymentRequest,
                    authorization,
                    order.getCustomerEmail()
            );

            System.out.println("Received PaymentResponse: " + paymentResponse);
            publishWasherEvent(order.getId(), order.getWasherEmail(), "washer.invoice_generated");
        } catch (FeignException e) {
            System.err.println("Payment service failed: " + e.getMessage());
            throw new RuntimeException("Failed to process payment: " + e.status(), e);
        }

        return new InvoiceResponse(
                request.getOrderId(),
                paymentResponse.getAmount(),
                LocalDateTime.now(),
                paymentResponse.getPaymentId(),
                paymentResponse.getClientSecret(),
                paymentResponse.getPaymentUrl()
        );
    }

    public OrderResponse markBookingAsCompleted(Long orderId, String washerEmail) {
        try {
            System.out.println("markBookingAsCompleted called with orderId: " + orderId + ", washerEmail: " + washerEmail);

            OrderResponse order = orderServiceClient.getOrderById(orderId);

            if (!washerEmail.equals(order.getWasherEmail())) {
                throw new RuntimeException("Order not assigned to this washer.");
            }

            PaymentResponse paymentStatus = paymentServiceClient.getPaymentStatus(orderId);
            order.setPaymentStatus(paymentStatus.getStatus());

            System.out.println("Payment response is: " + paymentStatus);

            order.setStatus("COMPLETED");
            OrderResponse updatedOrder = orderServiceClient.updateOrder(orderId, order);

            publishWasherEvent(orderId, order.getCustomerEmail(), "order.completed");

            return updatedOrder;
        } catch (FeignException.NotFound e) {
            throw new RuntimeException("Order not found: " + e.getMessage());
        } catch (FeignException e) {
            throw new RuntimeException("Error in payment service: " + e.status(), e);
        } catch (Exception e) {
            throw new RuntimeException("Internal server error: " + e.getMessage());
        }
    }

    private void publishWasherEvent(Long orderId, String washerEmail, String eventType) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event", eventType);
            event.put("orderId", orderId);
            event.put("washerEmail", washerEmail);
            rabbitTemplate.convertAndSend("carwash.events", "notification." + eventType, objectMapper.writeValueAsString(event));
            System.out.println("Published event: " + eventType + " for order " + orderId);
        } catch (Exception e) {
            System.err.println(" Error publishing event " + eventType + ": " + e.getMessage());
        }
    }

    private InvoiceResponse mapToResponse(Invoice invoice) {
        if (invoice == null) {
            throw new RuntimeException("Invoice not found in database");
        }

        InvoiceResponse response = new InvoiceResponse();
        response.setOrderId(invoice.getOrderId());
        response.setAmount(invoice.getAmount());
        response.setCreatedAt(invoice.getCreatedAt());
        return response;
    }
}
