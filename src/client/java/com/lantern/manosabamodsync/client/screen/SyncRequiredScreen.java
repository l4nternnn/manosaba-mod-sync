package com.lantern.manosabamodsync.client.screen;

import com.lantern.manosabamodsync.client.ClientSyncController;
import com.lantern.manosabamodsync.manifest.SyncManifest;
import com.lantern.manosabamodsync.manifest.SyncModEntry;
import com.lantern.manosabamodsync.sync.SyncPlan;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.stream.Collectors;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class SyncRequiredScreen extends Fragment implements ScreenCallback {
	private final SyncManifest manifest;
	private final SyncPlan plan;

	public SyncRequiredScreen(SyncManifest manifest, SyncPlan plan) {
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
		title.setText("需要同步服务器 Mods");
		title.setTextSize(28);
		title.setTextStyle(Typeface.BOLD);
		panel.addView(title, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

		TextView summary = new TextView(context);
		summary.setText(summaryText());
		summary.setTextSize(16);
		LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		summaryParams.setMargins(0, panel.dp(16), 0, panel.dp(16));
		panel.addView(summary, summaryParams);

		ScrollView detailsScroll = new ScrollView(context);
		TextView details = new TextView(context);
		details.setText(detailsText());
		details.setTextSize(14);
		detailsScroll.addView(details, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1);
		panel.addView(detailsScroll, detailsParams);

		LinearLayout actions = new LinearLayout(context);
		actions.setOrientation(LinearLayout.HORIZONTAL);
		actions.setGravity(Gravity.CENTER);

		Button start = new Button(context);
		start.setText("开始同步");
		start.setOnClickListener(view -> ClientSyncController.openProgress(manifest, plan));
		actions.addView(start, buttonParams(actions));

		Button cancel = new Button(context);
		cancel.setText("取消");
		cancel.setOnClickListener(view -> {
			var handler = MinecraftClient.getInstance().getNetworkHandler();
			if (handler != null) {
				handler.getConnection().disconnect(Text.literal("Mod synchronization cancelled."));
			} else {
				MinecraftClient.getInstance().setScreen(null);
			}
		});
		actions.addView(cancel, buttonParams(actions));

		LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		actionParams.topMargin = panel.dp(18);
		panel.addView(actions, actionParams);

		FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(root.dp(620), root.dp(440));
		panelParams.gravity = Gravity.CENTER;
		root.addView(panel, panelParams);
		return root;
	}

	private LinearLayout.LayoutParams buttonParams(View view) {
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(view.dp(128), WRAP_CONTENT);
		params.setMargins(view.dp(6), 0, view.dp(6), 0);
		return params;
	}

	private String summaryText() {
		long mib = plan.totalDownloadSize() / 1024L / 1024L;
		return "服务器：" + manifest.packName() + "\n"
				+ "整合包版本：" + manifest.packVersion() + "\n"
				+ "需要下载：" + plan.entriesToDownload().size() + " 个文件，共约 " + mib + " MB";
	}

	private String detailsText() {
		return "缺失：\n" + formatEntries(plan.missingMods())
				+ "\n需要更新：\n" + formatEntries(plan.mismatchedMods())
				+ "\n本地额外 Mod（不会删除）：\n" + (plan.extraMods().isEmpty()
				? "- 无\n"
				: plan.extraMods().stream().map(mod -> "- " + mod.fileName()).collect(Collectors.joining("\n")) + "\n");
	}

	private String formatEntries(java.util.List<SyncModEntry> entries) {
		if (entries.isEmpty()) {
			return "- 无\n";
		}
		return entries.stream().map(entry -> "- " + entry.fileName()).collect(Collectors.joining("\n")) + "\n";
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
