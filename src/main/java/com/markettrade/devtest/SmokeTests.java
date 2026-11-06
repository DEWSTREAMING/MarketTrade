package com.markettrade.devtest;

import com.markettrade.MarketTrade;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MarketTrade.MODID)
@PrefixGameTestTemplate(false)
public class SmokeTests {
    @GameTest(template = "empty")
    public static void serverStarts(GameTestHelper helper) {
        helper.succeed();
    }
}
