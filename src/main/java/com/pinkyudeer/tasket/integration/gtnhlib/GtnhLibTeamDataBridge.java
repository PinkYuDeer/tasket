package com.pinkyudeer.tasket.integration.gtnhlib;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

import net.minecraft.nbt.NBTTagCompound;

import com.pinkyudeer.tasket.Tasket;

import cpw.mods.fml.common.Loader;

public final class GtnhLibTeamDataBridge {

    public static final String DATA_KEY = "tasket";

    private static final String TEAM_DATA = "com.gtnewhorizon.gtnhlib.teams.ITeamData";
    private static final String TEAM_DATA_REGISTRY = "com.gtnewhorizon.gtnhlib.teams.TeamDataRegistry";
    private static boolean registered;

    private GtnhLibTeamDataBridge() {}

    public static synchronized void register() {
        if (registered || !isGtnhLibLoaded()) return;
        try {
            Class<?> teamDataType = Class.forName(TEAM_DATA);
            Class<?> registryType = Class.forName(TEAM_DATA_REGISTRY);
            Method register = findRegisterMethod(registryType);
            if (register == null) {
                Tasket.LOG.warn("GTNHLib TeamDataRegistry register method not found");
                return;
            }

            Supplier<?> supplier = () -> Proxy.newProxyInstance(
                teamDataType.getClassLoader(),
                new Class<?>[] { teamDataType },
                new TasketTeamDataHandler());
            register.invoke(null, DATA_KEY, supplier);
            registered = true;
            Tasket.LOG.info("Registered tasket GTNHLib team data bridge: {}", DATA_KEY);
        } catch (ClassNotFoundException e) {
            // PR #297 尚未合入旧版 GTNHLib 时，静默跳过。
        } catch (Exception e) {
            Tasket.LOG.warn("Unable to register GTNHLib team data bridge", e);
        }
    }

    private static Method findRegisterMethod(Class<?> registryType) {
        for (Method method : registryType.getMethods()) {
            if (!("register".equals(method.getName()) || "registerTeamData".equals(method.getName()))) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 2 && String.class.equals(params[0]) && Supplier.class.isAssignableFrom(params[1])) {
                return method;
            }
        }
        return null;
    }

    private static boolean isGtnhLibLoaded() {
        return Loader.isModLoaded("gtnhlib") || Loader.isModLoaded("GTNHLib");
    }

    private static final class TasketTeamDataHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            return switch (name) {
                case "toString" -> "TasketTeamDataBridge";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "writeToNBT" -> {
                    writeMarker(args);
                    yield null;
                }
                case "readFromNBT", "mergeData", "markDirty" -> null;
                default -> null;
            };
        }

        private void writeMarker(Object[] args) {
            if (args == null || args.length == 0 || !(args[0] instanceof NBTTagCompound tag)) return;
            tag.setString("schema", "external-team-bridge");
        }
    }
}
