package com.readkakaotalk.app.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Objects;

public class MyAccessibilityService extends AccessibilityService {
    private static final String TAG = "AccessibilityService";
    public static final String ACTION_NOTIFICATION_BROADCAST = "MyAccessibilityService_LocalBroadcast";
    public static final String EXTRA_TEXT = "extra_text";

    public static final String TARGET_APP_PACKAGE = "com.kakao.talk"; // 화면을 읽어올 앱

    // 이벤트가 발생할때마다 실행되는 부분
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 패키지 체크
        final String packageName = event.getPackageName().toString();
        if (!Objects.equals(packageName, TARGET_APP_PACKAGE)) return;

        // 로그
//        Log.v(TAG, String.format(
//                "EVENT :: type=%s, class=%s, package=%s, time=%s, text=%s, source=%s",
//                event.getEventType(), event.getClassName(), event.getPackageName(),
//                event.getEventTime(), event.getText(), event.getSource()));

        // 정보 가져오기
        if (event.getClassName() == null || event.getSource() == null) return;
        int    type      = event.getEventType();
        String className = event.getClassName().toString();
        AccessibilityNodeInfo rootNode  = event.getSource();

        // 카카오톡 현재 보고있는 대화창 텍스트 읽기
        StringBuilder message = new StringBuilder();

