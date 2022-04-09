package com.readkakaotalk.app.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.Toast;

import com.readkakaotalk.app.R;
import com.readkakaotalk.app.service.MyAccessibilityService;
import com.readkakaotalk.app.service.MyNotificationService;

import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private AlertDialog dialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final EditText messages = (EditText) findViewById(R.id.editTextTextMultiLine);

        LocalBroadcastManager.getInstance(this).registerReceiver(
            new BroadcastReceiver() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onReceive(Context context, Intent intent) {
                    String time = intent.getStringExtra(MyNotificationService.EXTRA_TIME);
                    String name = intent.getStringExtra(MyNotificationService.EXTRA_NAME);
                    String text = intent.getStringExtra(MyNotificationService.EXTRA_TEXT);
                    String room = intent.getStringExtra(MyNotificationService.EXTRA_ROOM);
                    if (room == null) room = "개인 채팅";
                    messages.setText("시간: " + time + "\n이름: " + name + "\n메시지: " + text + "\n채팅방: " + room + "\n\n" + messages.getText());
                }
            }, new IntentFilter(MyNotificationService.ACTION_NOTIFICATION_BROADCAST)
        );
        LocalBroadcastManager.getInstance(this).registerReceiver(
            new BroadcastReceiver() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onReceive(Context context, Intent intent) {
                    String text = intent.getStringExtra(MyAccessibilityService.EXTRA_TEXT);
                    messages.setText("대화내역: \n\n" + text);
                }
            }, new IntentFilter(MyAccessibilityService.ACTION_NOTIFICATION_BROADCAST)
        );
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (this.dialog != null) {
            this.dialog.dismiss();
        }

        if (!checkNotificationPermission()) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("알림 접근 권한 필요");
            builder.setMessage("알림 전근 권한이 필요합니다.");
            builder.setPositiveButton("설정", (dialog, which) -> {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            });
            dialog = builder.create();
            dialog.show();
            return;
        }

        if (!isMyServiceRunning(MyNotificationService.class)) {
            Intent intent = new Intent(getApplicationContext(), MyNotificationService.class);
            startService(intent); // 서비스 시작
            Toast.makeText(this.getApplicationContext(), "알림 읽기 서비스 - 시작됨", Toast.LENGTH_SHORT).show();
        }

        if (!checkAccessibilityPermission()) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("접근성 권한 필요");
            builder.setMessage("접근성 권한이 필요합니다.\n\n설치된 서비스 -> 허용");
            builder.setPositiveButton("설정", (dialog, which) -> {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            });
            dialog = builder.create();
            dialog.show();
            return;
        }
    }




    // 서비스가 실행 중인지 확인하는 함수
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    // 알림 접근 권한이 있는지 확인하는 함수
    public boolean checkNotificationPermission(){
        final Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
        return sets.contains(getApplicationContext().getPackageName());
    }

    // 접근성 권한이 있는지 확인하는 함수
    public boolean checkAccessibilityPermission() {
        final AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        final List<AccessibilityServiceInfo> list = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (int i = 0; i < list.size(); i++) {
            final AccessibilityServiceInfo info = list.get(i);
            if (info.getResolveInfo().serviceInfo.packageName.equals(getApplication().getPackageName())) {
                return true;
            }
        }
        return false;
    }
}
