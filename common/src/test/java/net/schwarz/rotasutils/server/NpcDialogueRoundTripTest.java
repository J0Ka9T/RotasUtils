package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.core.DialogueStudio;
import net.schwarz.rotasutils.core.NpcDialogueTemplates;
import net.schwarz.rotasutils.npc.NpcDef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NpcDialogueRoundTripTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void studioEditsSurviveTheNpcSaveFormat() {
        DialogueStudio studio = new DialogueStudio(NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GREETER));
        int message = studio.addMessage("The eastern gate is closed at night.");
        int reply = studio.addReply(0, "Tell me about the gate");
        studio.goTo(0, reply, message);

        NpcDef npc = new NpcDef("elena");
        npc.setInteractionJson(studio.json());
        NpcDef restored = NpcDef.load(npc.save());

        assertNotNull(restored.interactions());
        assertEquals(npc.interactions(), restored.interactions());
        assertEquals(npc.interactionJson(), restored.interactionJson());
        assertEquals(new DialogueStudio(com.google.gson.JsonParser.parseString(restored.interactionJson()).getAsJsonObject())
                .document(), studio.document());
    }

    @Test void theNpcStillRejectsABrokenConversationFromTheStudio() {
        DialogueStudio studio = new DialogueStudio(NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GREETER));
        studio.deleteMessage(1);
        NpcDef npc = new NpcDef("elena");
        assertThrows(IllegalArgumentException.class, () -> npc.setInteractionJson(studio.json()));
        assertEquals("", npc.interactionJson());
    }
}
