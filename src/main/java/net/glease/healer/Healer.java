package net.glease.healer;

import org.apache.commons.io.output.NullOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.core.lookup.Interpolator;
import org.apache.logging.log4j.core.lookup.StrLookup;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Map;

import static net.glease.healer.Healer.MOD_ID;
import static net.glease.healer.Healer.MOD_PACKAGE;

@cpw.mods.fml.relauncher.IFMLLoadingPlugin.Name(MOD_ID)
@cpw.mods.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions(MOD_PACKAGE)
// log4j 2.11.x+ renamed lookups to strLookupMap
// IDK since which minecraft version it breaks, so let's stay safe
@cpw.mods.fml.relauncher.IFMLLoadingPlugin.MCVersion("1.7.10")
public class Healer implements cpw.mods.fml.relauncher.IFMLLoadingPlugin {
    public static final String MOD_ID = "healer";
    public static final String MOD_PACKAGE = "net.glease.healer";
    static final Field fieldLookup;

    static {
        try {
            Class<?> clazz = Class.forName("org.apache.logging.log4j.core.lookup.Interpolator");
            fieldLookup = clazz.getDeclaredField("lookups");
            fieldLookup.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw panic(e);
        }
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
            factory.set(null, new WrappingLogContextFactory(LogManager.getFactory()));
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
}
