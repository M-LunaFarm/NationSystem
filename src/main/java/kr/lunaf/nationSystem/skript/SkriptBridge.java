package kr.lunaf.nationSystem.skript;

import kr.lunaf.nationSystem.api.NationSystemApi;

public final class SkriptBridge {
    private static NationSystemApi api;

    private SkriptBridge() {
    }

    public static void setApi(NationSystemApi apiInstance) {
        api = apiInstance;
    }

    public static NationSystemApi api() {
        return api;
    }
}
