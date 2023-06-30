package online.hualin.flymsg.activity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Message;
import android.os.StrictMode;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jaeger.library.StatusBarUtil;
import com.speedystone.greendaodemo.db.DaoSession;
import com.speedystone.greendaodemo.db.PoetryDao;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import es.dmoral.toasty.Toasty;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import online.hualin.flymsg.App;
import online.hualin.flymsg.R;
import online.hualin.flymsg.View.PoetryPupop;
import online.hualin.flymsg.adapter.MainContentAdapter;
import online.hualin.flymsg.data.ChatMessage;
import online.hualin.flymsg.data.PoetryGson;
import online.hualin.flymsg.data.User;
import online.hualin.flymsg.db.Poetry;
import online.hualin.flymsg.net.NetThreadHelper;
import online.hualin.flymsg.utils.IpMessageConst;
import online.hualin.flymsg.utils.NotificationUtils;

import static online.hualin.flymsg.utils.CommonUtils.getLocalIpAddress;
import static online.hualin.flymsg.utils.CommonUtils.isWifiActive;

public class MainActivity extends BaseActivity implements OnClickListener
        , NavigationView.OnNavigationItemSelectedListener {
    public static final String TAG = "BaseActivity";
    private static final int REQUEST_PERMS = 1;
    public static String hostIp;
    public List<User> mUserList = new ArrayList<>();
    private MainContentAdapter pagerAdapter;
    private Toolbar toolbar;
    private FloatingActionButton fab;
    private FloatingActionButton fab_search;
    private SearchView searchView;
    private DrawerLayout mDrawerLayout;
    private NavigationView navView;
    private TabLayout tabLayout;
    private ViewPager viewPager;
    private TextView mainTitle;
    private Switch aSwitch;
    private String[] tabTitiles = new String[]{"设备", "历史"};
    private int[] pics = {R.drawable.ic_devices_black_24dp, R.drawable.ic_history_white_24dp};
    private PoetryGson poetryGson;
    private String poetryText;

    private ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
                for (Map.Entry<String, Boolean> entry : grants.entrySet()) {
                    if (!entry.getValue()) {
                        Toasty.error(this, "权限申请失败，部分功能可能受影响").show();
                    }
                }
            });

    private ViewPager.OnPageChangeListener pageChangeListener = new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            if (position == 1) {
                fab.show();
                fab_search.show();
            } else {
                fab.hide();
                fab_search.hide();
            }
        }

        @Override
        public void onPageSelected(int position) {
            if (position == 1) {
                fab.show();
                fab_search.show();

            } else {
                fab.hide();
                fab_search.hide();

            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }
    };
    private TextView poetryTitle;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StatusBarUtil.setTranslucent(this);

        if (!NotificationUtils.isNotificationEnabled(getApplicationContext())) {
            NotificationUtils.openNotificationSetting();
        }
        pref = PreferenceManager.getDefaultSharedPreferences(this);

        boolean isFirstLogin = pref.getBoolean("FirstUse", true);
        if (isFirstLogin) {
            checkedFirst();
        }
        checkAndRequirePerms();

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        iniNet();
        initView();
        initNav();
        initTab();
    }

    private void checkedFirst(){

        editor = pref.edit();
        editor.putBoolean("FirstUse", false);
        editor.putString("DeviceName", android.os.Build.DEVICE);
        editor.putString("DeviceGroup", "WORKGROUP");
        editor.apply();

    }
    @Subscribe
    private void setPoetryGson(PoetryGson poetryGson){
        this.poetryGson=poetryGson;
    }

    public void checkAndRequirePerms() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Snackbar.make(findViewById(R.id.drawer_layout), "需要读写权限才能正常使用", Snackbar.LENGTH_INDEFINITE)
                        .setAction("确定", v -> {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMS);
                        }).show();
            } else {
                requestPermissionLauncher.launch(new String[] {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                });
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    private void iniNet() {

        netThreadHelper = NetThreadHelper.newInstance();
        netThreadHelper.connectSocket();    //开始监听数据
        netThreadHelper.noticeOnline();    //广播上线

    }

    private void initTab() {
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);

        tabLayout.setupWithViewPager(viewPager, false);
        pagerAdapter = new MainContentAdapter(getSupportFragmentManager(), this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setCurrentItem(0);
        viewPager.addOnPageChangeListener(pageChangeListener);
        viewPager.setOffscreenPageLimit(1);

        for (int i = 0; i < tabTitiles.length; i++) {
            tabLayout.getTabAt(i).setCustomView(makeTabView(i));
        }

    }

    private void initNav() {
        navView = findViewById(R.id.nav_view);
        navView.setCheckedItem(R.id.my_home);
        navView.setNavigationItemSelectedListener(this);
        navView.setOnClickListener(this);
        View headerLayout = navView.getHeaderView(0);

        aSwitch = headerLayout.findViewById(R.id.switch_online);
        aSwitch.setOnClickListener(this);
        aSwitch.setChecked(pref.getBoolean("switch_notify", true));

    }

    private void initView() {
        hostIp = getLocalIpAddress();
        fab = findViewById(R.id.fab_main);
        fab_search = findViewById(R.id.fab_search);
        fab.hide();
        fab_search.hide();

        toolbar = findViewById(R.id.toolbar);
        setToolbar(toolbar, 0);

        mainTitle = findViewById(R.id.main_titile);
        poetryTitle = findViewById(R.id.poetry_title);
        poetryTitle.setOnClickListener(this);

        mDrawerLayout = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, mDrawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mDrawerLayout.addDrawerListener(toggle);
        toggle.syncState();


        sendRequestOkhttpPoetry();

    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    public void refreshUserAndView() {
        netThreadHelper.refreshUsers();
        refreshUserList();

    }

    @Subscribe
    public void refreshUserAndSend(String text) {
        if (text.equals("refreshUserList")) {
            netThreadHelper.refreshUsers();
            refreshUserList();
            EventBus.getDefault().post(mUserList);
        }
    }

    public void refreshUserList() {
        //清空数据
        mUserList.clear();

        Map<String, User> currentUsers = new HashMap<String, User>();
        currentUsers.putAll(netThreadHelper.getUsers());
        Queue<ChatMessage> msgQueue = netThreadHelper.getReceiveMsgQueue();
        Map<String, Integer> ip2Msg = new HashMap<String, Integer>();    //IP地址与未收消息个数的map
        Map<String, String> msg2Ip = new HashMap<>(); //get latest msg

        //遍历消息队列，填充ip2Msg
        Iterator<ChatMessage> it = msgQueue.iterator();
        while (it.hasNext()) {
            ChatMessage chatMsg = it.next();
            String ip = chatMsg.getSenderIp();    //得到消息发送者IP

            String tmpStr = chatMsg.getMsg();
            Integer tempInt = ip2Msg.get(ip);

            msg2Ip.put(ip, tmpStr); //put as latest msg

            if (tempInt == null) {    //若map中没有IP对应的消息个数,则把IP添加进去,值为1
                ip2Msg.put(ip, 1);
            } else {    //若已经有对应ip，则将其值加一
                ip2Msg.put(ip, ip2Msg.get(ip) + 1);
            }
        }

        //更新未读消息数
        Iterator<String> iterator = currentUsers.keySet().iterator();
        while (iterator.hasNext()) {
            User user = currentUsers.get(iterator.next());

            if (msg2Ip.get(user.getIp()) != null) {
                user.setLastestMsg(msg2Ip.get(user.getIp()));
            }

            //设置每个在线用户对应的未收消息个数
            if (ip2Msg.get(user.getIp()) == null) {
                user.setMsgCount(0);
            } else {
                user.setMsgCount(ip2Msg.get(user.getIp()));
            }
            mUserList.add(user);
        }

//        getSupportActionBar().setTitle("当前在线" + currentUsers.size() + "个用户");
        mainTitle.setText("当前在线" + currentUsers.size() + "个用户");
    }

    private View makeTabView(int position) {
        View tabView = LayoutInflater.from(this).inflate(R.layout.tab_icon, null);
        TextView textView = tabView.findViewById(R.id.tab_text);
        ImageView imageView = tabView.findViewById(R.id.tab_image);
        textView.setText(tabTitiles[position]);
        imageView.setImageResource(pics[position]);

        return tabView;
    }

    public void sendRequestOkhttpPoetry() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String url = "http://47.102.85.59:8000/api/poetry/";

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(url)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String retrunStr = response.body().string();
                    Gson gson = new Gson();
                    PoetryGson poetryGson = gson.fromJson(retrunStr, new TypeToken<PoetryGson>() {
                    }.getType());

                    DaoSession daoSession = ((App) getApplication()).getDaoSession();
                    PoetryDao poetryDao = daoSession.getPoetryDao();
                    Poetry poetryOne = new Poetry();
                    poetryOne.setAuthor(poetryGson.getAuthor());
                    poetryOne.setTitle(poetryGson.getTitle());
                    poetryOne.setContent(poetryGson.getContent());
                    poetryDao.insert(poetryOne);

                    poetryText = poetryGson.getContent();
                    Log.d(TAG, poetryText);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            poetryTitle.setText(poetryText);

//                            EventBus.getDefault().post(poetryGson);
                        }
                    });
                    pref.edit().putString("poetry", poetryText).apply();
                } catch (Exception e) {
                    e.printStackTrace();

                    String poetryStr = pref.getString("poetry", "无言独上西楼");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
//                            getSupportActionBar().setSubtitle(poetryStr);
                            poetryTitle.setText(poetryStr);
                        }
                    });
                }
            }
        }).start();

    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.my_home:
                closeDrawerAndReset();
                break;
            case R.id.setting:
                Intent intent3 = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(intent3);
                closeDrawerAndReset();
                break;
            case R.id.about:
                Intent intent = new Intent(getApplicationContext(), AboutActivity.class);
                startActivity(intent);
                closeDrawerAndReset();
                break;

            default:
                closeDrawerAndReset();
        }

        return true;
    }

    private void closeDrawerAndReset() {
        navView.setCheckedItem(R.id.my_home);
        mDrawerLayout.closeDrawers();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
//        MenuItem searchItem = menu.findItem(R.id.menu_search);
//        searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
//        searchView.setIconified(false);
//        searchView.setSubmitButtonEnabled(true);
//        searchView.setQueryHint("search");

        return true;
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (menu != null) {
            if (menu.getClass().getSimpleName().equalsIgnoreCase("MenuBuilder")) {
                try {
                    Method method = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
                    method.setAccessible(true);
                    method.invoke(menu, true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                break;
            case R.id.setting_menu:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.about:
                Intent intent2 = new Intent(this, AboutActivity.class);
                startActivity(intent2);
                break;
            case R.id.intro:
                Intent intent3 = new Intent(this, IntroActivity.class);
                startActivity(intent3);
                break;
        }
        return true;
    }

    @Override
    public void finish() {

        super.finish();
        netThreadHelper.noticeOffline();    //通知下线
        netThreadHelper.disconnectSocket(); //停止监听

    }

    @Override
    public void processMessage(Message msg) {

        switch (msg.what) {
            case IpMessageConst.IPMSG_BR_ENTRY:
            case IpMessageConst.IPMSG_BR_EXIT:
            case IpMessageConst.IPMSG_ANSENTRY:
            case IpMessageConst.IPMSG_SENDMSG: {
                refreshUserList();
                EventBus.getDefault().post(mUserList);
                break;
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                makeTextLong("退出");
                exit();
            case KeyEvent.KEYCODE_HOME:
                globalToast("后台运行");
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.nav_header:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.switch_online:

                if (!aSwitch.isChecked()) {
                    pref.edit().putBoolean("switch_notify", false).apply();
                    Toasty.warning(getApplicationContext(), "已关闭通知音", Toasty.LENGTH_SHORT).show();
                } else {
                    pref.edit().putBoolean("switch_notify", true).apply();
                    Toasty.warning(getApplicationContext(), "已打开通知音", Toasty.LENGTH_SHORT).show();

                }
                break;

            case R.id.poetry_title:
                new PoetryPupop(getApplicationContext(),poetryGson)
//                        .setBlurBackgroundEnable(true)
                        .showPopupWindow();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        EventBus.getDefault().unregister(this);
    }
}