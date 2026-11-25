package com.example.xsltservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableResilientMethods(proxyTargetClass = true)
public class XsltserviceApplication {
	static void main(String[] args) {
		SpringApplication.run(XsltserviceApplication.class, args);
	}
}
