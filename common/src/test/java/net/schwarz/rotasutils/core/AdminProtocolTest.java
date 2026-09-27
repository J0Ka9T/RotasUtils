package net.schwarz.rotasutils.core;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.schwarz.rotasutils.network.AdminProtocol;
import net.schwarz.rotasutils.network.ProgressChunks;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AdminProtocolTest {
    @Test void largeAdminDocumentUsesLegalBoundedPacketsAndRoundTrips() {
        var tag = new CompoundTag(); tag.putUUID("ui", UUID.randomUUID());
        tag.putByteArray("json", "a".repeat(200000).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var chunks = new ProgressChunks(AdminProtocol.CHUNK, AdminProtocol.MAX);
        List<byte[]> completed = new ArrayList<>();
        AdminProtocol.send(7, tag, packet -> {
            try {
                assertTrue(packet.readableBytes() < 32767);
                var segment = AdminProtocol.read(packet);
                byte[] bytes = chunks.accept(segment.id(), segment.total(), segment.index(), segment.bytes(), 0);
                if (bytes != null) { completed.add(bytes); }
            } finally { packet.release(); }
        });
        assertEquals(1, completed.size()); assertEquals(tag, AdminProtocol.decode(completed.get(0)));
    }

    @Test void oversizeAndMalformedPacketsAreRejectedBeforeApplication() {
        var tag = new CompoundTag(); tag.putByteArray("json", new byte[AdminProtocol.MAX]);
        assertThrows(IllegalArgumentException.class, () -> AdminProtocol.send(1, tag, buffer -> fail("must not send")));
        FriendlyByteBuf malformed = new FriendlyByteBuf(Unpooled.buffer());
        try {
            malformed.writeByte(99);
            assertThrows(IllegalArgumentException.class, () -> AdminProtocol.read(malformed));
        } finally { malformed.release(); }
    }

    @Test void editorMustMatchBothLiveRevisionAndDraftGeneration() {
        var request = new CompoundTag(); request.putLong("revision", 3); request.putLong("generation", 5);
        AdminProtocol.expected(request, 3, 5);
        assertThrows(IllegalStateException.class, () -> AdminProtocol.expected(request, 4, 5));
        assertThrows(IllegalStateException.class, () -> AdminProtocol.expected(request, 3, 6));
        assertThrows(IllegalStateException.class, () -> AdminProtocol.expected(new CompoundTag(), 0, 0));
    }

    @Test void editorLayoutStaysWithinSmallAndLargeGuiViewports() {
        for (int width : new int[]{240, 320, 480, 640, 960, 1920}) {
            for (int height : new int[]{200, 240, 360, 540, 1080}) {
                var layout = EditorLayout.fit(width, height);
                assertTrue(layout.left() >= 8 && layout.top() >= 8);
                assertTrue(layout.left() + layout.width() <= width - 8);
                assertTrue(layout.top() + layout.height() <= height - 8);
                assertTrue(layout.columnWidth() >= 60 && layout.contentHeight() >= 32);
                assertTrue(layout.contentTop() + layout.contentHeight() < layout.top() + layout.height() - 26);
            }
        }
    }
}
