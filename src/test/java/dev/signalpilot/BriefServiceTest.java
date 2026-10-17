package dev.signalpilot;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BriefServiceTest {
  @Test
  void validatesStructuredEvidenceReferences() {
    String valid =
        """
        {"claims":[{"text":"Checkout errors are elevated.","evidenceIds":["E1"]}],
         "uncertainty":"The root cause is not independently confirmed.",
         "nextStep":"Review the simulated proposal."}
        """;
    String rendered = BriefService.validateAndRender(valid, List.of("E1", "E2"));
    assertThat(rendered).contains("[E1]", "not independently confirmed");
  }

  @Test
  void rejectsUnknownEvidenceAndSecretLikeOutput() {
    String unknown =
        """
        {"claims":[{"text":"A claim.","evidenceIds":["E99"]}],
         "uncertainty":"Unknown.","nextStep":"Review."}
        """;
    assertThatThrownBy(() -> BriefService.validateAndRender(unknown, List.of("E1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown evidence");
    String secret =
        """
        {"claims":[{"text":"Bearer abcdefghijklmnopqrstuvwxyz", "evidenceIds":["E1"]}],
         "uncertainty":"Unknown.","nextStep":"Review."}
        """;
    assertThatThrownBy(() -> BriefService.validateAndRender(secret, List.of("E1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("secret-like");
  }
}
