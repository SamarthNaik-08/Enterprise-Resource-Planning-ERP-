package com.erp.controller;

import com.erp.dto.NotificationDtos.*;
import com.erp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public NotificationSummary getNotifications() {
        return notificationService.getNotifications();
    }
}
