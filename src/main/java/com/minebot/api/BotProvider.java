package com.minebot.api;

@FunctionalInterface
public interface BotProvider {
   JavaBot create() throws Exception;
}