        // CASE 1. 새로운 메시지가 온 경우
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && className.equals("android.widget.FrameLayout")) {
            if (rootNode.getChildCount() >= 1) {
                rootNode  = rootNode.getChild(0);
                className = rootNode.getClassName().toString();
                type      = AccessibilityEvent.TYPE_VIEW_SCROLLED;
            } else {
                return;
            }
        }
        // CASE 2. 사용자가 스크롤한 경우
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED && className.equals("androidx.recyclerview.widget.RecyclerView")) {
            AccessibilityNodeInfo node;
            CharSequence name = null;
            CharSequence text = null;
            for (int i = 0; i < rootNode.getChildCount(); i++) {
                node = rootNode.getChild(i);
                if (!"android.widget.FrameLayout".equals(node.getClassName().toString())) return;
                final int childCount = node.getChildCount();

                // 날짜 노드
                if (childCount == 1 &&
                        isChildTextView(node, 0)) {
                    continue; // 넘어감
                }
                // 공지 노드
                else if (childCount == 1 &&
                        isChildLinearLayout(node, 0) &&
                        isChildTextView(node.getChild(0), 0) &&
                        Objects.equals(node.getChild(0).getChild(0).getText().toString(), "공지가 등록되었습니다.")) {
                    name = null;
                    continue; // 넘어감
                }
                // 텍스트 노드 (상대방 이름 있음)
                else if (childCount >= 3 && // 일반=3, 장문(전체보기 버튼)=4, URL=5
                        isChildButton(node, 0) &&
                        isChildTextView(node, 1) &&
                        (isChildRelativeLayout(node, 2) || isChildLinearLayout(node, 2)) &&
                        isChildTextView(node.getChild(2), 0)) {
                    name = node.getChild(1).getText();
                    text = node.getChild(2).getChild(0).getText();
                }
                // 이미지 노드 (상대방 이름 있음)
                else if (childCount >= 4 && // 일반=4, 반응=5
                        isChildButton(node, 0) &&
                        isChildTextView(node, 1) &&
                        isChildFrameLayout(node, 2) &&
                        (isChildImageView(node.getChild(2), 0) || isChildRecyclerView(node.getChild(2), 0)) && // 1장 vs 여러장
                        isChildImageView(node, 3)) {
                    name = node.getChild(1).getText();
                    text = "(사진)";
                }
                // 이모티콘 노드 (상대방 이름 있음)
                else if ((childCount == 3 || childCount == 4) && // 일반=3, 반응=4
                        isChildButton(node, 0) &&
                        isChildTextView(node, 1) &&
                        ((isChildRelativeLayout(node, 2) && isChildImageView(node.getChild(2), 0)) || // 움직이는 이모티콘
                                isChildImageView(node, 2))) { // 일반 이모티콘
                    name = node.getChild(1).getText();
                    text = "(이모티콘)";
                }
                // 텍스트 노드 (상대방 이름 없음)
                else if (childCount >= 1 && // 일반=1, 반응=2, URL=3
                        isChildRelativeLayout(node, 0) &&
                        isChildTextView(node.getChild(0), 0)) {
                    name = (isSelfMessage(node.getChild(0)) ? "나" : name);
                    text = node.getChild(0).getChild(0).getText();
                }
                // 이미지 노드 (상대방 이름 없음)
                else if (childCount == 2 &&
                        isChildImageView(node, 0) &&
                        isChildFrameLayout(node, 1)) {
                    name = (isSelfMessage(node.getChild(0)) ? "나" : name);
                    text = "(사진)";
                }
                // 이미지 노드 2 (상대방 이름 없음)
                else if (childCount == 2 &&
                        isChildFrameLayout(node, 0) &&
                        isChildImageView(node.getChild(0), 0) &&
                        isChildImageView(node, 1)) {
                    name = (isSelfMessage(node.getChild(0)) ? "나" : name);
                    text = "(사진)";
//                  final Rect rect = new Rect();
//                  node.getChild(0).getBoundsInScreen(rect);
//                  Log.i(TAG, "POS: " + rect.toString());
                }
                // 이모티콘 노드 (상대방 이름 없음)
                else if ((childCount == 1 || childCount == 2) && // 일반=1, 반응=2
                        ((isChildRelativeLayout(node, 0) && isChildImageView(node.getChild(0), 0)) || // 움직이는 이모티콘
                        isChildImageView(node, 0))) { // 일반 이모티콘
                    name = (isSelfMessage(node.getChild(0)) ? "나" : name);
                    text = "(이모티콘)";
                }
                // 기타 노드
                else {
                    name = null;
                    text = getAllText(node, 0);
                    // Log.e(TAG,"CHILD " + i + " // " + "unknown // childCnt="+ node.getChildCount() + ", text=" + text);
                    // printAllViews(node, 0);
                }

//                Log.v(TAG,"CHILD " + i + " // " + name + ": " + text);

//                message.append(text).append(" "); // 대화 내용만
                message.append(name).append(": ").append(text).append("\n"); // 이름 + 대화 내용
            }
        }

        final String m = message.toString();
        if (m.length() > 0) {
            Log.e(TAG, m);
            final Intent intent = new Intent(ACTION_NOTIFICATION_BROADCAST);
            intent.putExtra(EXTRA_TEXT, m); // 메인 엑티비티로 전달
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }


    private boolean checkChildClass(final AccessibilityNodeInfo node, final int index, final String className) {
        final AccessibilityNodeInfo child = node.getChild(index);
        if (child == null) return false;
        final CharSequence name = child.getClassName();
        if (name == null) return false;
        return Objects.equals(name.toString(), className);
    }
    private boolean isChildButton(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "android.widget.Button");
    }
    private boolean isChildTextView(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "android.widget.TextView");
    }
    private boolean isChildImageView(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "android.widget.ImageView");
    }
    private boolean isChildRecyclerView(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "androidx.recyclerview.widget.RecyclerView");
    }
    private boolean isChildFrameLayout(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "android.widget.FrameLayout");
    }
    private boolean isChildLinearLayout(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "android.widget.LinearLayout");
    }
    private boolean isChildRelativeLayout(final AccessibilityNodeInfo node, final int index) {
        return checkChildClass(node, index, "android.widget.RelativeLayout");
    }

    private boolean isSelfMessage(final AccessibilityNodeInfo node) { // 내가 보낸 메시지인지 체크
        final Rect rect = new Rect();
        node.getBoundsInScreen(rect); // 노드의 화면 위치를 기준으로 내가 보냈는지 상대가 보냈는지 알아냄
        return rect.left >= 200;
    }




    public void onServiceConnected() {
//        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
//        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
//        info.feedbackType = AccessibilityServiceInfo.DEFAULT | AccessibilityServiceInfo.FEEDBACK_VISUAL;
//        info.notificationTimeout = 500;
//        setServiceInfo(info);
    }

    @Override
    public void onInterrupt() {
        Log.e(TAG, "onInterrupt()");
    }

    private void printAllViews(AccessibilityNodeInfo nodeInfo, int depth) {
        if (nodeInfo == null) return;
        if (depth > 10) return; // Max-Depth
        String t = "";
        for (int i = 0; i < depth; i++) t += ".";
        Log.d(TAG, t + "(" + nodeInfo.getText() + " <-- " + nodeInfo.getViewIdResourceName() + " / " + nodeInfo.getClassName().toString() + ")");
        for (int i = 0; i < nodeInfo.getChildCount(); i++) {
            printAllViews(nodeInfo.getChild(i), depth+1);
        }
    }

    private String getAllText(AccessibilityNodeInfo node, int depth) {
        if (node == null) return "";
        if (depth > 5) return ""; // Max-Depth
        final CharSequence className = node.getClassName();
        StringBuilder text = new StringBuilder();
        if (className != null && "android.widget.TextView".equals(className.toString())) {
            if (node.getText() != null) {
                text.append(node.getText().toString()).append(" ");
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            text.append(getAllText(node.getChild(i), depth + 1));
        }
        return text.toString();
    }
}
