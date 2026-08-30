package com.jarvis.brain;

public final class ProductionSafetyContractTest {
    private static int checks;

    public static void main(String[] args) {
        productionRejectsTestKeyMaterial();
        debugAllowsExplicitTestKeyMaterial();
        productionAcceptsKeystoreBackedMaterial();
        System.out.println("ProductionSafetyContractTest: " + checks + " assertions passed");
    }

    private static void productionRejectsTestKeyMaterial() {
        boolean threw = false;
        try {
            MemoryKeyPolicy.requireSafe(BuildMode.RELEASE, MemoryKeySource.TEST_STATIC);
        } catch (IllegalStateException expected) {
            threw = true;
        }
        check(threw, "release/device memory must fail closed when wired to test/static key material");
    }

    private static void debugAllowsExplicitTestKeyMaterial() {
        MemoryKeyPolicy.requireSafe(BuildMode.DEBUG, MemoryKeySource.TEST_STATIC);
        check(true, "debug/test mode may use explicit test-only key material");
    }

    private static void productionAcceptsKeystoreBackedMaterial() {
        MemoryKeyPolicy.requireSafe(BuildMode.RELEASE, MemoryKeySource.ANDROID_KEYSTORE);
        check(true, "release memory may proceed with Android Keystore-backed material");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
