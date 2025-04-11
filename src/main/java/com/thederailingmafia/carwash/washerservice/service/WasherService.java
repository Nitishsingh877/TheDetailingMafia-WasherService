package com.thederailingmafia.carwash.washerservice.service;

import com.thederailingmafia.carwash.washerservice.client.OrderServiceClient;
import com.thederailingmafia.carwash.washerservice.dto.*;
import com.thederailingmafia.carwash.washerservice.model.Invoice;
import com.thederailingmafia.carwash.washerservice.repository.InvoiceRepository;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.stream.Collectors;

@Service
public class WasherService {

    @Autowired
    private OrderServiceClient orderServiceClient;
    @Autowired
    private InvoiceRepository invoiceRepository;

    public List<WashRequestResponse> getWashRequest(String washerEmail) {
        try{
            List<OrderResponse> orders = orderServiceClient.getCurrentOrders();
            return orders.stream()
                    .filter(o -> washerEmail.equals(o.getWasherEmail()))
                    .map(o -> {
                        WashRequestResponse resp = new WashRequestResponse();
                        resp.setOrderId(o.getId());
                        resp.setCustomerEmail(o.getCustomerEmail());
                        resp.setCarId(o.getCarId());
                        resp.setStatus(o.getStatus());
                        resp.setWasherEmail(o.getWasherEmail());
                        return resp;
                    })
                    .collect(Collectors.toList());
        }catch (Exception e){
            throw new RuntimeException("Failed to get wash request" +  e.getMessage());
        }
}
    public OrderResponse acceptWashRequest(Long orderId, String washerEmail) {
        System.out.println("Accepting order " + orderId + " for washer " + washerEmail);
        OrderResponse order;
        try {
            order = orderServiceClient.getOrderById(orderId);
        } catch (FeignException e) {
            System.out.println("Failed to fetch order: " + e.getMessage());
            throw new RuntimeException("Failed to fetch order: " + e.status(), e);
        }
        System.out.println("Fetched order: " + order);
        if (!washerEmail.equals(order.getWasherEmail())) {
            throw new RuntimeException("Order not assigned to this washer");
        }
        // Check original status first
        if (!"ASSIGNED".equalsIgnoreCase(order.getStatus().trim())) {
            System.out.println("Status mismatch: expected ASSIGNED, got " + order.getStatus());
            throw new RuntimeException("Order not in assignable state");
        }
        // Set status after validation
        order.setStatus("ACCEPTED");
        System.out.println("ACCEPTED STATUS" + order.getStatus());
        try {
            OrderResponse updatedOrder = orderServiceClient.updateOrder(orderId, order);
            System.out.println("Updated order: " + updatedOrder);
            return updatedOrder;
        } catch (FeignException e) {
            System.out.println("Failed to update order: " + e.getMessage());
            throw new RuntimeException("Failed to update order: " + e.status(), e);
        }
    }

    public OrderResponse rejectWashRequest(Long orderId,String washerEmail) {
        OrderResponse order = orderServiceClient.getOrderById(orderId);
        if(!washerEmail.equals(order.getWasherEmail())){
            throw new RuntimeException("Washer email does not match");
        }
        if(!"ASSIGNED".equals(order.getStatus())){
            throw new RuntimeException("Washer status does not match");
        }
        order.setStatus("CANCELED");
        return orderServiceClient.updateOrder(orderId,order);
    }

    public InvoiceResponse genrateInvoice(InvoiceRequest request,String washerEmail) {
        OrderResponse order = orderServiceClient.getOrderById(request.getOrderId());
        if(!washerEmail.equals(order.getWasherEmail()) || !"ACCEPTED".equals(order.getStatus())){
            throw new RuntimeException("Washer email does not match");
        }
        Invoice invoice = new Invoice();
        invoice.setOrderId(request.getOrderId());
        invoice.setWasherEmail(washerEmail);
        invoice.setAmount(request.getAmount());
        invoice.setAmount(request.getAmount());

        Invoice savedInvoice = invoiceRepository.save(invoice);

        return  mapToResponse(savedInvoice);
    }

    private InvoiceResponse mapToResponse(Invoice invoice) {
        InvoiceResponse response = new InvoiceResponse();
        response.setId(invoice.getId());
        response.setOrderId(invoice.getOrderId());
        response.setAmount(invoice.getAmount());
        response.setCreatedAt(invoice.getCreatedAt());
        return response;
    }
}