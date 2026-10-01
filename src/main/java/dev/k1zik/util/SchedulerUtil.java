package dev.k1zik.util;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Prefers Paper/Folia region schedulers via reflection; falls back to BukkitScheduler.
 */
public final class SchedulerUtil {
   private static final boolean PAPER_SCHEDULERS;
   private static final Method GET_GLOBAL_REGION_SCHEDULER;
   private static final Method GET_ASYNC_SCHEDULER;
   private static final Method GLOBAL_EXECUTE;
   private static final Method ASYNC_RUN_NOW;

   static {
      Method getGlobal = null;
      Method getAsync = null;
      Method globalExecute = null;
      Method asyncNow = null;
      boolean available = false;
      try {
         Class<?> globalScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
         Class<?> asyncScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
         getGlobal = Bukkit.class.getMethod("getGlobalRegionScheduler");
         getAsync = Bukkit.class.getMethod("getAsyncScheduler");
         globalExecute = globalScheduler.getMethod("execute", Plugin.class, Runnable.class);
         asyncNow = asyncScheduler.getMethod("runNow", Plugin.class, Consumer.class);
         available = true;
      } catch (ReflectiveOperationException ignored) {
         // Spigot / older Paper without Folia schedulers
      }
      PAPER_SCHEDULERS = available;
      GET_GLOBAL_REGION_SCHEDULER = getGlobal;
      GET_ASYNC_SCHEDULER = getAsync;
      GLOBAL_EXECUTE = globalExecute;
      ASYNC_RUN_NOW = asyncNow;
   }

   private SchedulerUtil() {
   }

   public static boolean isFoliaOrPaperRegional() {
      return PAPER_SCHEDULERS;
   }

   public static void runGlobal(Plugin plugin, Runnable task) {
      if (PAPER_SCHEDULERS) {
         try {
            Object scheduler = GET_GLOBAL_REGION_SCHEDULER.invoke(null);
            GLOBAL_EXECUTE.invoke(scheduler, plugin, task);
            return;
         } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to run global task", exception);
         }
      }
      Bukkit.getScheduler().runTask(plugin, task);
   }

   public static void runAsync(Plugin plugin, Runnable task) {
      if (PAPER_SCHEDULERS) {
         try {
            Object scheduler = GET_ASYNC_SCHEDULER.invoke(null);
            ASYNC_RUN_NOW.invoke(scheduler, plugin, (Consumer<Object>) ignored -> task.run());
            return;
         } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to run async task", exception);
         }
      }
      Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
   }
}
