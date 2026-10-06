package net.schwarz.rotasutils.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankConditionTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject params(String rank) {
        return JsonParser.parseString("{\"params\":{\"rank\":\"" + rank + "\"}}").getAsJsonObject();
    }

    @Test
    void rankClearanceIsAContentCondition() {
        var adapter = KernelPlayerContext.requirementAdapters().get("RANK_CLEARANCE");
        assertNotNull(adapter);
        assertNotNull(adapter.apply(params("B")));
        assertThrows(IllegalArgumentException.class, () -> adapter.apply(params("Z")));
    }
}
