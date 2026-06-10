package com.lantern.manosabamodsync.hash;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Sha256Util {
	private static final int BUFFER_SIZE = 8192;

	private Sha256Util() {
	}

	public static String sha256(Path path) throws IOException {
		MessageDigest digest = newDigest();
		byte[] buffer = new byte[BUFFER_SIZE];

		try (InputStream input = Files.newInputStream(path);
				DigestInputStream digestInput = new DigestInputStream(input, digest)) {
			while (digestInput.read(buffer) != -1) {
				// DigestInputStream updates the digest as bytes are read.
			}
		}

		return toHex(digest.digest());
	}

	public static boolean matches(Path path, String expectedSha256) throws IOException {
		return sha256(path).equalsIgnoreCase(expectedSha256);
	}

	private static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	private static String toHex(byte[] bytes) {
		StringBuilder builder = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			builder.append(Character.forDigit((value >>> 4) & 0xF, 16));
			builder.append(Character.forDigit(value & 0xF, 16));
		}
		return builder.toString();
	}
}
