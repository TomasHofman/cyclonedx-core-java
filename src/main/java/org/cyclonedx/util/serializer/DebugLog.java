package org.cyclonedx.util.serializer;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Minimal debug logging facade that uses {@code java.util.logging} when it is available and silently
 * does nothing otherwise (e.g. in runtimes whose class loader structure does not expose
 * {@code java.util.logging}).
 * <p>
 * {@code java.util.logging} is accessed exclusively via reflection, so this class (and its callers) never
 * have a linkage-time dependency on it. If the classes cannot be resolved or invoked, logging is disabled
 * for the remainder of the JVM lifetime.
 */
final class DebugLog
{
  private static volatile boolean enabled = true;

  private static final Object LOCK = new Object();

  private static volatile JulAccess access;

  private DebugLog() {
  }

  /**
   * Logs a message at FINE level if logging is available and FINE is enabled for the given source class.
   * The message supplier is only invoked when the message will actually be logged.
   */
  static void fine(final Class<?> source, final Supplier<String> message) {
    if (!enabled) {
      return;
    }
    try {
      JulAccess jul = access;
      if (jul == null) {
        synchronized (LOCK) {
          jul = access;
          if (jul == null) {
            jul = new JulAccess();
            access = jul;
          }
        }
      }
      jul.fine(source, message);
    }
    catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
      enabled = false;
    }
  }

  private static final class JulAccess
  {
    private final ConcurrentMap<String, Object> loggers = new ConcurrentHashMap<>();

    private final Method getLogger;

    private final Method isLoggable;

    private final Method fine;

    private final Object fineLevel;

    JulAccess() throws ReflectiveOperationException {
      Class<?> loggerClass = Class.forName("java.util.logging.Logger");
      Class<?> levelClass = Class.forName("java.util.logging.Level");
      getLogger = loggerClass.getMethod("getLogger", String.class);
      isLoggable = loggerClass.getMethod("isLoggable", levelClass);
      fine = loggerClass.getMethod("fine", String.class);
      fineLevel = levelClass.getField("FINE").get(null);
    }

    void fine(final Class<?> source, final Supplier<String> message) throws ReflectiveOperationException {
      Object logger = loggers.get(source.getName());
      if (logger == null) {
        logger = getLogger.invoke(null, source.getName());
        loggers.put(source.getName(), logger);
      }
      if ((Boolean) isLoggable.invoke(logger, fineLevel)) {
        fine.invoke(logger, message.get());
      }
    }
  }
}
