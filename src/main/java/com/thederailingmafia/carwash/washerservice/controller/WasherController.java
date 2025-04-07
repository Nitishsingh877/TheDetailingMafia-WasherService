package com.thederailingmafia.carwash.washerservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/washer")
public class WasherController {

    @GetMapping("/health")
    public String health() {
        return "Washer Service is up and running";
    }

}
