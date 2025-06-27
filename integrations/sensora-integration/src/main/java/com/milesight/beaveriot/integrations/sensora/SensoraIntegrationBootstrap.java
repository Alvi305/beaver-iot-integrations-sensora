package com.milesight.beaveriot.integrations.sensora;

import com.milesight.beaveriot.context.integration.bootstrap.IntegrationBootstrap;
import com.milesight.beaveriot.context.integration.model.Integration;
import org.springframework.stereotype.Component;

@Component
public class SensoraIntegrationBootstrap implements IntegrationBootstrap {
    @Override
    public void onPrepared(Integration integration) {
        // do nothing
    }

    @Override
    public void onStarted(Integration integrationConfig) {
        System.out.println("Hello, world!");
    }

    @Override
    public void onDestroy(Integration integration) {
        // do nothing
    }
}
