package net.ixdarklord.testingmod;

import com.google.gson.JsonObject;
import net.ixdarklord.coolcat_lib.common.crafting.ICondition;
import net.ixdarklord.coolcat_lib.common.crafting.IConditionSerializer;
import net.minecraft.resources.ResourceLocation;

public class TestingCondition implements ICondition {
    @Override
    public ResourceLocation getID() {
        return Serializer.NAME;
    }

    @Override
    public boolean test(IContext context) {
        return false;
    }

    public static class Serializer implements IConditionSerializer<TestingCondition> {
        private static final ResourceLocation NAME = new ResourceLocation(TestingMod.MOD_ID, "testing_condition");
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, TestingCondition value) {}

        @Override
        public TestingCondition read(JsonObject json) {
            return new TestingCondition();
        }

        @Override
        public ResourceLocation getID() {
            return NAME;
        }
    }
}
