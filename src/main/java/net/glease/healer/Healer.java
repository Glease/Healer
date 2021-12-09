package net.glease.healer;

import org.apache.commons.io.output.NullOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.core.lookup.Interpolator;
import org.apache.logging.log4j.core.lookup.StrLookup;
import org.apache.logging.log4j.spi.LoggerContextFactory;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static net.glease.healer.Healer.MOD_ID;
import static net.glease.healer.Healer.MOD_PACKAGE;

@cpw.mods.fml.relauncher.IFMLLoadingPlugin.Name(MOD_ID)
@cpw.mods.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions(MOD_PACKAGE)
@net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name(MOD_ID)
@net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions(MOD_ID)
public class Healer implements cpw.mods.fml.relauncher.IFMLLoadingPlugin, net.minecraftforge.fml.relauncher.IFMLLoadingPlugin {
    public static final String MOD_ID = "healer";
    public static final String MOD_PACKAGE = "net.glease.healer";
    static final Field fieldLookup;

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

    static PrintStream log;

    static {
        try {
            log = new PrintStream(".healer.log");
        } catch (FileNotFoundException e) {
            // Too bad :(
            log = new PrintStream(new NullOutputStream());
        }
    }

    private static RuntimeException panic(ReflectiveOperationException e) {
        throw new RuntimeException("Remove healer, either you already patched stuff yourself or you are not running minecraft!", e);
    }

    @SuppressWarnings("unchecked")
    static Map<String, StrLookup> getLookup(Interpolator o) {
        try {
            return (Map<String, StrLookup>) fieldLookup.get(o);
        } catch (IllegalAccessException e) {
            throw  panic(e);
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
        try {
            for (LoggerContext ctx : ((Log4jContextFactory) LogManager.getFactory()).getSelector().getLoggerContexts()) {
                processCoreContext(ctx);
            }
            Field factory = LogManager.class.getDeclaredField("factory");
            factory.setAccessible(true);
            factory.set(null, Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{LoggerContextFactory.class}, new LoggerContextFactoryProxy(LogManager.getFactory())));
            log("Replaced factory");
        } catch (ReflectiveOperationException e) {
            throw panic(e);
        }
    }

    static void log(String message) {
        log.println(message);
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
    public String getAccessTransformerClass() {
        return null;
    }

    private static class LoggerContextFactoryProxy implements InvocationHandler {
        private final LoggerContextFactory backing;
        private final Set<org.apache.logging.log4j.spi.LoggerContext> known = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

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
