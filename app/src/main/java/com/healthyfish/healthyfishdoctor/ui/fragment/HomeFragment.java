package com.healthyfish.healthyfishdoctor.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.healthyfish.healthyfishdoctor.MyApplication;
import com.healthyfish.healthyfishdoctor.POJO.BeanBaseKeyGetReq;
import com.healthyfish.healthyfishdoctor.POJO.BeanBaseKeyGetResp;
import com.healthyfish.healthyfishdoctor.POJO.BeanInterrogationServiceUserList;
import com.healthyfish.healthyfishdoctor.POJO.BeanPageReq;
import com.healthyfish.healthyfishdoctor.POJO.BeanPageResp;
import com.healthyfish.healthyfishdoctor.POJO.BeanPersonalInformation;
import com.healthyfish.healthyfishdoctor.POJO.BeanUserChatInfo;
import com.healthyfish.healthyfishdoctor.POJO.BeanUserLoginReq;
import com.healthyfish.healthyfishdoctor.POJO.ImMsgBean;
import com.healthyfish.healthyfishdoctor.R;
import com.healthyfish.healthyfishdoctor.adapter.InterrogationServiceAdapter;
import com.healthyfish.healthyfishdoctor.constant.Constants;
import com.healthyfish.healthyfishdoctor.eventbus.WeChatReceiveMsg;
import com.healthyfish.healthyfishdoctor.ui.activity.Inspection_report.InspectionReport;
import com.healthyfish.healthyfishdoctor.ui.activity.interrogation.HealthyChat;
import com.healthyfish.healthyfishdoctor.ui.activity.medical_record.MedRecHome;
import com.healthyfish.healthyfishdoctor.utils.DateTimeUtil;
import com.healthyfish.healthyfishdoctor.utils.MySharedPrefUtil;
import com.healthyfish.healthyfishdoctor.utils.OkHttpUtils;
import com.healthyfish.healthyfishdoctor.utils.RetrofitManagerUtils;
import com.healthyfish.healthyfishdoctor.utils.mqtt_utils.MqttUtil;
import com.zhy.autolayout.AutoRelativeLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import okhttp3.ResponseBody;
import rx.Subscriber;

import static com.healthyfish.healthyfishdoctor.constant.Constants.HttpHealthyFishyUrl;

/**
 * 描述：首页fragment
 * 作者：Wayne on 2017/7/11 20:50
 * 邮箱：liwei_happyman@qq.com
 * 编辑：
 */
public class HomeFragment extends Fragment implements View.OnClickListener {

    @BindView(R.id.topbar_info)
    AutoRelativeLayout topbarInfo;
    @BindView(R.id.fm_med_rec)
    TextView fmMedRec;
    @BindView(R.id.message_lv)
    ListView messageLv;
    @BindView(R.id.fm_follow_up_rec)
    TextView fmFollowUpRec;
    @BindView(R.id.fm_inspection_report)
    TextView fmInspectionReport;
    private Context mContext;
    private View rootView;
    Unbinder unbinder;

    private BeanUserLoginReq beanUserLoginReq = new BeanUserLoginReq();
    private String sender;

    private InterrogationServiceAdapter mAdapter;
    private List<Map<String, Object>> mList = new ArrayList<Map<String, Object>>();

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mContext = getActivity();
        rootView = inflater.inflate(R.layout.fragment_home, container, false);
        unbinder = ButterKnife.bind(this, rootView);

        EventBus.getDefault().register(this);
        fmMedRec.setOnClickListener(this);
        fmFollowUpRec.setOnClickListener(this);
        fmInspectionReport.setOnClickListener(this);
//        initAll();
        initMqtt();
        lvListener();
        initPeerInfo();

        // 获取登录用户信息
        beanUserLoginReq = JSON.parseObject(MySharedPrefUtil.getValue("user"), BeanUserLoginReq.class);
        sender = "d" + MyApplication.uid;

        Log.e("本机用户名", sender);

