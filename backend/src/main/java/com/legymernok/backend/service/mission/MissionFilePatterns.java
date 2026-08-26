package com.legymernok.backend.service.mission;

import java.util.regex.Pattern;

/**
 * Misszió-repókban használt fájlnév-minták, EGY helyen.
 *
 * <p>Ezek a minták határozzák meg, mit lát egy kadét egy misszió repójából:
 * a {@link #SOLUTION} által illesztett fájlok a referencia megoldást tartalmazzák, ezért
 * kimaradnak a kadét-másolatból, a {@link #STARTER} által illesztettek pedig a kiinduló
 * vázat, amit a kadét {@code solution.&lt;ext&gt;} néven megkap.
 *
 * <p><b>Miért van külön osztályban:</b> ugyanezt a "mit nem láthat a kadét" szabályt két,
 * egymástól távoli helyen kell alkalmazni — a Gitea-másolás
 * ({@code GiteaService.transformForCadetCopy}) és a RAG-indexelés
 * ({@code CodeFileChunker}, ld. {@code plans/pr0_retrieval_security_2026.md} 3.3) —, és a
 * két lista szétcsúszása <b>csendes</b> hiba lenne: a kadét-másolat helyes maradna, miközben
 * az index elkezdené kiadni a megoldást a chatbot válaszaiban. Egy konstans, egy igazság.
 */
public final class MissionFilePatterns {

    /** A referencia megoldást tartalmazó fájlok — kadét SOSE láthatja. */
    public static final Pattern SOLUTION =
            Pattern.compile("^solution\\.(js|ts|py)$", Pattern.CASE_INSENSITIVE);

    /** A kiinduló váz — ezt kapja a kadét {@code solution.&lt;ext&gt;} néven. */
    public static final Pattern STARTER =
            Pattern.compile("^starter\\.(js|ts|py)$", Pattern.CASE_INSENSITIVE);

    private MissionFilePatterns() {
    }
}
