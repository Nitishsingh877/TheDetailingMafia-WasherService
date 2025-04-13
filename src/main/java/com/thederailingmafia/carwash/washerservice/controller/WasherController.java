package com.thederailingmafia.carwash.washerservice.controller;

import com.thederailingmafia.carwash.washerservice.client.PaymentServiceClient;
import com.thederailingmafia.carwash.washerservice.dto.*;
import com.thederailingmafia.carwash.washerservice.service.WasherService;
import feign.FeignException;
import io.swagger.v3.oas.annotations.Operation;
import org.hibernate.query.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/washer")
public class WasherController {

    @Autowired
    private WasherService washerService;

    @Autowired
    private PaymentServiceClient paymentServiceClient;

    @GetMapping("/health")
    public String health() {
        return "Washer Service is up and running";
    }


    @GetMapping("/request")
    @PreAuthorize("hasAuthority('WASHER')")
    public List<WashRequestResponse> getRequests() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String washerEmail = auth.getName();
        System.out.println("washerEmail: " + washerEmail);
        List<WashRequestResponse> request = washerService.getWashRequest(washerEmail);
        return request;
    }

    @PostMapping("/accept/{orderId}")
    @PreAuthorize("hasAuthority('WASHER')")
    public OrderResponse acceptWashRequest(@PathVariable Long orderId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String washerEmail = auth.getName();
        OrderResponse response  = washerService.acceptWashRequest(orderId, washerEmail);
        return response;
    }

    @PostMapping("/decline/{orderId}")
    @PreAuthorize("hasAuthority('WASHER')")
    public ResponseEntity<OrderResponse> declineWashRequest(@PathVariable Long orderId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String washerEmail = auth.getName();
        OrderResponse response = washerService.rejectWashRequest(orderId, washerEmail);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/invoice")
    @PreAuthorize("hasAuthority('WASHER')")
    public ResponseEntity<?> createInvoice(
            @RequestBody InvoiceRequest request,
            @RequestHeader("Authorization") String authorization
    ) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String washerEmail = auth.getName();
            System.out.println("Creating invoice for washer:" + washerEmail);
            InvoiceResponse response = washerService.generateInvoice(request, washerEmail, authorization);
            return ResponseEntity.ok(response);
        } catch (FeignException.Unauthorized e) {
            return ResponseEntity.status(401).body("Authentication failed: " + e.getMessage());
        } catch (FeignException.Forbidden e) {
            return ResponseEntity.status(403).body("Access denied: " + e.getMessage());
        } catch (FeignException e) {
            return ResponseEntity.status(e.status()).body("Payment service error: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
        }
    }
}

