package com.milesight.beaveriot.integrations.sensora.controller;

import com.milesight.beaveriot.base.response.ResponseBody;
import com.milesight.beaveriot.base.response.ResponseBuilder;
import com.milesight.beaveriot.context.api.DeviceServiceProvider;
import com.milesight.beaveriot.context.api.EntityValueServiceProvider;
import com.milesight.beaveriot.integrations.sensora.entity.DeviceEntity;
import com.milesight.beaveriot.integrations.sensora.entity.SensoraIntegrationEntities;
import com.milesight.beaveriot.integrations.sensora.service.SensoraDeviceService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/" + SensoraDeviceService.INTEGRATION_ID)
public class SensoraIntegrationController {
    @Autowired
    private DeviceServiceProvider deviceServiceProvider;

    @Autowired
    private EntityValueServiceProvider entityValueServiceProvider;

    @GetMapping("/active-count")
    public ResponseBody<CountResponse> getActiveDeviceCount() {
        List<String> statusEntityKeys = new ArrayList<>();
        deviceServiceProvider.findAll(SensoraDeviceService.INTEGRATION_ID).forEach(device -> statusEntityKeys.add(device.getEntities().get(0).getKey()));
        Long count = entityValueServiceProvider
                .findValuesByKeys(statusEntityKeys)
                .values()
                .stream()
                .map(n -> (long) n)
                .filter(status -> status == DeviceEntity.DeviceStatus.ONLINE.ordinal())
                .count();
        CountResponse resp = new CountResponse();
        resp.setCount(count);
        return ResponseBuilder.success(resp);
    }

    @GetMapping("/report")
    public ResponseEntity<SensoraIntegrationEntities.DetectReport> getReport() {
        return ResponseEntity.ok(SensoraDeviceService.getLatestReport());
    }

    @Data
    public  class CountResponse {
        private Long count;
    }
}
