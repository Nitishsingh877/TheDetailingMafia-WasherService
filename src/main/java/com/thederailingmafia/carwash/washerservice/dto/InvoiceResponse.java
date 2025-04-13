package com.thederailingmafia.carwash.washerservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InvoiceResponse {
    private Long id;
    private Long orderId;
    private Double amount;
    private LocalDateTime createdAt;
    private String paymentId;
    private String clientSecret;

}
