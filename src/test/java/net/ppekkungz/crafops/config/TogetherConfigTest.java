package net.ppekkungz.crafops.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TogetherConfigTest {
    @Test void clampsUnsafeLimitsAndOrdersDistances() {
        TogetherConfig config = TogetherConfig.get();
        config.maxPlayersPerChain = 999;
        config.maxPendingRequests = 0;
        config.comfortDistance = 3;
        config.softPullDistance = 1;
        config.strongPullDistance = 1;
        config.disconnectDistance = 1;
        config.validate();
        assertEquals(64, config.maxPlayersPerChain);
        assertEquals(1, config.maxPendingRequests);
        assertTrue(config.softPullDistance >= config.comfortDistance);
        assertTrue(config.strongPullDistance > config.softPullDistance);
        assertTrue(config.disconnectDistance > config.strongPullDistance);
    }
}
