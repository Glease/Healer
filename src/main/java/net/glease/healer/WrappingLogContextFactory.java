package net.glease.healer;

import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerContextFactory;

import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

class WrappingLogContextFactory implements LoggerContextFactory {
    private final LoggerContextFactory backing;
    private final Set<LoggerContext> known = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public WrappingLogContextFactory(LoggerContextFactory backing) {
        this.backing = backing;
    }

    @Override
    public LoggerContext getContext(String fqcn, ClassLoader loader, boolean currentContext) {
        return processAPIContext(backing.getContext(fqcn, loader, currentContext));
    }

    @Override
    public LoggerContext getContext(String fqcn, ClassLoader loader, boolean currentContext, URI configLocation) {
        return processAPIContext(backing.getContext(fqcn, loader, currentContext, configLocation));
    }

    private LoggerContext processAPIContext(LoggerContext ctx) {
        if (ctx instanceof org.apache.logging.log4j.core.LoggerContext && known.add(ctx)) {
            Healer.log("Accepting new context post launch");
            Healer.processCoreContext((org.apache.logging.log4j.core.LoggerContext) ctx);
        }
        return ctx;
    }

    @Override
    public void removeContext(LoggerContext context) {
        backing.removeContext(context);
        known.remove(context);
    }
}
