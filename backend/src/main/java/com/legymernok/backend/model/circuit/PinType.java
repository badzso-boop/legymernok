package com.legymernok.backend.model.circuit;

public enum PinType {
    DIGITAL_IO,
    ANALOG_IN,
    PWM_OUT,
    POWER_VCC,
    POWER_GND,
    I2C_SDA,
    I2C_SCL,
    SPI_MOSI,
    SPI_MISO,
    SPI_SCK,
    SPI_CS,
    UART_TX,
    UART_RX,
    ONE_WIRE,
    COMPONENT_ANODE,
    COMPONENT_CATHODE,
    PASSIVE_A,
    PASSIVE_B,
    SIGNAL_OUT,
    SIGNAL_IN
}
