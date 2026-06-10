package com.lantern.manosabamodsync.client.screen;

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
import icyllis.modernui.widget.TextView;
import net.minecraft.client.MinecraftClient;

import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class SyncFinishedScreen extends Fragment implements ScreenCallback {
	private final String message;
	private final boolean failed;

	public SyncFinishedScreen(String message, boolean failed) {
		this.message = message;
		this.failed = failed;
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
		title.setText(failed ? "同步失败" : "同步完成");
		title.setTextSize(26);
		title.setTextStyle(Typeface.BOLD);
		panel.addView(title, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

		TextView body = new TextView(context);
		body.setText(message);
		body.setTextSize(16);
		LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		bodyParams.setMargins(0, panel.dp(18), 0, panel.dp(18));
		panel.addView(body, bodyParams);

		LinearLayout actions = new LinearLayout(context);
		actions.setOrientation(LinearLayout.HORIZONTAL);
		actions.setGravity(Gravity.CENTER);

		Button quit = new Button(context);
		quit.setText("退出游戏");
		quit.setOnClickListener(view -> MinecraftClient.getInstance().scheduleStop());
		actions.addView(quit, buttonParams(actions));

		Button later = new Button(context);
		later.setText("稍后重启");
		later.setOnClickListener(view -> MinecraftClient.getInstance().setScreen(null));
		actions.addView(later, buttonParams(actions));

		panel.addView(actions, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

		FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(root.dp(520), root.dp(260));
		panelParams.gravity = Gravity.CENTER;
		root.addView(panel, panelParams);
		return root;
	}

	private LinearLayout.LayoutParams buttonParams(View view) {
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(view.dp(128), WRAP_CONTENT);
		params.setMargins(view.dp(6), 0, view.dp(6), 0);
		return params;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
