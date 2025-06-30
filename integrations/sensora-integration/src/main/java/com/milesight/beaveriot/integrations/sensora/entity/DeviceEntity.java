package com.milesight.beaveriot.integrations.sensora.entity;

import com.milesight.beaveriot.context.integration.entity.annotation.*;
import com.milesight.beaveriot.context.integration.enums.AccessMod;
import com.milesight.beaveriot.context.integration.enums.EntityType;
import com.milesight.beaveriot.context.integration.model.ExchangePayload;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@DeviceTemplateEntities(name = "Ping Device")
public class DeviceEntity extends ExchangePayload {
    @Entity(type = EntityType.PROPERTY, name = "Device Name", accessMod = AccessMod.R, attributes = @Attribute(enumClass = DeviceStatus.class))
    private String DeviceName;

    @Entity(type = EntityType.PROPERTY, name = "Device Connection Status", accessMod = AccessMod.R, attributes = @Attribute(enumClass = DeviceStatus.class))
    private Integer status;

    @Entity(type = EntityType.PROPERTY, name = "Device Serial Number", accessMod = AccessMod.R, attributes = @Attribute(enumClass = DeviceStatus.class))
    private String sn;

    public enum DeviceStatus {
        ONLINE, OFFLINE;
    }
}
