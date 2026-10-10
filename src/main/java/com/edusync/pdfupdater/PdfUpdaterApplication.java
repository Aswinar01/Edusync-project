package com.edusync.pdfupdater;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PdfUpdaterApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdfUpdaterApplication.class, args);
    }
}
