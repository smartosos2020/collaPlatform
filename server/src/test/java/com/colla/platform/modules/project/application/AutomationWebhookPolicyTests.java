package com.colla.platform.modules.project.application;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class AutomationWebhookPolicyTests {
 @Test void blocksLoopbackMetadataAndPlainHttp() {
  assertThatThrownBy(() -> AutomationWebhookPolicy.validate("http://example.com/hook")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> AutomationWebhookPolicy.validate("https://127.0.0.1/hook")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> AutomationWebhookPolicy.validate("https://169.254.169.254/latest")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void retryIsBounded() { assertThat(AutomationWebhookPolicy.retryDelaySeconds(99)).isEqualTo(2560); }
}
