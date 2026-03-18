package com.legymernok.backend.model.circuit;

public enum ElectricalValidationType {
    MAX_VCC,
    MIN_VCC,
    MAX_CURRENT,
    FORWARD_VOLTAGE,
    REQUIRES_SERIES_RESISTOR,
    REQUIRES_PULLUP_ON_DATA,
    POLARITY_SENSITIVE,
    MAX_SIGNAL_VOLTAGE,
    SHORT_CIRCUIT
}
