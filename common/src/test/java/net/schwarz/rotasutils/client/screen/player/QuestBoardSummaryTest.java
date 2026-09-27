package net.schwarz.rotasutils.client.screen.player;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestBoardSummaryTest {
    @Test
    void separatesAvailableActiveAndReadyContracts() {
        QuestBoardSummary summary = QuestBoardSummary.of(List.of(
                QuestBoardSummary.State.AVAILABLE,
                QuestBoardSummary.State.ACTIVE,
                QuestBoardSummary.State.READY,
                QuestBoardSummary.State.LOCKED));

        assertEquals(4, summary.total());
        assertEquals(1, summary.available());
        assertEquals(2, summary.active());
        assertEquals(1, summary.ready());
    }
}