        mList.clear();
        initListView();
        return rootView;
    }

    private void initMqtt() {
        if (!TextUtils.isEmpty(MySharedPrefUtil.getValue("user"))) {
            //AutoLogin.autoLogin();
            MqttUtil.startAsync();
        }
    }

    /**
     * ListView的点击监听
     */
    private void lvListener() {
        messageLv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 获取peer信息，
                BeanUserChatInfo beanUserChatInfo = new BeanUserChatInfo();
                beanUserChatInfo.setPhone((String) mList.get(position).get("peerNumber"));
                beanUserChatInfo.setName((String) mList.get(position).get("name"));
                beanUserChatInfo.setImgUrl((String) mList.get(position).get("portrait"));
                beanUserChatInfo.setServiceType((String) mList.get(position).get("type"));
                Intent intent = new Intent(getActivity(), HealthyChat.class);
                intent.putExtra("BeanUserChatInfo", beanUserChatInfo);
                startActivity(intent);
            }
        });
    }

    /**
     * 初始化ListView
     */
    private void initListView() {
        refreshDataList();
        /*if (!mList.isEmpty()) {
            Log.e("mlist", mList.get(0).toString());
        } else {
            Log.e("数据为空", "yes");
        }*/
        mAdapter = new InterrogationServiceAdapter(getActivity(), mList);
        messageLv.setAdapter(mAdapter);
    }

    /**
     * 初始化数据
     *
     * @return
     */
    private void refreshDataList() {
        Map<String, Object> map;
        // 获取购买过问诊服务的用户列表
        List<BeanInterrogationServiceUserList> purchaseList = DataSupport.findAll(BeanInterrogationServiceUserList.class);

        for (BeanInterrogationServiceUserList bean : purchaseList) {
            map = new HashMap<String, Object>();
            String topic = "u" + bean.getPeerNumber();
            String msgType;
            ImMsgBean lastMsg = DataSupport.where("topic = ? and name = ? or topic = ? and name = ?", topic, sender, sender, topic).findLast(ImMsgBean.class);

            if (lastMsg != null) {
                switch (lastMsg.getType()) {
                    case "t":
                        msgType = "「文本消息」";
                        break;
                    case "i":
                        msgType = "「图片消息」";
                        break;
                    case "m":
                        msgType = "「病历消息」";
                        break;
                    case "r":
                        msgType = "「化验单消息」";
                        break;
                    case "p":
                        msgType = "「处方消息」";
                        break;
                    default:
                        msgType = "「消息」";
                        break;
                }
                map.put("type", "pictureConsulting");
                map.put("message", msgType);
                map.put("time", DateTimeUtil.getTime(lastMsg.getTime()));
                map.put("time1", lastMsg.getTime());// 用于排序的比较器
                map.put("name", bean.getPeerName());
                map.put("portrait", bean.getPeerPortrait());
                map.put("peerNumber", bean.getPeerNumber());
                map.put("isNew", lastMsg.isNewMsg());
                map.put("isSender", lastMsg.isSender());
                mList.add(map);
            }
        }
        // 按发送时间给列表排序time1
        Collections.sort(mList, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                if (((long) o1.get("time1")) > (long) o2.get("time1")) {
                    return -1;
                }
                if (((long) o1.get("time1")) > (long) o2.get("time1")) {
                    return 0;
                }
                return 1;
            }
        });

    }

    /**
     * 更新发送信息状态
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUpdateSendingStatus(ImMsgBean msg) {
        // 刷新列表状态
        mList.clear();
        initListView();
    }

    /**
     * 接收到信息状态
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUpdateReceivingStatus(WeChatReceiveMsg msg) {
        //ImMsgBean bean = DataSupport.where("time = ?", msg.getTime() + "").find(ImMsgBean.class).get(0);
        // 判断是否保存了该用户信息，如果已经保存该信息，则无视，如果没有保存，新建一条记录
        // whetherTheUserExist();
        mList.clear();
        initListView();

    }

    @Override
    public void onResume() {
        super.onResume();
        mList.clear();
        initListView();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
        unbinder.unbind();
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fm_med_rec:
                Intent intent = new Intent(getActivity(), MedRecHome.class);
                startActivity(intent);
                break;
            case R.id.fm_follow_up_rec:

                break;
            case R.id.fm_inspection_report:
                Intent toInspectionReport = new Intent(getActivity(), InspectionReport.class);
                toInspectionReport.putExtra("key", Constants.FOR_LIST);
                startActivity(toInspectionReport);
                break;
        }
    }


    // 用来建立医生与客户关系
    private void initPeerInfo() {
        final BeanPageReq beanPageReq = new BeanPageReq();
        beanPageReq.setPrefix("chan_" + MyApplication.uid);
        beanPageReq.setRefresh(1);
        beanPageReq.setTo(-1);
        RetrofitManagerUtils.getInstance(MyApplication.getContetxt(), null).getHealthyInfoByRetrofit(OkHttpUtils.getRequestBody(beanPageReq), new Subscriber<ResponseBody>() {

            String resp = null;

            @Override
            public void onCompleted() {
                BeanPageResp beanPageResp = JSON.parseObject(resp, BeanPageResp.class);
                List<String> strJsonBeanPageRespList = beanPageResp.getPageList();
                for (String s : strJsonBeanPageRespList) {
                    // s的值是 chan_18977280163_u18077207818
                    if (s.substring(17).indexOf("u") >= 0) {
                        Log.e("响应值", s.substring(18));
                        whetherTheUserExist(s.substring(18));
                    }
                }
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(ResponseBody responseBody) {
                try {
                    resp = responseBody.string();
                    Log.e("返回数据", resp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // 判断用户是否存在
    void whetherTheUserExist(final String peerNumber) {
        //ImMsgBean user = DataSupport.findLast(ImMsgBean.class);
        //List<BeanInterrogationServiceUserList> list = DataSupport.where("peerName = ?", user.getName().substring(1)).find(BeanInterrogationServiceUserList.class);
        //if (list.isEmpty()) {
        final String key = "info_" + peerNumber;
        BeanBaseKeyGetReq beanBaseKeyGetReq = new BeanBaseKeyGetReq();
        beanBaseKeyGetReq.setKey(key);

        final BeanInterrogationServiceUserList userList = new BeanInterrogationServiceUserList();
        userList.setPeerNumber(peerNumber);

        RetrofitManagerUtils.getInstance(MyApplication.getContetxt(), null).getHealthyInfoByRetrofit(OkHttpUtils.getRequestBody(beanBaseKeyGetReq), new Subscriber<ResponseBody>() {
            String resp = null;

            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(ResponseBody responseBody) {
                try {
                    resp = responseBody.string();
                    BeanBaseKeyGetResp beanBaseKeyGetResp = JSON.parseObject(resp, BeanBaseKeyGetResp.class);
                    String strJsonBeanPersonalInformation = beanBaseKeyGetResp.getValue();
                    BeanPersonalInformation beanPersonalInformation = JSON.parseObject(strJsonBeanPersonalInformation, BeanPersonalInformation.class);

                    if (beanPersonalInformation != null) {
                        userList.setPeerName(beanPersonalInformation.getNickname());
                        userList.setPeerPortrait(HttpHealthyFishyUrl + beanPersonalInformation.getImgUrl());
                    }
                    // 比对数据库，如果名字头像或者发生变化了，重新写入
                    List<BeanInterrogationServiceUserList> contrastUserList = DataSupport.where("PeerNumber = ?", userList.getPeerNumber()).find(BeanInterrogationServiceUserList.class);
                    if (contrastUserList.isEmpty() || contrastUserList.get(0).getPeerName() != userList.getPeerName()
                            || contrastUserList.get(0).getPeerPortrait() != userList.getPeerPortrait()) {
                        userList.saveOrUpdate("PeerNumber = ?", userList.getPeerNumber());
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        //}
    }

}
