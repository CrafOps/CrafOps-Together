package net.ppekkungz.crafops.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TogetherConfigTest {
    @Test void clampsUnsafeLimitsAndFormationValues() {
        TogetherConfig config = TogetherConfig.get();
        config.maxPlayersPerChain = 999;
        config.maxPendingRequests = 0;
        config.formationSideOffset = 99;
        config.visualHandGap = 0;
        config.disconnectDistance = 1;
        config.validate();
        assertEquals(TogetherConfig.CURRENT_VERSION, config.configurationVersion);
        assertEquals(64, config.maxPlayersPerChain);
        assertEquals(1, config.maxPendingRequests);
        assertEquals(2.0, config.formationSideOffset);
        assertEquals(.02, config.visualHandGap);
        assertTrue(config.movingFormationBehindOffset >= config.formationBehindOffset);
        assertTrue(config.disconnectDistance > config.formationSideOffset);
    }
}
