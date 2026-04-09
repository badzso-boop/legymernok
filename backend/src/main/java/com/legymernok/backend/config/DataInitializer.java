package com.legymernok.backend.config;

import com.legymernok.backend.model.auth.Permission;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.circuit.BoardType;
import com.legymernok.backend.model.circuit.ComponentPinDefinition;
import com.legymernok.backend.model.circuit.ComponentType;
import com.legymernok.backend.model.circuit.PinType;
import com.legymernok.backend.repository.auth.PermissionRepository;
import com.legymernok.backend.repository.auth.RoleRepository;
import com.legymernok.backend.repository.circuit.ComponentPinDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final ComponentPinDefinitionRepository pinRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Mission permissions
        Permission missionRead = createPermissionIfNotFound("mission:read", "View missions");
        Permission missionStart = createPermissionIfNotFound("mission:start", "Start a mission");
        Permission missionCreate = createPermissionIfNotFound("mission:create", "Create a mission");
        Permission missionEdit = createPermissionIfNotFound("mission:edit", "Edit a mission");
        Permission missionDelete = createPermissionIfNotFound("mission:delete", "Delete a mission");
        Permission missionEditAny = createPermissionIfNotFound("mission:edit_any", "Edit any mission");
        Permission missionDeleteAny = createPermissionIfNotFound("mission:delete_any", "Delete any mission");
        Permission missionCreateAnySystem = createPermissionIfNotFound("mission:create_any_system", "Create a mission in any star system");
        // Quiz permissions
        Permission quizViewResults = createPermissionIfNotFound("quiz:view_results", "View own quiz results");
        Permission quizManage = createPermissionIfNotFound("quiz:manage", "Grade and score quizzes");

        // StarSystem permissions
        Permission starSystemRead = createPermissionIfNotFound("starsystem:read", "View star systems");
        Permission starSystemCreate = createPermissionIfNotFound("starsystem:create", "Create a star system");
        Permission starSystemEdit = createPermissionIfNotFound("starsystem:edit", "Edit a star system");
        Permission starSystemDelete = createPermissionIfNotFound("starsystem:delete", "Delete a star system");
        Permission starSystemEditAny = createPermissionIfNotFound("starsystem:edit_any", "Edit any star system");
        Permission starSystemDeleteAny = createPermissionIfNotFound("starsystem:delete_any", "Delete any star system");

        // User permissions
        Permission userRead = createPermissionIfNotFound("user:read", "List users");
        Permission userCreate = createPermissionIfNotFound("user:create", "Create a user");
        Permission userEdit = createPermissionIfNotFound("user:edit", "Edit a user");
        Permission userDelete = createPermissionIfNotFound("user:delete", "Delete a user");
        Permission inheritanceAdmin = createPermissionIfNotFound("system:inheritance", "Inherit content from deleted users");

        // Role permissions (RBAC management)
        Permission roleRead = createPermissionIfNotFound("role:read", "View roles");
        Permission roleWrite = createPermissionIfNotFound("role:write", "Manage roles (create, edit, delete)");

        // Log permissions
        Permission logsRead = createPermissionIfNotFound("logs:read", "View system logs");

        // Circuit permissions
        Permission circuitRead = createPermissionIfNotFound("circuit:read", "Query circuit pin catalogue");
        Permission circuitManage = createPermissionIfNotFound("circuit:manage", "Manage circuit definitions and catalogue (admin)");
        Permission circuitSimulate = createPermissionIfNotFound("circuit:simulate", "Run circuit simulation (cadet)");

        // --- 2. CREATE ROLES ---

        // ROLE_CADET: Basic permissions (Read, Start)
        Set<Permission> cadetPermissions = new HashSet<>();
        cadetPermissions.add(missionRead);
        cadetPermissions.add(missionStart);
        cadetPermissions.add(quizViewResults);
        cadetPermissions.add(starSystemRead);
        cadetPermissions.add(starSystemCreate);
        cadetPermissions.add(missionCreate);
        cadetPermissions.add(circuitRead);
        cadetPermissions.add(circuitSimulate);
        createRoleIfNotFound("ROLE_CADET", cadetPermissions);

        // ROLE_ADMIN: All permissions (Full Access)
        Set<Permission> adminPermissions = new HashSet<>();
        // Mission
        adminPermissions.add(missionRead);
        adminPermissions.add(missionStart);
        adminPermissions.add(missionCreate);
        adminPermissions.add(missionEdit);
        adminPermissions.add(missionDelete);
        adminPermissions.add(missionEditAny);
        adminPermissions.add(missionDeleteAny);
        adminPermissions.add(quizViewResults);
        adminPermissions.add(quizManage);
        adminPermissions.add(missionCreateAnySystem);
        // StarSystem
        adminPermissions.add(starSystemRead);
        adminPermissions.add(starSystemCreate);
        adminPermissions.add(starSystemEdit);
        adminPermissions.add(starSystemDelete);
        adminPermissions.add(starSystemEditAny);
        adminPermissions.add(starSystemDeleteAny);
        // User
        adminPermissions.add(userRead);
        adminPermissions.add(userCreate);
        adminPermissions.add(userEdit);
        adminPermissions.add(userDelete);
        adminPermissions.add(inheritanceAdmin);
        // Role
        adminPermissions.add(roleRead);
        adminPermissions.add(roleWrite);
        // Logs
        adminPermissions.add(logsRead);
        // Circuit
        adminPermissions.add(circuitRead);
        adminPermissions.add(circuitManage);
        adminPermissions.add(circuitSimulate);

        createRoleIfNotFound("ROLE_ADMIN", adminPermissions);

        System.out.println("--- Permission system initialized (Permissions & Roles) ---");

        // --- 3. CIRCUIT PIN CATALOG ---
        seedCircuitPins();
        System.out.println("--- Circuit pin catalog initialized ---");
    }

    // -----------------------------------------------------------------------
    // Circuit pin seed
    // -----------------------------------------------------------------------

    private void seedCircuitPins() {
        seedUnoBoard();
        seedMegaBoard();
        seedComponentPins();
    }

    private void seedUnoBoard() {
        // Left side — power header (xOffset=0)
        boardPin(BoardType.ARDUINO_UNO, "IOREF",  0, PinType.DIGITAL_IO, false, 0,   20);
        boardPin(BoardType.ARDUINO_UNO, "RESET",  1, PinType.DIGITAL_IO, false, 0,   44);
        boardPin(BoardType.ARDUINO_UNO, "3V3",    2, PinType.POWER_VCC,  true,  0,   68);
        boardPin(BoardType.ARDUINO_UNO, "5V",     3, PinType.POWER_VCC,  true,  0,   92);
        boardPin(BoardType.ARDUINO_UNO, "GND_P1", 4, PinType.POWER_GND,  true,  0,  116);
        boardPin(BoardType.ARDUINO_UNO, "GND_P2", 5, PinType.POWER_GND,  true,  0,  140);
        boardPin(BoardType.ARDUINO_UNO, "VIN",    6, PinType.POWER_VCC,  true,  0,  164);
        // Left side — analog header (xOffset=0, gap at y=188)
        boardPin(BoardType.ARDUINO_UNO, "A0", 7,  PinType.ANALOG_IN, false, 0, 212);
        boardPin(BoardType.ARDUINO_UNO, "A1", 8,  PinType.ANALOG_IN, false, 0, 236);
        boardPin(BoardType.ARDUINO_UNO, "A2", 9,  PinType.ANALOG_IN, false, 0, 260);
        boardPin(BoardType.ARDUINO_UNO, "A3", 10, PinType.ANALOG_IN, false, 0, 284);
        boardPin(BoardType.ARDUINO_UNO, "A4", 11, PinType.I2C_SDA,   false, 0, 308);
        boardPin(BoardType.ARDUINO_UNO, "A5", 12, PinType.I2C_SCL,   false, 0, 332);
        // Right side — digital header (xOffset=220)
        boardPin(BoardType.ARDUINO_UNO, "D0",  13, PinType.UART_RX,    false, 220,  20);
        boardPin(BoardType.ARDUINO_UNO, "D1",  14, PinType.UART_TX,    false, 220,  44);
        boardPin(BoardType.ARDUINO_UNO, "D2",  15, PinType.DIGITAL_IO, false, 220,  68);
        boardPin(BoardType.ARDUINO_UNO, "D3",  16, PinType.PWM_OUT,    false, 220,  92);
        boardPin(BoardType.ARDUINO_UNO, "D4",  17, PinType.DIGITAL_IO, false, 220, 116);
        boardPin(BoardType.ARDUINO_UNO, "D5",  18, PinType.PWM_OUT,    false, 220, 140);
        boardPin(BoardType.ARDUINO_UNO, "D6",  19, PinType.PWM_OUT,    false, 220, 164);
        boardPin(BoardType.ARDUINO_UNO, "D7",  20, PinType.DIGITAL_IO, false, 220, 188);
        boardPin(BoardType.ARDUINO_UNO, "D8",  21, PinType.DIGITAL_IO, false, 220, 212);
        boardPin(BoardType.ARDUINO_UNO, "D9",  22, PinType.PWM_OUT,    false, 220, 236);
        boardPin(BoardType.ARDUINO_UNO, "D10", 23, PinType.SPI_CS,     false, 220, 260);
        boardPin(BoardType.ARDUINO_UNO, "D11", 24, PinType.SPI_MOSI,   false, 220, 284);
        boardPin(BoardType.ARDUINO_UNO, "D12", 25, PinType.SPI_MISO,   false, 220, 308);
        boardPin(BoardType.ARDUINO_UNO, "D13", 26, PinType.SPI_SCK,    false, 220, 332);
        // Top edge (yOffset=0)
        boardPin(BoardType.ARDUINO_UNO, "AREF",  27, PinType.ANALOG_IN, false,  60, 0);
        boardPin(BoardType.ARDUINO_UNO, "GND_D", 28, PinType.POWER_GND, true,  100, 0);
    }

    private void seedMegaBoard() {
        // Left side — power header (xOffset=0)
        boardPin(BoardType.ARDUINO_MEGA_2560, "IOREF",  0, PinType.DIGITAL_IO, false, 0,   20);
        boardPin(BoardType.ARDUINO_MEGA_2560, "RESET",  1, PinType.DIGITAL_IO, false, 0,   44);
        boardPin(BoardType.ARDUINO_MEGA_2560, "3V3",    2, PinType.POWER_VCC,  true,  0,   68);
        boardPin(BoardType.ARDUINO_MEGA_2560, "5V",     3, PinType.POWER_VCC,  true,  0,   92);
        boardPin(BoardType.ARDUINO_MEGA_2560, "GND_P1", 4, PinType.POWER_GND,  true,  0,  116);
        boardPin(BoardType.ARDUINO_MEGA_2560, "GND_P2", 5, PinType.POWER_GND,  true,  0,  140);
        boardPin(BoardType.ARDUINO_MEGA_2560, "GND_P3", 6, PinType.POWER_GND,  true,  0,  164);
        boardPin(BoardType.ARDUINO_MEGA_2560, "GND_P4", 7, PinType.POWER_GND,  true,  0,  188);
        boardPin(BoardType.ARDUINO_MEGA_2560, "VIN",    8, PinType.POWER_VCC,  true,  0,  212);
        // Left side — analog A0-A15 (xOffset=0, gap at y=236, starts y=256)
        for (int i = 0; i <= 15; i++) {
            boardPin(BoardType.ARDUINO_MEGA_2560, "A" + i, 9 + i, PinType.ANALOG_IN, false, 0, 256 + i * 24);
        }
        // Right side — digital D0-D21 with special types (xOffset=280)
        boardPin(BoardType.ARDUINO_MEGA_2560, "D0",  25, PinType.UART_RX,  false, 280,   20);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D1",  26, PinType.UART_TX,  false, 280,   44);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D2",  27, PinType.PWM_OUT,  false, 280,   68);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D3",  28, PinType.PWM_OUT,  false, 280,   92);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D4",  29, PinType.DIGITAL_IO, false, 280, 116);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D5",  30, PinType.PWM_OUT,  false, 280,  140);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D6",  31, PinType.PWM_OUT,  false, 280,  164);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D7",  32, PinType.DIGITAL_IO, false, 280, 188);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D8",  33, PinType.DIGITAL_IO, false, 280, 212);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D9",  34, PinType.PWM_OUT,  false, 280,  236);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D10", 35, PinType.PWM_OUT,  false, 280,  260);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D11", 36, PinType.PWM_OUT,  false, 280,  284);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D12", 37, PinType.PWM_OUT,  false, 280,  308);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D13", 38, PinType.PWM_OUT,  false, 280,  332);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D14", 39, PinType.UART_TX,  false, 280,  356);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D15", 40, PinType.UART_RX,  false, 280,  380);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D16", 41, PinType.UART_TX,  false, 280,  404);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D17", 42, PinType.UART_RX,  false, 280,  428);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D18", 43, PinType.UART_TX,  false, 280,  452);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D19", 44, PinType.UART_RX,  false, 280,  476);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D20", 45, PinType.I2C_SDA,  false, 280,  500);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D21", 46, PinType.I2C_SCL,  false, 280,  524);
        // D22-D43: plain digital (22 pins)
        for (int i = 22; i <= 43; i++) {
            boardPin(BoardType.ARDUINO_MEGA_2560, "D" + i, 25 + i, PinType.DIGITAL_IO, false, 280, 548 + (i - 22) * 24);
        }
        // D44-D46: PWM capable
        boardPin(BoardType.ARDUINO_MEGA_2560, "D44", 69, PinType.PWM_OUT,    false, 280, 1076);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D45", 70, PinType.PWM_OUT,    false, 280, 1100);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D46", 71, PinType.PWM_OUT,    false, 280, 1124);
        // D47-D49: digital
        boardPin(BoardType.ARDUINO_MEGA_2560, "D47", 72, PinType.DIGITAL_IO, false, 280, 1148);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D48", 73, PinType.DIGITAL_IO, false, 280, 1172);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D49", 74, PinType.DIGITAL_IO, false, 280, 1196);
        // D50-D53: SPI
        boardPin(BoardType.ARDUINO_MEGA_2560, "D50", 75, PinType.SPI_MISO,   false, 280, 1220);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D51", 76, PinType.SPI_MOSI,   false, 280, 1244);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D52", 77, PinType.SPI_SCK,    false, 280, 1268);
        boardPin(BoardType.ARDUINO_MEGA_2560, "D53", 78, PinType.SPI_CS,     false, 280, 1292);
    }

    private void seedComponentPins() {
        // LED (xOffset: 0=left, 60=right; node width ~80px)
        compPin(ComponentType.LED, "Anode",   0, PinType.COMPONENT_ANODE,   false, 0,  20);
        compPin(ComponentType.LED, "Cathode", 1, PinType.COMPONENT_CATHODE, false, 60, 20);
        // RESISTOR
        compPin(ComponentType.RESISTOR, "Pin1", 0, PinType.PASSIVE_A, false, 0,  20);
        compPin(ComponentType.RESISTOR, "Pin2", 1, PinType.PASSIVE_B, false, 60, 20);
        // CAPACITOR
        compPin(ComponentType.CAPACITOR, "Positive", 0, PinType.COMPONENT_ANODE,   false, 0,  20);
        compPin(ComponentType.CAPACITOR, "Negative", 1, PinType.COMPONENT_CATHODE, false, 60, 20);
        // PUSHBUTTON (4-pin, node width ~80px, height ~60px)
        compPin(ComponentType.PUSHBUTTON, "Pin1A", 0, PinType.PASSIVE_A, false, 0,  16);
        compPin(ComponentType.PUSHBUTTON, "Pin1B", 1, PinType.PASSIVE_A, false, 0,  40);
        compPin(ComponentType.PUSHBUTTON, "Pin2A", 2, PinType.PASSIVE_B, false, 60, 16);
        compPin(ComponentType.PUSHBUTTON, "Pin2B", 3, PinType.PASSIVE_B, false, 60, 40);
        // POTENTIOMETER (3-pin, VCC and GND on left, Wiper bottom center)
        compPin(ComponentType.POTENTIOMETER, "VCC",   0, PinType.POWER_VCC,  false, 0,  16);
        compPin(ComponentType.POTENTIOMETER, "GND",   1, PinType.POWER_GND,  false, 0,  40);
        compPin(ComponentType.POTENTIOMETER, "Wiper", 2, PinType.SIGNAL_OUT, false, 60, 28);
        // DHT11 (3-pin, node width ~80px)
        compPin(ComponentType.DHT11, "VCC",  0, PinType.POWER_VCC, false, 0,  16);
        compPin(ComponentType.DHT11, "DATA", 1, PinType.ONE_WIRE,   false, 60, 28);
        compPin(ComponentType.DHT11, "GND",  2, PinType.POWER_GND, false, 0,  40);
        // HC_SR04 (4-pin, node width ~90px)
        compPin(ComponentType.HC_SR04, "VCC",  0, PinType.POWER_VCC,  false, 0,  16);
        compPin(ComponentType.HC_SR04, "TRIG", 1, PinType.SIGNAL_IN,  false, 60, 16);
        compPin(ComponentType.HC_SR04, "ECHO", 2, PinType.SIGNAL_OUT, false, 60, 40);
        compPin(ComponentType.HC_SR04, "GND",  3, PinType.POWER_GND,  false, 0,  40);
        // SERVO (3-pin)
        compPin(ComponentType.SERVO, "VCC",    0, PinType.POWER_VCC, false, 0,  16);
        compPin(ComponentType.SERVO, "GND",    1, PinType.POWER_GND, false, 0,  40);
        compPin(ComponentType.SERVO, "Signal", 2, PinType.SIGNAL_IN, false, 60, 28);
        // Power symbols (single-pin, center bottom)
        compPin(ComponentType.VCC_5V,  "PWR", 0, PinType.POWER_VCC, true, 20, 30);
        compPin(ComponentType.VCC_3V3, "PWR", 0, PinType.POWER_VCC, true, 20, 30);
        compPin(ComponentType.GND,     "GND", 0, PinType.POWER_GND, true, 20, 30);
    }

    private void boardPin(BoardType board, String name, int index, PinType pinType,
                          boolean multiConn, int xOff, int yOff) {
        if (!pinRepository.existsByComponentTypeAndBoardTypeAndPinName(ComponentType.BOARD, board, name)) {
            pinRepository.save(ComponentPinDefinition.builder()
                    .componentType(ComponentType.BOARD).boardType(board)
                    .pinName(name).pinIndex(index).pinType(pinType)
                    .allowMultipleConnections(multiConn)
                    .posXOffset(xOff).posYOffset(yOff)
                    .build());
        }
    }

    private void compPin(ComponentType type, String name, int index, PinType pinType,
                         boolean multiConn, int xOff, int yOff) {
        if (!pinRepository.existsByComponentTypeAndBoardTypeIsNullAndPinName(type, name)) {
            pinRepository.save(ComponentPinDefinition.builder()
                    .componentType(type).boardType(null)
                    .pinName(name).pinIndex(index).pinType(pinType)
                    .allowMultipleConnections(multiConn)
                    .posXOffset(xOff).posYOffset(yOff)
                    .build());
        }
    }

    private Permission createPermissionIfNotFound(String name, String description) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> permissionRepository.save(
                        Permission.builder().name(name).description(description).build()
                ));
    }

    private Role createRoleIfNotFound(String name, Set<Permission> permissions) {
        return roleRepository.findByName(name)
                .map(existingRole -> {
                     existingRole.setPermissions(permissions);
                     return roleRepository.save(existingRole);
                })
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name(name).permissions(permissions).build()
                ));
    }
}