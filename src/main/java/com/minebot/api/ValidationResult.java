package com.minebot.api;

import java.util.List;

public record ValidationResult(boolean valid, List<String> checks, List<String> errors) {
   public ValidationResult(boolean valid, List<String> checks, List<String> errors) {
      checks = List.copyOf(checks);
      errors = List.copyOf(errors);
      this.valid = valid;
      this.checks = checks;
      this.errors = errors;
   }
}
