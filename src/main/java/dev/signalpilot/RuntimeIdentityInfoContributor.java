package dev.signalpilot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Component;

@Component
public class RuntimeIdentityInfoContributor implements InfoContributor {
  private final Path source;

  public RuntimeIdentityInfoContributor() {
    this(applicationSource());
  }

  private static Path applicationSource() {
    java.io.File source = new ApplicationHome(SignalPilotApplication.class).getSource();
    return source == null ? Path.of("target", "classes") : source.toPath();
  }

  RuntimeIdentityInfoContributor(Path source) {
    this.source = source.toAbsolutePath().normalize();
  }

  @Override
  public void contribute(Info.Builder builder) {
    builder.withDetail(
        "artifact",
        Map.of(
            "file", source.getFileName().toString(),
            "sha256", Files.isRegularFile(source) ? sha256(source) : "development-classpath"));
  }

  static String sha256(Path path) {
    try (InputStream input = Files.newInputStream(path)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[64 * 1024];
      for (int read; (read = input.read(buffer)) >= 0; ) {
        if (read > 0) digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("Could not calculate runtime artifact identity", e);
    }
  }
}
