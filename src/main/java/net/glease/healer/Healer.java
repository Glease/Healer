package net.glease.healer;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import net.minecraft.launchwrapper.Launch;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.core.lookup.Interpolator;
import org.apache.logging.log4j.core.lookup.StrLookup;
import org.apache.logging.log4j.spi.LoggerContextFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static net.glease.healer.Healer.MOD_ID;
import static net.glease.healer.Healer.MOD_PACKAGE;

@cpw.mods.fml.relauncher.IFMLLoadingPlugin.Name(MOD_ID)
@cpw.mods.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions(MOD_PACKAGE)
@cpw.mods.fml.relauncher.IFMLLoadingPlugin.SortingIndex(1001)
@net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name(MOD_ID)
@net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions(MOD_PACKAGE)
@net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.SortingIndex(1001)
public class Healer implements cpw.mods.fml.relauncher.IFMLLoadingPlugin, net.minecraftforge.fml.relauncher.IFMLLoadingPlugin {
    public static final String MOD_ID = "healer";
    public static final String MOD_PACKAGE = "net.glease.healer";
    static final Set<org.apache.logging.log4j.spi.LoggerContext> known = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    static final Field fieldLookup;
    private static final Map<String, PatchStage> patchStages = new HashMap<>();
    // let's not use the bugged software if you are patching it
    static PrintWriter log;

    static {
        Field fieldLookup1;
        Class<?> clazz;
        try {
            clazz = Class.forName("org.apache.logging.log4j.core.lookup.Interpolator");
        } catch (ReflectiveOperationException e) {
            throw panic(e);
        }
        try {
            fieldLookup1 = clazz.getDeclaredField("lookups");
            fieldLookup1.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            try {
                //noinspection JavaReflectionMemberAccess
                fieldLookup1 = clazz.getDeclaredField("strLookupMap");
                fieldLookup1.setAccessible(true);
            } catch (ReflectiveOperationException ex) {
                throw panic(ex);
            }
        }
        fieldLookup = fieldLookup1;
    }

    static {
        try {
            log = new PrintWriter(new OutputStreamWriter(new FileOutputStream(".healer.log"), StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            // Too bad :(
            log = new PrintWriter(new NullOutputStream());
        }
    }

    private PatchStage selectedPatchStage = PatchStage.PRELOAD;
    private boolean replaced = false;

    private static RuntimeException panic(ReflectiveOperationException e) {
        throw new RuntimeException("Remove healer, either you already patched stuff yourself or you are not running a supported instance of minecraft!", e);
    }

    @SuppressWarnings("unchecked")
    static Map<String, StrLookup> getLookup(Interpolator o) {
        try {
            return (Map<String, StrLookup>) fieldLookup.get(o);
        } catch (IllegalAccessException e) {
            throw panic(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> getTweakClasses() {
        return (List<String>) Launch.blackboard.get("TweakClasses");
    }

    static void log(String message) {
        log.printf("%tc %s%n", System.currentTimeMillis(), message);
    }

    static void processCoreContext(LoggerContext ctx) {
        log("Processing context: " + ctx.getName() + "@" + System.identityHashCode(ctx));
        StrLookup lookup = ctx.getConfiguration().getStrSubstitutor().getVariableResolver();
        if (lookup instanceof Interpolator) {
            Interpolator interpolator = (Interpolator) lookup;
            getLookup(interpolator).remove("jndi");
        } else {
            log("Unknown variable resolver: " + lookup.getClass().getName() + ": " + lookup + "@" + System.identityHashCode(lookup));
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        if (getTweakClasses().contains("com.forgeessentials.core.preloader.FELaunchHandler")) {
            log("ForgeEssentials detected. Setting default PatchStage to POSTINIT");
            selectedPatchStage = PatchStage.POSTINIT;
        }
        try {
            selectedPatchStage = PatchStage.valueOf(System.getProperty("net.glease.healer.patch_stage", selectedPatchStage.name().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unsupported PatchStage selected.", e);
        }
        log("PatchStage selected: " + selectedPatchStage);
        if (selectedPatchStage == PatchStage.PRELOAD) {
            patchLog4j(true);
        } else {
            patchLog4j(false);
            Thread injector = new Thread("Healer Callhook Injector") {
                @Override
                public void run() {
                    while (true) {
                        try {
                            //noinspection BusyWait
                            Thread.sleep(10);
                            Class<?> loaderClass;
                            try {
                                loaderClass = Class.forName("cpw.mods.fml.common.Loader");
                            } catch (ClassNotFoundException e) {
                                loaderClass = Class.forName("net.minecraftforge.fml.common.Loader");
                            }
                            Object loader = MethodUtils.invokeExactStaticMethod(loaderClass, "instance");
                            if (loader == null)
                                continue;
                            Object loadController = FieldUtils.readDeclaredField(loader, "modController", true);
                            if (loadController == null)
                                continue;
                            // good news is EventBus is thread safe, so no need to worry about thread safety
                            //noinspection UnstableApiUsage
                            ((EventBus) FieldUtils.readDeclaredField(loadController, "masterChannel", true)).register(Healer.this);
                            break;
                        } catch (ReflectiveOperationException e) {
                            throw new RuntimeException("Forge version unrecognized", e);
                        } catch (InterruptedException ignored) {
                        }
                    }
                    log("Callhook injected");
                }
            };
            injector.setDaemon(true);
            injector.start();
        }
    }

    @Subscribe
    public void onEvent(Object e) {
        PatchStage stage = patchStages.get(e.getClass().getSimpleName());
        // 1. unknown event
        // 2. replaced already
        // 3. too early
        if (stage == null || replaced || stage.compareTo(selectedPatchStage) < 0) {
            log("Ignored "+ e);
            return;
        }
        log("Patching at event " + e);
        patchLog4j(true);
    }

    void patchLog4j(boolean doReplace) {
        try {
            for (LoggerContext ctx : ((Log4jContextFactory) LogManager.getFactory()).getSelector().getLoggerContexts()) {
                if (!known.add(ctx)) {
                    log("Ignoring known context " + ctx.getName() + "@" + System.identityHashCode(ctx));
                    continue;
                }
                processCoreContext(ctx);
            }
            if (doReplace && !replaced) {
                Field factory = LogManager.class.getDeclaredField("factory");
                factory.setAccessible(true);
                factory.set(null, Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{LoggerContextFactory.class}, new LoggerContextFactoryProxy(LogManager.getFactory())));
                log("Replaced factory");
                replaced = true;
            }
        } catch (ReflectiveOperationException e) {
            throw panic(e);
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    private enum PatchStage {
        PRELOAD, // coremod load time
        PREINIT("FMLPreInitializationEvent"),
        INIT("FMLInitializationEvent"),
        POSTINIT("FMLPostInitializationEvent");

        PatchStage() {
        }

        PatchStage(String fmlEventClassName) {
            patchStages.put(fmlEventClassName, this);
        }
    }

    private static class LoggerContextFactoryProxy implements InvocationHandler {
        private final LoggerContextFactory backing;

        public LoggerContextFactoryProxy(LoggerContextFactory backing) {
            this.backing = backing;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("getContext".equals(method.getName())) {
                Object result = method.invoke(backing, args);
                if (result instanceof LoggerContext) {
                    LoggerContext casted = (LoggerContext) result;
                    if (known.add(casted)) {
                        log("Accepting new context post launch");
                        processCoreContext(casted);
                    }
                }
                return result;
            } else if ("removeContext".equals(method.getName())) {
                if (args[0] instanceof LoggerContext) {
                    known.remove(args[0]);
                }
            }
            return method.invoke(backing, args);
        }
    }
}
