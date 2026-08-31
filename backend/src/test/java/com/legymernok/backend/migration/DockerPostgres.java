package com.legymernok.backend.migration;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Eldobható Postgres konténer a séma-tesztekhez, a {@code docker} CLI-n keresztül.
 *
 * <p><b>Miért nem Testcontainers:</b> a Testcontainers (a jelenlegi legfrissebb, 1.21.3-as
 * verzióban is) fixen {@code 1.32}-es Docker API-verziót küld, amit a Docker Engine 29+ már
 * visszautasít ({@code "client version 1.32 is too old. Minimum supported API version is
 * 1.44"}). Ez konfigurációval nem hidalható át — sem a {@code DOCKER_API_VERSION} env-vel,
 * sem a rendszertulajdonsággal (kipróbálva, mindkettő ugyanígy elhasal). A {@code docker}
 * CLI viszont mindig a saját daemonjához illő API-verziót használja, tehát ez a megoldás
 * verzió-független — és nem hoz be új Maven-függőséget sem.
 *
 * <p>A konténer {@code --rm}-mel indul, és a {@link #stop()} is törli: két, egymástól
 * független takarítás, hogy egy megszakított futás se hagyjon szemetet.
 */
final class DockerPostgres {

    private static final String IMAGE = "pgvector/pgvector:pg16";
    private static final String DB = "legymernok_test";
    private static final String USER = "test";
    private static final String PASSWORD = "test";
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(60);

    private final String containerId;
    private final String jdbcUrl;

    private DockerPostgres(String containerId, String jdbcUrl) {
        this.containerId = containerId;
        this.jdbcUrl = jdbcUrl;
    }

    static DockerPostgres start() {
        // Külön pull: így az (első futáskor akár perces) letöltés nem a készenlét-várakozás
        // időkeretét fogyasztja, és a hibaüzenet is beszédesebb, ha az image nem érhető el.
        run("docker", "pull", IMAGE);

        String name = "legymernok-schema-test-" + UUID.randomUUID().toString().substring(0, 8);
        String containerId = run("docker", "run", "-d", "--rm", "--name", name,
                "-e", "POSTGRES_DB=" + DB,
                "-e", "POSTGRES_USER=" + USER,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1::5432",
                IMAGE).trim();

        try {
            String mapping = run("docker", "port", containerId, "5432/tcp").trim().lines()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Nincs porttérkép a konténerhez"));
            int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
            String jdbcUrl = "jdbc:postgresql://127.0.0.1:" + port + "/" + DB;

            awaitReady(jdbcUrl);
            return new DockerPostgres(containerId, jdbcUrl);
        } catch (RuntimeException e) {
            forceRemove(containerId);
            throw e;
        }
    }

    String jdbcUrl() {
        return jdbcUrl;
    }

    String username() {
        return USER;
    }

    String password() {
        return PASSWORD;
    }

    Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, USER, PASSWORD);
    }

    void stop() {
        forceRemove(containerId);
    }

    /**
     * A Postgres a konténer indulása után még másodpercekig nem fogad kapcsolatot (és egyszer
     * újra is indul az initdb után), ezért a JDBC-kapcsolat sikere az egyetlen megbízható
     * készenlét-jel — nem elég, hogy a konténer fut.
     */
    private static void awaitReady(String jdbcUrl) {
        Instant deadline = Instant.now().plus(READY_TIMEOUT);
        SQLException last = null;
        while (Instant.now().isBefore(deadline)) {
            try (Connection ignored = DriverManager.getConnection(jdbcUrl, USER, PASSWORD)) {
                return;
            } catch (SQLException e) {
                last = e;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Megszakítva a Postgres indulására várva", interrupted);
                }
            }
        }
        throw new IllegalStateException(
                "A Postgres " + READY_TIMEOUT.toSeconds() + "s alatt sem lett elérhető", last);
    }

    private static void forceRemove(String containerId) {
        try {
            run("docker", "rm", "-f", containerId);
        } catch (RuntimeException e) {
            // A --rm miatt lehet, hogy már nincs mit törölni — ez nem hiba.
        }
    }

    private static String run(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(
                        String.join(" ", command) + " -> exit " + exitCode + "\n" + output);
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "A 'docker' parancs nem futtatható — ez a teszt valódi Docker-t igényel. "
                            + "Parancs: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Megszakítva: " + String.join(" ", command), e);
        }
    }

    /** Csak a hibaüzenetek olvashatóságáért. */
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        parts.add(IMAGE);
        parts.add(jdbcUrl);
        return String.join(" @ ", parts);
    }
}
