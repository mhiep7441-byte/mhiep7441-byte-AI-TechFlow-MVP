package vn.techflow.manager.tiktok;

import org.junit.jupiter.api.Test;
import vn.techflow.manager.publication.PublicationStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TikTokPublishStatusTest {
    @Test
    void mapsTikTokTerminalAndProcessingStates() {
        assertEquals(PublicationStatus.PUBLISHED, TikTokService.mapPublicationStatus("PUBLISH_COMPLETE"));
        assertEquals(PublicationStatus.PUBLISHED, TikTokService.mapPublicationStatus("SEND_TO_USER_INBOX"));
        assertEquals(PublicationStatus.FAILED, TikTokService.mapPublicationStatus("FAILED"));
        assertEquals(PublicationStatus.PROCESSING, TikTokService.mapPublicationStatus("PROCESSING_UPLOAD"));
    }
}
