package com.redhat.demo.openshiftjavademo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    @Value("${demo.env}")
    private String demoEnv;

    @GetMapping("/demoenv")
    public String getDemoEnv() {
        return demoEnv;
    }
}
