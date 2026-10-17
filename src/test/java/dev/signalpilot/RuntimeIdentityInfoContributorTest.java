package dev.signalpilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeIdentityInfoContributorTest {
  @TempDir Path temp;

  @Test
  void calculatesStableArtifactHash() throws Exception {
    Path artifact = temp.resolve("artifact.jar");
    Files.writeString(artifact, "signalpilot");
    assertThat(RuntimeIdentityInfoContributor.sha256(artifact))
        .isEqualTo("9a618b333b07892286f752ed5b690005881b6805413ef7708053f3a22022096a");
  }
}
