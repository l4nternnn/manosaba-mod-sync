package com.lantern.manosabamodsync.client.screen;

import com.lantern.manosabamodsync.ManosabaModSync;
import com.lantern.manosabamodsync.client.ClientSyncController;
import com.lantern.manosabamodsync.client.DownloadManager;
import com.lantern.manosabamodsync.client.StagingManager;
import com.lantern.manosabamodsync.manifest.SyncManifest;
import com.lantern.manosabamodsync.net.SyncResultReport;
import com.lantern.manosabamodsync.sync.SyncPlan;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class SyncProgressScreen extends Fragment implements ScreenCallback {
	private final SyncManifest manifest;
	private final SyncPlan plan;
	private TextView currentFile;
	private TextView overallProgress;
	private boolean started;

	public SyncProgressScreen(SyncManifest manifest, SyncPlan plan) {
		this.manifest = manifest;
		this.plan = plan;
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable DataSet savedInstanceState) {
		var context = requireContext();
		FrameLayout root = new FrameLayout(context);
		LinearLayout panel = new LinearLayout(context);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setGravity(Gravity.CENTER_HORIZONTAL);
		int pad = panel.dp(24);
		panel.setPadding(pad, pad, pad, pad);

		TextView title = new TextView(context);
		title.setText("正在同步服务器 Mods");
		title.setTextSize(26);
		title.setTextStyle(Typeface.BOLD);
		panel.addView(title, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

		currentFile = new TextView(context);
		currentFile.setText("准备下载...");
		currentFile.setTextSize(16);
		LinearLayout.LayoutParams currentParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		currentParams.setMargins(0, panel.dp(20), 0, panel.dp(10));
		panel.addView(currentFile, currentParams);

		overallProgress = new TextView(context);
		overallProgress.setText("总体进度：0 / " + plan.entriesToDownload().size());
		overallProgress.setTextSize(16);
		panel.addView(overallProgress, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

		FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(root.dp(520), root.dp(240));
		panelParams.gravity = Gravity.CENTER;
		root.addView(panel, panelParams);
		startDownload();
		return root;
	}

	private void startDownload() {
		if (started) {
			return;
		}
		started = true;
		ClientSyncController.sendResult(SyncResultReport.syncing());
		DownloadManager manager = new DownloadManager(ClientSyncController.config(), new StagingManager(ClientSyncController.config()));
		manager.download(plan, progress ->
				MuiModApi.postToUiThread(() -> {
					currentFile.setText("正在下载：" + progress.fileName());
					overallProgress.setText("总体进度：" + progress.completedFiles() + " / " + progress.totalFiles());
				})
		).whenComplete((summary, throwable) -> {
			if (throwable != null) {
				ManosabaModSync.LOGGER.error("Client sync failed for pack {}", manifest.packId(), throwable);
				ClientSyncController.sendResult(SyncResultReport.failed(throwable.getMessage()));
				ClientSyncController.openFinished("同步失败：" + throwable.getMessage(), true);
				return;
			}
			ClientSyncController.sendResult(SyncResultReport.restartRequired());
			ClientSyncController.openFinished("同步完成，需要重启游戏后生效。", false);
		});
	}

	@Override
	public boolean shouldClose() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
