package com.lantern.manosabamodsync.client;

import com.lantern.manosabamodsync.ManosabaModSync;
import com.lantern.manosabamodsync.config.ClientSyncConfig;
import com.lantern.manosabamodsync.hash.Sha256Util;
import com.lantern.manosabamodsync.manifest.SyncModEntry;
import com.lantern.manosabamodsync.sync.SyncPlan;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class DownloadManager {
	private final ClientSyncConfig config;
	private final StagingManager stagingManager;
	private final HttpClient httpClient;

	public DownloadManager(ClientSyncConfig config, StagingManager stagingManager) {
		this.config = config;
		this.stagingManager = stagingManager;
		this.httpClient = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(Duration.ofSeconds(30))
				.build();
	}

	public CompletableFuture<DownloadSummary> download(SyncPlan plan, ProgressListener progressListener) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return downloadBlocking(plan, progressListener);
			} catch (Exception e) {
				throw new DownloadException("Failed to sync mods", e);
			}
		});
	}

	private DownloadSummary downloadBlocking(SyncPlan plan, ProgressListener progressListener) throws Exception {
		if (!config.allowDownload) {
			throw new IOException("Downloads are disabled by client config");
		}

		stagingManager.ensureDirectories();
		stagingManager.clearFailedDownloads();
		List<SyncModEntry> entries = plan.entriesToDownload();
		long bytes = 0L;
		long downloadedBytes = 0L;
		long totalBytes = Math.max(0L, plan.totalDownloadSize());
		long startedAt = System.nanoTime();
		int completed = 0;

		for (SyncModEntry entry : entries) {
			progressListener.onProgress(new DownloadProgress(entry.fileName(), completed, entries.size(),
					downloadedBytes, totalBytes, 0L, Math.max(0L, entry.size()), 0L));
			DownloadState state = new DownloadState(downloadedBytes, startedAt);
			Path stagedFile = downloadOne(entry, completed, entries.size(), totalBytes, state, progressListener);
			downloadedBytes = state.downloadedBytes();
			bytes += Files.size(stagedFile);
			completed++;
			progressListener.onProgress(new DownloadProgress(entry.fileName(), completed, entries.size(),
					downloadedBytes, totalBytes, Math.max(0L, entry.size()), Math.max(0L, entry.size()),
					bytesPerSecond(downloadedBytes, startedAt)));
		}

		stagingManager.writeSyncState(entries);
		return new DownloadSummary(completed, bytes);
	}

	private Path downloadOne(SyncModEntry entry, int completedFiles, int totalFiles, long totalBytes,
			DownloadState state, ProgressListener progressListener) throws Exception {
		URI uri = validateDownloadUri(entry.downloadUrl());
		Path target = safeStagedPath(entry.fileName());
		Path temp = target.resolveSibling(target.getFileName() + ".download");

		ManosabaModSync.LOGGER.info("Downloading {} from {}", entry.fileName(), uri);
		Files.deleteIfExists(temp);

		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofMinutes(5))
				.GET()
				.build();
		HttpResponse<InputStream> response = sendWithRedirects(request, 5);
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			Files.deleteIfExists(temp);
			throw new IOException("Download failed with HTTP " + response.statusCode());
		}

		long currentFileBytes = 0L;
		long currentFileSize = Math.max(0L, entry.size());
		try (InputStream input = response.body();
				OutputStream output = Files.newOutputStream(temp)) {
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = input.read(buffer)) >= 0) {
				output.write(buffer, 0, read);
				currentFileBytes += read;
				state.addDownloadedBytes(read);
				progressListener.onProgress(new DownloadProgress(entry.fileName(), completedFiles, totalFiles,
						state.downloadedBytes(), totalBytes, currentFileBytes, currentFileSize,
						bytesPerSecond(state.downloadedBytes(), state.startedAt())));
			}
		}

		String actualSha256 = Sha256Util.sha256(temp);
		if (!actualSha256.equalsIgnoreCase(entry.sha256())) {
			Files.deleteIfExists(temp);
			ManosabaModSync.LOGGER.error("SHA-256 mismatch for {}: expected {}, got {}", entry.fileName(), entry.sha256(), actualSha256);
			throw new IOException("SHA-256 mismatch for " + entry.fileName());
		}

		Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		ManosabaModSync.LOGGER.info("Downloaded and verified {}", target);
		return target;
	}

	private HttpResponse<InputStream> sendWithRedirects(HttpRequest request, int redirectsRemaining)
			throws IOException, InterruptedException {
		HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		int status = response.statusCode();
		if (status < 300 || status >= 400) {
			return response;
		}
		response.body().close();
		if (redirectsRemaining <= 0) {
			throw new IOException("Too many redirects while downloading " + request.uri());
		}

		String location = response.headers().firstValue("location")
				.orElseThrow(() -> new IOException("Redirect response missing Location header"));
		URI redirected = request.uri().resolve(location);
		validateDownloadUri(redirected.toString());

		HttpRequest redirectedRequest = HttpRequest.newBuilder(redirected)
				.timeout(Duration.ofMinutes(5))
				.GET()
				.build();
		return sendWithRedirects(redirectedRequest, redirectsRemaining - 1);
	}

	private long bytesPerSecond(long downloadedBytes, long startedAt) {
		long elapsedNanos = Math.max(1L, System.nanoTime() - startedAt);
		return (long) (downloadedBytes / (elapsedNanos / 1_000_000_000.0));
	}

	private URI validateDownloadUri(String downloadUrl) throws IOException {
		URI uri = URI.create(downloadUrl);
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			throw new IOException("Refusing non-HTTPS download URL: " + downloadUrl);
		}

		Set<String> allowedHosts = normalizedHosts();
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		if (!allowedHosts.contains(host)) {
			throw new IOException("Download host is not allowed: " + host);
		}
		return uri;
	}

	private Set<String> normalizedHosts() {
		Set<String> hosts = new HashSet<>();
		if (config.allowedDownloadHosts == null) {
			return hosts;
		}
		for (String host : config.allowedDownloadHosts) {
			if (host != null && !host.isBlank()) {
				hosts.add(host.toLowerCase(Locale.ROOT));
			}
		}
		return hosts;
	}

	private Path safeStagedPath(String fileName) throws IOException {
		if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
			throw new IOException("Unsafe staged file name: " + fileName);
		}
		Path staged = stagingManager.stagedDirectory().toAbsolutePath().normalize();
		Path target = staged.resolve(fileName).normalize();
		if (!target.getParent().equals(staged)) {
			throw new IOException("Staged file escapes sync directory: " + fileName);
		}
		return target;
	}

	public record DownloadSummary(int fileCount, long byteCount) {
	}

	public record DownloadProgress(
			String fileName,
			int completedFiles,
			int totalFiles,
			long downloadedBytes,
			long totalBytes,
			long currentFileBytes,
			long currentFileSize,
			long bytesPerSecond
	) {
		public int remainingFiles() {
			return Math.max(0, totalFiles - completedFiles);
		}

		public long remainingBytes() {
			return Math.max(0L, totalBytes - downloadedBytes);
		}
	}

	@FunctionalInterface
	public interface ProgressListener {
		void onProgress(DownloadProgress progress);
	}

	private static final class DownloadState {
		private long downloadedBytes;
		private final long startedAt;

		private DownloadState(long downloadedBytes, long startedAt) {
			this.downloadedBytes = downloadedBytes;
			this.startedAt = startedAt;
		}

		private void addDownloadedBytes(long bytes) {
			downloadedBytes += bytes;
		}

		private long downloadedBytes() {
			return downloadedBytes;
		}

		private long startedAt() {
			return startedAt;
		}
	}

	public static final class DownloadException extends RuntimeException {
		public DownloadException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
