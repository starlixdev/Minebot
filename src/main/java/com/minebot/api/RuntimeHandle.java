package com.minebot.api;

public interface RuntimeHandle {
   String name();

   BotLifecycle lifecycle();

   boolean running();

   long pendingTasks();

   long droppedTasks();

   long staleTasks();

   long completedTasks();
}
