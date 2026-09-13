package net.ppekkungz.crafops.interaction;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChainTopologyTest {
    @Test void mergesTailToHeadWithoutReversing() {
        assertEquals(List.of("a", "b", "c", "d"), ChainTopology.merge(List.of("a", "b"), "b", List.of("c", "d"), "c", 4));
    }
    @Test void reversesChainsWhenOppositeEndpointsAreUsed() {
        assertEquals(List.of("b", "a", "d", "c"), ChainTopology.merge(List.of("a", "b"), "a", List.of("c", "d"), "d", 4));
    }
    @Test void rejectsMiddlePlayersCyclesAndCapacityOverflow() {
        assertThrows(IllegalArgumentException.class, () -> ChainTopology.merge(List.of("a", "b", "c"), "b", List.of("d"), "d", 4));
        assertThrows(IllegalArgumentException.class, () -> ChainTopology.merge(List.of("a", "b"), "b", List.of("b", "c"), "b", 4));
        assertThrows(IllegalArgumentException.class, () -> ChainTopology.merge(List.of("a", "b"), "b", List.of("c"), "c", 2));
    }
    @Test void splitsLinksAndRemovesMiddlePlayer() {
        assertEquals(List.of(List.of("a", "b"), List.of("c", "d")), ChainTopology.splitLink(List.of("a", "b", "c", "d"), 1));
        assertEquals(List.of(List.of("a"), List.of("c", "d")), ChainTopology.removePlayer(List.of("a", "b", "c", "d"), "b"));
    }
}
