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
import net.minecraft.client.gui.screen.Screen;

import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class ManualCheckInfoScreen extends Fragment implements ScreenCallback {
	private final Screen previous;

	public ManualCheckInfoScreen(Screen previous) {
		this.previous = previous;
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
		title.setText("\u68c0\u67e5\u66f4\u65b0");
		title.setTextSize(26);
		title.setTextStyle(Typeface.BOLD);
		panel.addView(title, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

		TextView body = new TextView(context);
		body.setText("请先关闭游戏，运行 Manosaba 独立更新器。\n\n"
				+ "更新器会在启动游戏前检查本地 mods，下载缺失或不一致的文件。\n\n"
				+ "更新完成后，再用你平时的启动器进入服务器。");
		body.setTextSize(16);
		body.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		bodyParams.setMargins(0, panel.dp(18), 0, panel.dp(22));
		panel.addView(body, bodyParams);

		Button back = new Button(context);
		back.setText("\u8fd4\u56de");
		back.setOnClickListener(view -> MinecraftClient.getInstance().setScreen(previous));
		panel.addView(back, new LinearLayout.LayoutParams(panel.dp(128), WRAP_CONTENT));

		FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(root.dp(560), root.dp(300));
		panelParams.gravity = Gravity.CENTER;
		root.addView(panel, panelParams);
		return root;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
