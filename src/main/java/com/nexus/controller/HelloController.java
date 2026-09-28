package com.nexus.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class HelloController {

    @GetMapping("/api/hello")
    public String hello(){
        return "Hello World from Nexus backend!";
    }
}