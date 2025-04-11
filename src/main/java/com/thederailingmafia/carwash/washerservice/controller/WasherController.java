package com.thederailingmafia.carwash.washerservice.controller;

import com.thederailingmafia.carwash.washerservice.dto.InvoiceRequest;
import com.thederailingmafia.carwash.washerservice.dto.InvoiceResponse;
import com.thederailingmafia.carwash.washerservice.dto.OrderResponse;
import com.thederailingmafia.carwash.washerservice.dto.WashRequestResponse;
import com.thederailingmafia.carwash.washerservice.service.WasherService;
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
    @PreAuthorize("hasAnyAuthority('CUSTOMER','ADMIN')")
    public ResponseEntity<InvoiceResponse> generateInvoice(@RequestBody InvoiceRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String washerEmail = auth.getName();
        InvoiceResponse response = washerService.genrateInvoice(request, washerEmail);
        return ResponseEntity.status(201).body(response);
    }

}
